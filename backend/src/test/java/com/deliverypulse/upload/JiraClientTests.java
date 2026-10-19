package com.deliverypulse.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class JiraClientTests {

    private MockRestServiceServer server;
    private JiraClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new JiraClient(JiraTestData.properties("DPDEMO", "OPS"), builder.build());
    }

    @Test
    void followsPageTokensAndSendsBasicAuthAndTheProjectQuery() {
        String auth = "Basic " + Base64.getEncoder()
                .encodeToString("me@example.com:secret-token".getBytes(StandardCharsets.UTF_8));
        server.expect(requestTo(Matchers.startsWith("https://example.atlassian.net/rest/api/3/search/jql?")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", auth))
                .andExpect(queryParam("jql", "project%20in%20(DPDEMO,%20OPS)%20ORDER%20BY%20key"))
                .andExpect(queryParam("fields", JiraClient.FIELDS))
                .andRespond(withSuccess("{\"issues\":[{\"key\":\"DPDEMO-1\"}],\"nextPageToken\":\"page-2\",\"isLast\":false}",
                        MediaType.APPLICATION_JSON));
        server.expect(queryParam("nextPageToken", "page-2"))
                .andRespond(withSuccess("{\"issues\":[{\"key\":\"OPS-1\"}],\"isLast\":true}", MediaType.APPLICATION_JSON));

        assertThat(client.fetchIssues()).extracting(issue -> issue.path("key").asText())
                .containsExactly("DPDEMO-1", "OPS-1");
        server.verify();
    }

    @Test
    void reportsRejectedCredentialsWithoutEchoingThem() {
        server.expect(requestTo(Matchers.startsWith("https://example.atlassian.net/")))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(client::fetchIssues)
                .isInstanceOfSatisfying(UploadRejectedException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(exception.getMessage()).contains("JIRA_API_TOKEN").doesNotContain("secret-token");
                });
    }

    @Test
    void rejectsProjectKeysThatCouldAlterTheQuery() {
        JiraClient unsafe = new JiraClient(JiraTestData.properties("DPDEMO) OR (1=1"), RestClient.create());

        assertThatThrownBy(unsafe::fetchIssues).isInstanceOf(UploadRejectedException.class)
                .hasMessageContaining("invalid project key");
    }
}

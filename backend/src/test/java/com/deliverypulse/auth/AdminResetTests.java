package com.deliverypulse.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Runs the startup bootstrap directly with the reset flag on, against the test database's admin. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdminResetTests {

    @Autowired
    private AppUserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthService authService;

    @Test
    void resetSetsThePasswordReenablesTheAdminAndEndsItsSessions() {
        AppUser admin = users.findByNormalizedUsername("admin").orElseThrow();
        String oldToken = authService.login("admin", "test-admin-password").token();
        admin.update(admin.getDisplayName(), Role.ADMIN, false);

        bootstrap("admin", "brand-new-password", true);

        assertThat(admin.isActive()).isTrue();
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(authService.authenticate(oldToken)).isEmpty();
        assertThat(authService.login("admin", "brand-new-password").user().role()).isEqualTo(Role.ADMIN);
        assertThatThrownBy(() -> authService.login("admin", "test-admin-password"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void resetWithoutAUsablePasswordChangesNothing() {
        String hashBefore = users.findByNormalizedUsername("admin").orElseThrow().getPasswordHash();

        bootstrap("admin", "short", true);
        bootstrap("admin", "", true);

        assertThat(users.findByNormalizedUsername("admin").orElseThrow().getPasswordHash()).isEqualTo(hashBefore);
    }

    @Test
    void resetCreatesTheAdminWhenTheUsernameDoesNotExistYet() {
        bootstrap("rescue", "rescue-password-1", true);

        assertThat(authService.login("rescue", "rescue-password-1").user().role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void withoutTheFlagExistingUsersAreLeftAlone() {
        String hashBefore = users.findByNormalizedUsername("admin").orElseThrow().getPasswordHash();

        bootstrap("admin", "brand-new-password", false);

        assertThat(users.findByNormalizedUsername("admin").orElseThrow().getPasswordHash()).isEqualTo(hashBefore);
    }

    private void bootstrap(String username, String password, boolean reset) {
        new AdminBootstrap(users, passwordEncoder, authService, username, password, reset)
                .run(new DefaultApplicationArguments());
    }
}

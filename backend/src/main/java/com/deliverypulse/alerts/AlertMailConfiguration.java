package com.deliverypulse.alerts;

import java.util.Properties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/** The SMTP sender for alerts. Created only when MAIL_HOST is set, so alerts stay off by default. */
@Configuration
public class AlertMailConfiguration {

    @Bean
    @ConditionalOnExpression("!'${app.alerts.mail.host:}'.isBlank()")
    JavaMailSender alertMailSender(@Value("${app.alerts.mail.host}") String host,
            @Value("${app.alerts.mail.port:587}") int port,
            @Value("${app.alerts.mail.username:}") String username,
            @Value("${app.alerts.mail.password:}") String password,
            @Value("${app.alerts.mail.starttls:true}") boolean starttls) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host.trim());
        sender.setPort(port);
        if (!username.isBlank()) {
            sender.setUsername(username.trim());
            sender.setPassword(password);
        }
        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.smtp.auth", String.valueOf(!username.isBlank()));
        properties.put("mail.smtp.starttls.enable", String.valueOf(starttls));
        // A slow mail server must not hold up the refresh job for minutes.
        properties.put("mail.smtp.connectiontimeout", "10000");
        properties.put("mail.smtp.timeout", "10000");
        properties.put("mail.smtp.writetimeout", "10000");
        return sender;
    }
}

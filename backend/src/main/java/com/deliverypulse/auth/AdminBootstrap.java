package com.deliverypulse.auth;

import java.security.SecureRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first admin when there are no users. The password comes from APP_ADMIN_PASSWORD; without it a
 * random one is generated and written to the log once, so a fresh install is never left with a known password.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(AdminBootstrap.class);
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;

    public AdminBootstrap(AppUserRepository users, PasswordEncoder passwordEncoder,
            @Value("${app.auth.admin-username:admin}") String username,
            @Value("${app.auth.admin-password:}") String password) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.username = username == null || username.isBlank() ? "admin" : username.trim();
        this.password = password == null ? "" : password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        String initialPassword = password;
        if (initialPassword.isBlank()) {
            SecureRandom random = new SecureRandom();
            StringBuilder generated = new StringBuilder();
            for (int index = 0; index < 16; index++) {
                generated.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
            initialPassword = generated.toString();
            logger.warn("Created the first admin user '{}' with generated password: {} "
                    + "Sign in and change it, or set APP_ADMIN_PASSWORD before the first start.", username, initialPassword);
        } else {
            logger.info("Created the first admin user '{}' from APP_ADMIN_PASSWORD.", username);
        }
        users.save(new AppUser(username, "Administrator", passwordEncoder.encode(initialPassword), Role.ADMIN));
    }
}

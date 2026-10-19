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
 *
 * <p>Break-glass recovery: with APP_ADMIN_RESET=true and APP_ADMIN_PASSWORD set, every start sets that admin's
 * password, makes them an active Admin and ends their sessions. Only someone who can edit the server's
 * environment and restart it can do this. Turn the flag off again afterwards.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(AdminBootstrap.class);
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    static final int MIN_PASSWORD_LENGTH = 8;

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final String username;
    private final String password;
    private final boolean reset;

    public AdminBootstrap(AppUserRepository users, PasswordEncoder passwordEncoder, AuthService authService,
            @Value("${app.auth.admin-username:admin}") String username,
            @Value("${app.auth.admin-password:}") String password,
            @Value("${app.auth.admin-reset:false}") boolean reset) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
        this.username = username == null || username.isBlank() ? "admin" : username.trim();
        this.password = password == null ? "" : password;
        this.reset = reset;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (reset) {
            resetAdmin();
        } else if (users.count() == 0) {
            createFirstAdmin();
        }
    }

    private void resetAdmin() {
        if (password.length() < MIN_PASSWORD_LENGTH) {
            logger.error("APP_ADMIN_RESET is on but APP_ADMIN_PASSWORD is missing or shorter than {} characters; "
                    + "nothing was changed.", MIN_PASSWORD_LENGTH);
            return;
        }
        AppUser admin = users.findByNormalizedUsername(AppUser.normalize(username)).orElse(null);
        if (admin == null) {
            users.save(new AppUser(username, "Administrator", passwordEncoder.encode(password), Role.ADMIN));
            logger.warn("APP_ADMIN_RESET: created admin user '{}' from APP_ADMIN_PASSWORD. "
                    + "Remove APP_ADMIN_RESET now.", username);
            return;
        }
        admin.changePassword(passwordEncoder.encode(password));
        admin.update(admin.getDisplayName(), Role.ADMIN, true);
        authService.endAllSessions(admin);
        logger.warn("APP_ADMIN_RESET: reset the password of admin user '{}', re-enabled it and ended its sessions. "
                + "Remove APP_ADMIN_RESET now.", admin.getUsername());
    }

    private void createFirstAdmin() {
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

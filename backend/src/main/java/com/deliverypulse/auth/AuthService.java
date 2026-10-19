package com.deliverypulse.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Password login with opaque bearer tokens. Only a SHA-256 hash of each token is stored. */
@Service
public class AuthService {

    static final int MAX_FAILURES = 5;
    static final Duration LOCKOUT = Duration.ofMinutes(5);
    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AppUserRepository users;
    private final UserSessionRepository sessions;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final Duration sessionLength;
    /** Checked when the username does not exist, so both paths take about as long. */
    private final String dummyHash;
    // ponytail: per-username lockout kept in memory; it resets on restart and is per instance.
    private final Map<String, Failures> failures = new ConcurrentHashMap<>();

    public AuthService(AppUserRepository users, UserSessionRepository sessions, PasswordEncoder passwordEncoder,
            Clock clock, @Value("${app.auth.session-hours:12}") long sessionHours) {
        this.users = users;
        this.sessions = sessions;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.sessionLength = Duration.ofHours(sessionHours);
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    @Transactional
    public LoginResult login(String username, String password) {
        String key = AppUser.normalize(username);
        Instant now = clock.instant();
        Failures previous = failures.get(key);
        if (previous != null && previous.lockedUntil() != null && previous.lockedUntil().isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many failed sign-ins. Try again in a few minutes.");
        }
        Optional<AppUser> user = users.findByNormalizedUsername(key);
        boolean matches = passwordEncoder.matches(password, user.map(AppUser::getPasswordHash).orElse(dummyHash));
        if (user.isEmpty() || !matches || !user.get().isActive()) {
            recordFailure(key, now);
            logger.warn("Sign-in failed: username={}", key);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Wrong username or password.");
        }
        failures.remove(key);
        sessions.deleteExpired(now);
        String token = newToken();
        Instant expiresAt = now.plus(sessionLength);
        sessions.save(new UserSession(hash(token), user.get(), now, expiresAt));
        logger.info("Signed in: username={}", key);
        return new LoginResult(token, expiresAt, user.get());
    }

    /** The active user for a bearer token, or empty if the token is unknown, expired or the user is disabled. */
    @Transactional(readOnly = true)
    public Optional<CurrentUser> authenticate(String token) {
        return sessions.findById(hash(token))
                .filter(session -> session.getExpiresAt().isAfter(clock.instant()))
                .map(UserSession::getUser)
                .filter(AppUser::isActive)
                .map(CurrentUser::of);
    }

    @Transactional
    public void logout(String token) {
        sessions.deleteById(hash(token));
    }

    @Transactional
    public void endAllSessions(AppUser user) {
        sessions.deleteAllForUser(user.getId());
    }

    private void recordFailure(String key, Instant now) {
        failures.compute(key, (ignored, current) -> {
            int count = current == null ? 1 : current.count() + 1;
            return new Failures(count, count >= MAX_FAILURES ? now.plus(LOCKOUT) : null);
        });
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record LoginResult(String token, Instant expiresAt, AppUser user) {
    }

    private record Failures(int count, Instant lockedUntil) {
    }
}

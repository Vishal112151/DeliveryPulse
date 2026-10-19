package com.deliverypulse.auth;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserSessionRepository extends JpaRepository<UserSession, String> {

    @Modifying
    @Query("delete from UserSession s where s.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);

    /** Flushes pending changes first and clears the persistence context, so no stale session survives in it. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from UserSession s where s.user.id = :userId")
    int deleteAllForUser(@Param("userId") UUID userId);
}

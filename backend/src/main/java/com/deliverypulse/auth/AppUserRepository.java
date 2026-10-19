package com.deliverypulse.auth;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByNormalizedUsername(String normalizedUsername);

    long countByRoleAndActiveTrue(Role role);

    @Query("select p.id from AppUser u join u.projects p where u.id = :userId")
    Set<UUID> findProjectIds(@Param("userId") UUID userId);
}

package com.deliverypulse.auth;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.deliverypulse.project.Project;

/**
 * Decides which projects the current caller may see. Admins see all; Managers and Viewers see the projects
 * they are members of. Work without a signed-in caller (the scheduled jobs) sees every project; HTTP requests
 * never get here unauthenticated because the security filter chain rejects them first.
 */
@Component
public class ProjectAccess {

    private final AppUserRepository users;

    public ProjectAccess(AppUserRepository users) {
        this.users = users;
    }

    public Optional<CurrentUser> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof CurrentUser user
                ? Optional.of(user) : Optional.empty();
    }

    /** Project ids the caller may see, or empty when the caller may see every project. */
    @Transactional(readOnly = true)
    public Optional<Set<UUID>> visibleProjectIds() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        if (authentication.getPrincipal() instanceof CurrentUser user) {
            return user.role() == Role.ADMIN ? Optional.empty() : Optional.of(users.findProjectIds(user.id()));
        }
        // Other authentication types (only test doubles today): admins see all, everyone else nothing.
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
        return admin ? Optional.empty() : Optional.of(Set.of());
    }

    public boolean canSee(UUID projectId) {
        return visibleProjectIds().map(ids -> ids.contains(projectId)).orElse(true);
    }

    /** 404 rather than 403, so a caller cannot tell a hidden project from a missing one. */
    public void require(UUID projectId) {
        if (!canSee(projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
        }
    }

    public List<Project> filter(Collection<Project> projects) {
        Optional<Set<UUID>> visible = visibleProjectIds();
        return projects.stream()
                .filter(project -> visible.map(ids -> ids.contains(project.getId())).orElse(true))
                .toList();
    }

    /** Gives a non-admin caller access to a project they just created. */
    @Transactional
    public void grantToCurrentUser(Project project) {
        currentUser().filter(user -> user.role() != Role.ADMIN)
                .flatMap(user -> users.findById(user.id()))
                .ifPresent(user -> user.getProjects().add(project));
    }
}

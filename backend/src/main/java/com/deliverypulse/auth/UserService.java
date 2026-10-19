package com.deliverypulse.auth;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.deliverypulse.project.Project;
import com.deliverypulse.project.ProjectRepository;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Service
public class UserService {

    private final AppUserRepository users;
    private final ProjectRepository projects;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;

    public UserService(AppUserRepository users, ProjectRepository projects, PasswordEncoder passwordEncoder,
            AuthService authService) {
        this.users = users;
        this.projects = projects;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return users.findAll().stream()
                .sorted(Comparator.comparing(AppUser::getUsername, String.CASE_INSENSITIVE_ORDER))
                .map(UserResponse::of).toList();
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID id) {
        return UserResponse.of(find(id));
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        if (users.findByNormalizedUsername(AppUser.normalize(request.username())).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That username is already taken.");
        }
        AppUser user = new AppUser(request.username(), request.displayName(),
                passwordEncoder.encode(request.password()), request.role());
        user.replaceProjects(projectsFor(request.projectIds()));
        return UserResponse.of(users.save(user));
    }

    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request, CurrentUser actor) {
        AppUser user = find(id);
        boolean losesAdmin = user.getRole() == Role.ADMIN && user.isActive()
                && (request.role() != Role.ADMIN || !request.active());
        if (losesAdmin && user.getId().equals(actor.id())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You cannot remove your own admin access.");
        }
        if (losesAdmin && users.countByRoleAndActiveTrue(Role.ADMIN) <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "At least one active admin is required.");
        }
        user.update(request.displayName(), request.role(), request.active());
        user.replaceProjects(projectsFor(request.projectIds()));
        boolean passwordChanged = request.password() != null && !request.password().isBlank();
        if (passwordChanged) {
            user.changePassword(passwordEncoder.encode(request.password()));
        }
        if (passwordChanged || !request.active()) {
            authService.endAllSessions(user);
        }
        return UserResponse.of(user);
    }

    @Transactional
    public void changeOwnPassword(CurrentUser actor, ChangePasswordRequest request) {
        AppUser user = find(actor.id());
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The current password is wrong.");
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()));
    }

    private AppUser find(UUID id) {
        return users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private Set<Project> projectsFor(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        Set<Project> found = new HashSet<>(projects.findAllById(ids));
        if (found.size() != new HashSet<>(ids).size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One or more project ids do not exist.");
        }
        return found;
    }

    public record UserResponse(UUID id, String username, String displayName, Role role, boolean active,
            List<UUID> projectIds) {

        static UserResponse of(AppUser user) {
            return new UserResponse(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole(),
                    user.isActive(), user.getProjects().stream().map(Project::getId).sorted().toList());
        }
    }

    public record CreateUserRequest(
            @NotBlank @Size(max = 80) @Pattern(regexp = "[A-Za-z0-9._@-]+",
                    message = "may contain letters, digits and . _ @ - only") String username,
            @NotBlank @Size(max = 160) String displayName,
            @NotBlank @Size(min = 8, max = 128) String password,
            @NotNull Role role,
            List<UUID> projectIds) {
    }

    /** {@code password} is optional; blank keeps the current one. */
    public record UpdateUserRequest(
            @NotBlank @Size(max = 160) String displayName,
            @NotNull Role role,
            boolean active,
            @Size(min = 8, max = 128) String password,
            List<UUID> projectIds) {
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 128) String newPassword) {
    }
}

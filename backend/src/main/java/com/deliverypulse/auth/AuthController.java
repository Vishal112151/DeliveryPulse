package com.deliverypulse.auth;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@Validated
@Tag(name = "Auth and users", description = "Sign-in sessions and admin user management.")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    public AuthController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    @PostMapping("/api/auth/login")
    @Operation(summary = "Sign in and get a bearer token",
            description = "Send the token as 'Authorization: Bearer <token>'. Five wrong passwords lock the username for five minutes.")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        AuthService.LoginResult result = authService.login(request.username(), request.password());
        return new LoginResponse(result.token(), result.expiresAt(), result.user());
    }

    @PostMapping("/api/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "End this session")
    public void logout(HttpServletRequest request) {
        SecurityConfiguration.bearerToken(request).ifPresent(authService::logout);
    }

    @GetMapping("/api/auth/me")
    @Operation(summary = "The signed-in user, with the projects they can see")
    public UserService.UserResponse me(@AuthenticationPrincipal CurrentUser user) {
        return userService.get(user.id());
    }

    @PostMapping("/api/auth/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Change your own password")
    public void changePassword(@AuthenticationPrincipal CurrentUser user,
            @Valid @RequestBody UserService.ChangePasswordRequest request) {
        userService.changeOwnPassword(user, request);
    }

    @GetMapping("/api/users")
    @Operation(summary = "List users (admin)")
    public List<UserService.UserResponse> listUsers() {
        return userService.list();
    }

    @PostMapping("/api/users")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a user (admin)")
    public UserService.UserResponse createUser(@Valid @RequestBody UserService.CreateUserRequest request) {
        return userService.create(request);
    }

    @PutMapping("/api/users/{id}")
    @Operation(summary = "Update a user's name, role, status, password or projects (admin)",
            description = "Changing the password or deactivating the user ends their sessions.")
    public UserService.UserResponse updateUser(@PathVariable UUID id,
            @Valid @RequestBody UserService.UpdateUserRequest request, @AuthenticationPrincipal CurrentUser actor) {
        return userService.update(id, request, actor);
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record LoginResponse(String token, Instant expiresAt, UserService.UserResponse user) {
    }
}

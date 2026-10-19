package com.deliverypulse.auth;

import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.deliverypulse.project.Project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_users")
public class AppUser {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, length = 80)
    private String username;

    @Column(name = "normalized_username", nullable = false, length = 80)
    private String normalizedUsername;

    @Column(name = "display_name", nullable = false, length = 160)
    private String displayName;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false)
    private boolean active = true;

    @ManyToMany
    @JoinTable(name = "project_members", joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "project_id"))
    private Set<Project> projects = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AppUser() {
    }

    public AppUser(String username, String displayName, String passwordHash, Role role) {
        this.id = UUID.randomUUID();
        this.username = username.trim();
        this.normalizedUsername = normalize(username);
        this.displayName = displayName.trim();
        this.passwordHash = passwordHash;
        this.role = role;
    }

    static String normalize(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }

    public Set<Project> getProjects() {
        return projects;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    void update(String displayName, Role role, boolean active) {
        this.displayName = displayName.trim();
        this.role = role;
        this.active = active;
    }

    void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    void replaceProjects(Set<Project> projects) {
        this.projects.clear();
        this.projects.addAll(projects);
    }
}

package com.shopsphere.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Index;

import java.time.Instant;

/**
 * A ShopSphere customer. The table is called "users" because "user" is a
 * reserved word in several databases.
 */
@Entity
@Table(name = "users", indexes = @Index(name = "idx_users_email", columnList = "email", unique = true))
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false, length = 20)
    private String role = "USER";

    @Column(nullable = false)
    private boolean blocked = false;

    protected User() {
        // required by JPA
    }

    public User(String name, String email) {
        this.name = name;
        this.email = email;
        this.passwordHash = "!legacy-account-disabled!";
    }

    public User(String name, String email, String passwordHash, String role) {
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public User(String name, String email, String role) {
        this(name, email);
        this.role = role;
    }

    public void setBlocked(boolean blocked) { this.blocked = blocked; }
    public boolean isBlocked() { return blocked; }
    public String getRole() { return role; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void updateProfile(String name, String email) { this.name = name; this.email = email; }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

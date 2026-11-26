package com.shopsphere.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record RegisterRequest(@NotBlank @Size(max=100) String name, @NotBlank @Email @Size(max=150) String email,
                       @NotBlank @Size(min=10,max=72) String password) {}
record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
record LoginResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {}
record ProfileUpdateRequest(@NotBlank @Size(max=100) String name, @NotBlank @Email @Size(max=150) String email) {}
record PasswordChangeRequest(@NotBlank String currentPassword, @NotBlank @Size(min=10,max=72) String newPassword) {}
record AdminUserUpdateRequest(@NotBlank @Size(max=100) String name, @NotBlank @Email @Size(max=150) String email) {}
record StatusRequest(boolean blocked) {}

package com.shopsphere.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record RegisterRequest(@NotBlank @Size(max=100) String name, @NotBlank @Email @Size(max=150) String email,
                       @NotBlank @Size(min=10,max=72) String password) {
    RegisterRequest {
        name = name == null ? null : name.trim();
        email = email == null ? null : email.trim();
    }
}
record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    LoginRequest {
        email = email == null ? null : email.trim();
    }
}
record LoginResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {}
record ProfileUpdateRequest(@NotBlank @Size(max=100) String name, @NotBlank @Email @Size(max=150) String email) {
    ProfileUpdateRequest {
        name = name == null ? null : name.trim();
        email = email == null ? null : email.trim();
    }
}
record PasswordChangeRequest(@NotBlank String currentPassword, @NotBlank @Size(min=10,max=72) String newPassword) {}
record AdminUserUpdateRequest(@NotBlank @Size(max=100) String name, @NotBlank @Email @Size(max=150) String email) {
    AdminUserUpdateRequest {
        name = name == null ? null : name.trim();
        email = email == null ? null : email.trim();
    }
}
record StatusRequest(boolean blocked) {}

package com.shopsphere.user;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserAccountService accounts;
    public AuthController(UserAccountService accounts) { this.accounts=accounts; }
    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) { return UserResponse.from(accounts.register(request)); }
    @PostMapping("/login") public LoginResponse login(@Valid @RequestBody LoginRequest request) { return accounts.login(request); }
    @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT) public void logout() { }
}

package com.shopsphere.user;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.List;

@Service
public class UserAccountService {
    private final UserRepository users; private final PasswordEncoder passwords; private final JwtService jwt;
    public UserAccountService(UserRepository users, PasswordEncoder passwords, JwtService jwt) { this.users=users; this.passwords=passwords; this.jwt=jwt; }
    @Transactional public User register(RegisterRequest r) {
        String email=r.email().trim().toLowerCase();
        if (users.existsByEmail(email)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Email is already registered");
        return users.save(new User(r.name().trim(), email, passwords.encode(r.password()), "USER"));
    }
    public LoginResponse login(LoginRequest r) {
        User u=users.findByEmailIgnoreCase(r.email().trim()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid email or password"));
        if (u.isBlocked() || u.getPasswordHash()==null || !passwords.matches(r.password(),u.getPasswordHash())) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid email or password");
        return new LoginResponse(jwt.issue(u),"Bearer",jwt.ttl(),UserResponse.from(u));
    }
    public User require(Long id) { return users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"User not found")); }
    @Transactional public User updateProfile(Long id, ProfileUpdateRequest r) {
        User u=require(id); String email=r.email().trim().toLowerCase();
        users.findByEmailIgnoreCase(email).filter(other -> !other.getId().equals(id)).ifPresent(other -> { throw new ResponseStatusException(HttpStatus.CONFLICT,"Email is already registered"); });
        u.updateProfile(r.name().trim(),email); return u;
    }
    @Transactional public void changePassword(Long id, PasswordChangeRequest r) {
        User u=require(id); if (!passwords.matches(r.currentPassword(),u.getPasswordHash())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Current password is incorrect");
        u.setPasswordHash(passwords.encode(r.newPassword()));
    }
    public List<UserResponse> list(String q) {
        List<User> found=(q==null||q.isBlank())?users.findAll():users.findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(q,q);
        return found.stream().map(UserResponse::from).toList();
    }
    public long adminCount() { return users.countByRole("ADMIN"); }
    @Transactional public User adminUpdate(Long id, AdminUserUpdateRequest r) {
        User u=require(id); String email=r.email().trim().toLowerCase();
        users.findByEmailIgnoreCase(email).filter(other -> !other.getId().equals(id)).ifPresent(other -> { throw new ResponseStatusException(HttpStatus.CONFLICT,"Email is already registered"); });
        u.updateProfile(r.name().trim(),email); return u;
    }
    @Transactional public User block(Long id, boolean blocked) { User u=require(id); u.setBlocked(blocked); return u; }
    @Transactional public void delete(Long id) { users.delete(require(id)); }
}

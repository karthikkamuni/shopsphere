package com.shopsphere.user;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserAccountService accounts;
    public UserController(UserAccountService accounts) { this.accounts=accounts; }
    @GetMapping("/me") public UserResponse me(Authentication auth) { return UserResponse.from(accounts.require(principal(auth).id())); }
    @PutMapping("/me") public UserResponse updateMe(Authentication auth,@Valid @RequestBody ProfileUpdateRequest r) { return UserResponse.from(accounts.updateProfile(principal(auth).id(),r)); }
    @PutMapping("/me/password") @ResponseStatus(HttpStatus.NO_CONTENT) public void changePassword(Authentication auth,@Valid @RequestBody PasswordChangeRequest r) { accounts.changePassword(principal(auth).id(),r); }
    @GetMapping public List<UserResponse> list(@RequestParam(required=false) String q, Authentication auth) { requireAdmin(auth); return accounts.list(q); }
    @GetMapping("/{id}") public UserResponse get(@PathVariable Long id, Authentication auth) { requireAdmin(auth); return UserResponse.from(accounts.require(id)); }
    @PutMapping("/{id}") public UserResponse update(@PathVariable Long id,@Valid @RequestBody AdminUserUpdateRequest r,Authentication auth) { requireAdmin(auth); return UserResponse.from(accounts.adminUpdate(id,r)); }
    @PatchMapping("/{id}/status") public UserResponse status(@PathVariable Long id,@RequestBody @Valid StatusRequest r,Authentication auth) { requireAdmin(auth); return UserResponse.from(accounts.block(id,r.blocked())); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable Long id,Authentication auth) {
        requireAdmin(auth);
        if (id.equals(principal(auth).id())) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.CONFLICT,"You cannot delete your own account");
        User target=accounts.require(id);
        if ("ADMIN".equals(target.getRole()) && accounts.adminCount() <= 1) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.CONFLICT,"Cannot delete the last administrator");
        accounts.delete(id);
    }
    private AuthPrincipal principal(Authentication a) { return (AuthPrincipal)a.getPrincipal(); }
    private void requireAdmin(Authentication a) { if (a==null || a.getAuthorities().stream().noneMatch(x->x.getAuthority().equals("ROLE_ADMIN"))) throw new AccessDeniedException("Administrator role required"); }
}

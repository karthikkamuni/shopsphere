package com.shopsphere.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/internal/users")
public class InternalUserController {
    private final UserAccountService accounts;
    private final byte[] serviceKey;
    public InternalUserController(UserAccountService accounts, @Value("${shopsphere.service-api-key:}") String serviceKey) {
        this.accounts=accounts; this.serviceKey=serviceKey.getBytes(StandardCharsets.UTF_8);
    }
    @GetMapping("/{id}/exists") public ExistsResponse exists(@PathVariable Long id,@RequestHeader(value="X-Service-Token",required=false) String token) {
        authenticate(token);
        try { User u=accounts.require(id); return new ExistsResponse(true,!u.isBlocked()); }
        catch (ResponseStatusException e) { if(e.getStatusCode()==HttpStatus.NOT_FOUND) return new ExistsResponse(false,false); throw e; }
    }

    @GetMapping("/{id}") public CustomerDetails details(@PathVariable Long id,@RequestHeader(value="X-Service-Token",required=false) String token) {
        authenticate(token);
        User user=accounts.require(id);
        return new CustomerDetails(user.getId(),user.getName(),user.getEmail(),!user.isBlocked());
    }

    private void authenticate(String token) {
        if (serviceKey.length<32 || token==null || !MessageDigest.isEqual(serviceKey,token.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid service credentials");
    }

    record ExistsResponse(boolean exists, boolean active) {}
    record CustomerDetails(Long id, String name, String email, boolean active) {}
}

package com.shopsphere.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.nio.charset.StandardCharsets;

@Configuration
public class AdminInitializer {
    @Bean ApplicationRunner provisionInitialAdmin(UserRepository users, PasswordEncoder encoder,
            @Value("${shopsphere.admin.name:}") String name,
            @Value("${shopsphere.admin.email:}") String email,
            @Value("${shopsphere.admin.password:}") String password) {
        return args -> {
            if (name.isBlank() && email.isBlank() && password.isBlank()) return;
            if (email.isBlank() || password.length()<10 || name.isBlank()) throw new IllegalStateException("Set SHOPSPHERE_ADMIN_NAME, SHOPSPHERE_ADMIN_EMAIL and SHOPSPHERE_ADMIN_PASSWORD (10+ chars) together");
            if (password.getBytes(StandardCharsets.UTF_8).length > 72) throw new IllegalStateException("SHOPSPHERE_ADMIN_PASSWORD must be at most 72 UTF-8 bytes");
            String normalizedEmail = email.trim().toLowerCase();
            if (users.findByEmailIgnoreCase(normalizedEmail).isEmpty()) {
                try {
                    users.save(new User(name.trim(), normalizedEmail, encoder.encode(password), "ADMIN"));
                } catch (DataIntegrityViolationException e) {
                    if (users.findByEmailIgnoreCase(normalizedEmail).isEmpty()) throw e;
                }
            }
        };
    }
}

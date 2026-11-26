package com.shopsphere.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@Configuration
public class AdminInitializer {
    @Bean ApplicationRunner provisionInitialAdmin(UserRepository users, PasswordEncoder encoder,
            @Value("${shopsphere.admin.name:}") String name,
            @Value("${shopsphere.admin.email:}") String email,
            @Value("${shopsphere.admin.password:}") String password) {
        return args -> {
            if (email.isBlank() && password.isBlank()) return;
            if (email.isBlank() || password.length()<10 || name.isBlank()) throw new IllegalStateException("Set SHOPSPHERE_ADMIN_NAME, SHOPSPHERE_ADMIN_EMAIL and SHOPSPHERE_ADMIN_PASSWORD (10+ chars) together");
            users.findByEmailIgnoreCase(email.trim()).orElseGet(() -> users.save(new User(name.trim(),email.trim().toLowerCase(),encoder.encode(password),"ADMIN")));
        };
    }
}

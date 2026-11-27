package com.shopsphere.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

@Component
public class JwtService {
    private final byte[] secret;
    private final long ttl;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

    public JwtService(@Value("${security.jwt.secret}") String secret,
                      @Value("${security.jwt.ttl-seconds:3600}") long ttl) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        if (this.secret.length < 32) throw new IllegalStateException("JWT secret must contain at least 32 bytes");
        this.ttl = ttl;
    }
    public long ttl() { return ttl; }
    public String issue(User user) {
        try {
            long now = Instant.now().getEpochSecond();
            String header = encoder.encodeToString(mapper.writeValueAsBytes(Map.of("alg", "HS256", "typ", "JWT")));
            String payload = encoder.encodeToString(mapper.writeValueAsBytes(Map.of("sub", String.valueOf(user.getId()), "role", user.getRole(), "iat", now, "exp", now + ttl)));
            String unsigned = header + "." + payload;
            return unsigned + "." + encoder.encodeToString(sign(unsigned));
        } catch (Exception e) { throw new IllegalStateException("Unable to issue token", e); }
    }
    public Long subject(String token) {
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3 || !MessageDigestUtil.constantTime(sign(parts[0] + "." + parts[1]), Base64.getUrlDecoder().decode(parts[2]))) return null;
            Map<?, ?> claims = mapper.readValue(Base64.getUrlDecoder().decode(parts[1]), Map.class);
            if (!"HS256".equals(mapper.readValue(Base64.getUrlDecoder().decode(parts[0]), Map.class).get("alg"))) return null;
            if (!(claims.get("exp") instanceof Number exp) || exp.longValue() <= Instant.now().getEpochSecond()) return null;
            Object sub = claims.get("sub");
            return sub == null ? null : Long.valueOf(sub.toString());
        } catch (Exception e) { return null; }
    }
    private byte[] sign(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    }
}

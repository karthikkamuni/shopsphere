package com.shopsphere.product;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;
@Configuration @EnableMethodSecurity
public class ProductSecurityConfiguration {
 @Bean JwtDecoder jwtDecoder(@Value("${security.jwt.secret}")String secret){byte[] bytes=secret.getBytes(StandardCharsets.UTF_8);if(bytes.length<32)throw new IllegalStateException("JWT_SECRET must contain at least 32 bytes");return NimbusJwtDecoder.withSecretKey(new SecretKeySpec(bytes,"HmacSHA256")).macAlgorithm(MacAlgorithm.HS256).build();}
 @Bean JwtAuthenticationConverter jwtAuthenticationConverter(){JwtAuthenticationConverter c=new JwtAuthenticationConverter();c.setJwtGrantedAuthoritiesConverter(jwt->{String role=jwt.getClaimAsString("role");return role==null?List.of():List.of(new SimpleGrantedAuthority("ROLE_"+role));});return c;}
 @Bean SecurityFilterChain securityFilterChain(HttpSecurity http,JwtAuthenticationConverter converter)throws Exception{return http.csrf(c->c.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
  .authorizeHttpRequests(a->a.requestMatchers("/internal/**","/actuator/health/**").permitAll().requestMatchers("/api/products/**").permitAll().anyRequest().permitAll())
  .oauth2ResourceServer(o->o.jwt(j->j.jwtAuthenticationConverter(converter))).exceptionHandling(e->e.authenticationEntryPoint((req,res,ex)->res.sendError(401,"Authentication required")).accessDeniedHandler((req,res,ex)->res.sendError(403,"Insufficient permissions"))).build();}
}

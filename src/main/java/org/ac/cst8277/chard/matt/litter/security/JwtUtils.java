package org.ac.cst8277.chard.matt.litter.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.ac.cst8277.chard.matt.litter.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtEncodingException;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.ac.cst8277.chard.matt.litter.model.User.ROLES_HASHMAP_DEFAULT_CAP;

/**
 * Utility class for JWT operations.
 */
@Slf4j
@Component
public class JwtUtils {
    private static final String ROLES_CLAIM = "roles";
    private static final String ROLE_PREFIX = "ROLE_";

    @Value("${jwt.secret}")
    private String jwtSecretKey;

    @Value("${jwt.issuer}")
    private String jwtIssuer;

    @Value("${jwt.expiration-time}")
    private Long jwtExpirationLong;

    /**
     * Creates a JWT decoder bean.
     *
     * @return ReactiveJwtDecoder for decoding JWTs
     */
    @Bean
    protected ReactiveJwtDecoder jwtDecoder() {
        log.info("Initializing JWT decoder");
        return NimbusReactiveJwtDecoder
                .withSecretKey(getSigningKey())
                .macAlgorithm(MacAlgorithm.HS512)
                .build();
    }

    /**
     * Gets the signing key for JWT operations.
     *
     * @return SecretKey used for signing JWTs
     */
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecretKey.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Creates a JWT authentication converter bean.
     *
     * @return JwtAuthenticationConverter for converting JWTs to Authentication objects
     */
    @Bean
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        log.info("Initializing JWT authentication converter");
        JwtGrantedAuthoritiesConverter grantedAuthConverter = new JwtGrantedAuthoritiesConverter();
        grantedAuthConverter.setAuthoritiesClaimName(ROLES_CLAIM);
        grantedAuthConverter.setAuthorityPrefix(ROLE_PREFIX);

        JwtAuthenticationConverter jwtAuthConverter = new JwtAuthenticationConverter();
        jwtAuthConverter.setJwtGrantedAuthoritiesConverter(grantedAuthConverter);
        return jwtAuthConverter;
    }

    /**
     * Generates a JWT token for a given user.
     *
     * @param user User for whom to generate the token
     * @return String representation of the JWT token
     * @throws JwtEncodingException if the user's username is null
     */
    public String generateToken(User user) {
        if (null == user.getUsername()) {
            log.error("Cannot generate token: Username is null");
            throw new JwtEncodingException("Cannot generate token for a user with a null username.");
        }

        log.info("Generating JWT token for user: {}", user.getUsername());
        Map<String, Object> claims = HashMap.newHashMap(ROLES_HASHMAP_DEFAULT_CAP);
        claims.put(ROLES_CLAIM, user.getRoles());

        return Jwts.builder()
                .issuer(jwtIssuer)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusMillis(jwtExpirationLong)))
                .subject(user.getUsername())
                .claims(claims)
                .signWith(getSigningKey())
                .compact();
    }
}

package org.ac.cst8277.chard.matt.litter.security;

import org.ac.cst8277.chard.matt.litter.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;


/**
 * Configuration class for web security.
 */
@Configuration
@EnableWebFluxSecurity
class WebSecurityConfig {
    private static final Logger logger = LoggerFactory.getLogger(WebSecurityConfig.class);

    private static final String[] AUTH_WHITELIST = {
            "/user/login",
            "/user/register",
    };

    private static final String[] AUTH_ADMIN_ONLY = {
            "/messages/all"
    };

    /**
     * Custom authentication entry point for handling authentication failures.
     * <p>
     * This method is called when authentication fails. It sets the response status to 401,
     * sets the content type to plain text, and writes the basic error text to the response body.
     *
     * @param exchange The ServerWebExchange representing the current request and response
     * @param err      The AuthenticationException that triggered this entry point
     * @return A Mono representing the completion of the response writing
     */
    private static Mono<Void> authenticationEntryPoint(ServerWebExchange exchange, AuthenticationException err) {
        logger.warn("Authentication failed: {}", err.getMessage());
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.TEXT_PLAIN);

        String simpleErrMsg = err.getMessage().contains(":")
                ? err.getMessage().split(":")[0]
                : "Unauthorized";

        return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(simpleErrMsg.getBytes(StandardCharsets.UTF_8)))
        );
    }

    /**
     * Configure the security for the application.
     * <p>
     * This app is stateless, so basic auth and form login are disabled and no security context is stored.
     * Allows anyone to access endpoints in AUTH_WHITELIST.
     * Requires admin role for endpoints in AUTH_ADMIN_ONLY.
     * All other endpoints require authentication.
     * Uses OAuth2ResourceServer to authenticate using JWT.
     *
     * @param http             built ServerHttpSecurity object
     * @param jwtAuthConverter converter for JWT authentication
     * @return SecurityWebFilterChain object
     */
    @Bean
    protected SecurityWebFilterChain filterChain(
            ServerHttpSecurity http,
            JwtAuthenticationConverter jwtAuthConverter
    ) {
        logger.info("Configuring security filter chain");
        return http
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(AUTH_WHITELIST).permitAll()
                        .pathMatchers(AUTH_ADMIN_ONLY).hasRole(User.DB_USER_ROLE_ADMIN_NAME)
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(
                                new ReactiveJwtAuthenticationConverterAdapter(jwtAuthConverter)))
                        .authenticationEntryPoint(WebSecurityConfig::authenticationEntryPoint))
                .build();
    }
}

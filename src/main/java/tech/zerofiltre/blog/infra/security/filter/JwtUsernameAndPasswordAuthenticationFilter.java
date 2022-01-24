package tech.zerofiltre.blog.infra.security.filter;

import com.fasterxml.jackson.databind.*;
import io.jsonwebtoken.*;
import lombok.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.*;
import org.springframework.security.web.authentication.*;
import org.springframework.security.web.util.matcher.*;
import tech.zerofiltre.blog.infra.security.config.*;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import java.util.*;
import java.util.stream.*;

public class JwtUsernameAndPasswordAuthenticationFilter extends UsernamePasswordAuthenticationFilter {

    // We use auth manager to validate the user credentials
    private final AuthenticationManager authManager;

    private final JwtConfiguration jwtConfiguration;

    public JwtUsernameAndPasswordAuthenticationFilter(AuthenticationManager authManager, JwtConfiguration jwtConfiguration) {
        this.authManager = authManager;
        this.jwtConfiguration = jwtConfiguration;

        // By default, UsernamePasswordAuthenticationFilter listens to "/login" path.
        // In our case, we use "/auth". So, we need to override the defaults.
        this.setRequiresAuthenticationRequestMatcher(new AntPathRequestMatcher(jwtConfiguration.getUri(), "POST"));
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response)
            throws AuthenticationException {

        try {

            // 1. Get credentials from request
            UserCredentials creds = new ObjectMapper().readValue(request.getInputStream(), UserCredentials.class);

            // 2. Create auth object (contains credentials) which will be used by auth manager
            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                    creds.getUsername(), creds.getPassword(), Collections.emptyList());

            // 3. Authentication manager authenticate the user, and use DBUserDetailsService::loadUserByUsername() method to load the user.
                return authManager.authenticate(authToken);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // Upon successful authentication, generate a token.
    // The 'auth' passed to successfulAuthentication() is the current authenticated user.
    @Override
    protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response, FilterChain chain,
                                            Authentication auth) {

        long now = System.currentTimeMillis();
        String token = Jwts.builder()
                .setSubject(auth.getName())
                // Convert to list of strings.
                .claim("authorities", auth.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority).collect(Collectors.toList()))
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + jwtConfiguration.getExpiration() * 1000L))  // in milliseconds
                .signWith(SignatureAlgorithm.HS512, jwtConfiguration.getSecret().getBytes())
                .compact();

        // Add token to header
        response.addHeader(jwtConfiguration.getHeader(), jwtConfiguration.getPrefix() + token);
    }

    // A class just to represent the user credentials
    @Data
    private static class UserCredentials {
        private String username;
        private String password;
    }
}

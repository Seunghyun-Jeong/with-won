package com.withwon.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final AuthSessionService authSessionService;
    private final AuthErrorResponseWriter responseWriter;

    public JwtAuthenticationFilter(AuthSessionService authSessionService,
                                   AuthErrorResponseWriter responseWriter) {
        this.authSessionService = authSessionService;
        this.responseWriter = responseWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            try {
                AuthSessionService.SessionPrincipal principal = authSessionService
                        .authenticate(authorization.substring("Bearer ".length()));
                var authentication = new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + principal.role())));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (InvalidTokenException exception) {
                responseWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED,
                        "INVALID_ACCESS_TOKEN", exception.getMessage());
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}

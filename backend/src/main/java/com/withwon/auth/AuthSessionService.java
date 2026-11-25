package com.withwon.auth;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class AuthSessionService {

    private final AuthSessionRepository authSessionRepository;
    private final JwtTokenService jwtTokenService;
    private final Clock clock;

    public AuthSessionService(AuthSessionRepository authSessionRepository,
                              JwtTokenService jwtTokenService) {
        this(authSessionRepository, jwtTokenService, Clock.systemUTC());
    }

    AuthSessionService(AuthSessionRepository authSessionRepository,
                       JwtTokenService jwtTokenService,
                       Clock clock) {
        this.authSessionRepository = authSessionRepository;
        this.jwtTokenService = jwtTokenService;
        this.clock = clock;
    }

    @Transactional
    public SessionPrincipal authenticate(String accessToken) {
        JwtTokenService.JwtClaims claims = jwtTokenService.verify(accessToken);
        AuthSession session = authSessionRepository
                .findByTokenHashAndRevokedAtIsNull(sha256(accessToken))
                .filter(found -> found.isActiveAt(LocalDateTime.now(clock)))
                .filter(found -> found.matches(claims.userId(), claims.role()))
                .orElseThrow(InvalidTokenException::new);

        session.touch(LocalDateTime.now(clock));
        return new SessionPrincipal(claims.userId(), claims.role());
    }

    @Transactional
    public void logout(String accessToken) {
        authSessionRepository.findByTokenHashAndRevokedAtIsNull(sha256(accessToken))
                .ifPresent(session -> session.revoke(LocalDateTime.now(clock)));
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    public record SessionPrincipal(Long userId, String role) {
    }
}

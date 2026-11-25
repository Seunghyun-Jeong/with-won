package com.withwon.auth;

import com.withwon.domain.customer.Customer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthSessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-25T00:00:00Z");

    private AuthSessionRepository authSessionRepository;
    private JwtTokenService jwtTokenService;
    private AuthSessionService authSessionService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        authSessionRepository = mock(AuthSessionRepository.class);
        jwtTokenService = new JwtTokenService("01234567890123456789012345678901", 3600, clock);
        authSessionService = new AuthSessionService(authSessionRepository, jwtTokenService, clock);
    }

    @Test
    void authenticatesActiveCustomerSession() {
        Customer customer = mock(Customer.class);
        when(customer.getId()).thenReturn(7L);
        String token = jwtTokenService.issue(7L, "CUSTOMER");
        AuthSession session = AuthSession.forCustomer(
                customer,
                sha256(token),
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC),
                LocalDateTime.ofInstant(NOW.plusSeconds(3600), ZoneOffset.UTC));
        when(authSessionRepository.findByTokenHashAndRevokedAtIsNull(sha256(token)))
                .thenReturn(Optional.of(session));

        AuthSessionService.SessionPrincipal principal = authSessionService.authenticate(token);

        assertEquals(7L, principal.userId());
        assertEquals("CUSTOMER", principal.role());
    }

    @Test
    void rejectsRevokedOrMissingSession() {
        String token = jwtTokenService.issue(7L, "CUSTOMER");
        when(authSessionRepository.findByTokenHashAndRevokedAtIsNull(sha256(token)))
                .thenReturn(Optional.empty());

        assertThrows(InvalidTokenException.class, () -> authSessionService.authenticate(token));
    }

    private String sha256(String value) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }
}

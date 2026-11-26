package com.withwon.auth;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenServiceTest {

    private static final String SECRET = "01234567890123456789012345678901";
    private static final Instant ISSUED_AT = Instant.parse("2026-11-25T00:00:00Z");

    @Test
    void issuesSignedJwtWithCustomerClaims() {
        JwtTokenService service = new JwtTokenService(
                SECRET,
                3600,
                Clock.fixed(ISSUED_AT, ZoneOffset.UTC));

        String token = service.issue(42L, "CUSTOMER");
        String[] parts = token.split("\\.");

        assertThat(parts).hasSize(3);
        assertThat(decode(parts[0])).contains("\"alg\":\"HS256\"");
        assertThat(decode(parts[1]))
                .contains("\"sub\":\"42\"")
                .contains("\"role\":\"CUSTOMER\"")
                .contains("\"iat\":1795564800")
                .contains("\"exp\":1795568400");
        assertThat(parts[2]).isNotBlank();
    }

    private String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value));
    }
}

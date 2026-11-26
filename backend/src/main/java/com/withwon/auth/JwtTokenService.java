package com.withwon.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
public class JwtTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final byte[] secret;
    private final long expirationSeconds;
    private final Clock clock;

    public JwtTokenService(
            @Value("${withwon.auth.jwt-secret}") String secret,
            @Value("${withwon.auth.jwt-expiration-seconds:3600}") long expirationSeconds) {
        this(secret, expirationSeconds, Clock.systemUTC());
    }

    JwtTokenService(String secret, long expirationSeconds, Clock clock) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("WITHWON_JWT_SECRET must be at least 32 bytes.");
        }
        if (expirationSeconds <= 0) {
            throw new IllegalArgumentException("WITHWON_JWT_EXPIRATION_SECONDS must be positive.");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.expirationSeconds = expirationSeconds;
        this.clock = clock;
    }

    public String issue(Long userId, String role) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plusSeconds(expirationSeconds);
        String header = encode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = encode("{\"sub\":\"" + userId + "\",\"role\":\"" + role
                + "\",\"iat\":" + issuedAt.getEpochSecond()
                + ",\"exp\":" + expiresAt.getEpochSecond() + "}");
        String unsignedToken = header + "." + payload;
        return unsignedToken + "." + sign(unsignedToken);
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }

    public JwtClaims verify(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidTokenException();
        }

        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) {
            throw new InvalidTokenException();
        }

        String unsignedToken = parts[0] + "." + parts[1];
        byte[] actualSignature = decode(parts[2]);
        byte[] expectedSignature = decode(sign(unsignedToken));
        if (!java.security.MessageDigest.isEqual(actualSignature, expectedSignature)) {
            throw new InvalidTokenException();
        }

        String payload = new String(decode(parts[1]), StandardCharsets.UTF_8);
        Map<String, String> claims = parseClaims(payload);
        try {
            long userId = Long.parseLong(claims.get("sub"));
            String role = claims.get("role");
            long expiresAt = Long.parseLong(claims.get("exp"));
            if (role == null || role.isBlank() || Instant.now(clock).getEpochSecond() >= expiresAt) {
                throw new InvalidTokenException();
            }
            return new JwtClaims(userId, role, Instant.ofEpochSecond(expiresAt));
        } catch (NumberFormatException | NullPointerException exception) {
            throw new InvalidTokenException();
        }
    }

    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("JWT signing failed.", exception);
        }
    }

    private byte[] decode(String value) {
        try {
            return Base64.getUrlDecoder().decode(value);
        } catch (IllegalArgumentException exception) {
            throw new InvalidTokenException();
        }
    }

    private Map<String, String> parseClaims(String payload) {
        Map<String, String> claims = new HashMap<>();
        String body = payload.trim();
        if (!body.startsWith("{") || !body.endsWith("}")) {
            throw new InvalidTokenException();
        }
        for (String entry : body.substring(1, body.length() - 1).split(",")) {
            String[] pair = entry.split(":", 2);
            if (pair.length != 2) {
                throw new InvalidTokenException();
            }
            String key = pair[0].trim().replace("\"", "");
            String value = pair[1].trim().replace("\"", "");
            claims.put(key, value);
        }
        return claims;
    }

    public record JwtClaims(Long userId, String role, Instant expiresAt) {
    }
}

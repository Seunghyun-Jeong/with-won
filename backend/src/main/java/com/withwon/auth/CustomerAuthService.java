package com.withwon.auth;

import com.withwon.domain.customer.Customer;
import com.withwon.domain.customer.CustomerRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class CustomerAuthService {

    private static final String CUSTOMER_ROLE = "CUSTOMER";

    private final CustomerRepository customerRepository;
    private final AuthSessionRepository authSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final Clock clock;

    @Autowired
    public CustomerAuthService(CustomerRepository customerRepository,
                               AuthSessionRepository authSessionRepository,
                               PasswordEncoder passwordEncoder,
                               JwtTokenService jwtTokenService) {
        this(customerRepository, authSessionRepository, passwordEncoder, jwtTokenService, Clock.systemUTC());
    }

    CustomerAuthService(CustomerRepository customerRepository,
                        AuthSessionRepository authSessionRepository,
                        PasswordEncoder passwordEncoder,
                        JwtTokenService jwtTokenService,
                        Clock clock) {
        this.customerRepository = customerRepository;
        this.authSessionRepository = authSessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.clock = clock;
    }

    @Transactional
    public CustomerLoginResponse login(CustomerLoginRequest request) {
        Customer customer = customerRepository.findByLoginIdAndActiveTrue(request.loginId())
                .filter(found -> passwordEncoder.matches(request.password(), found.getPasswordHash()))
                .orElseThrow(InvalidCredentialsException::new);

        LocalDateTime now = LocalDateTime.now(clock);
        String accessToken = jwtTokenService.issue(customer.getId(), CUSTOMER_ROLE);
        String tokenHash = sha256(accessToken);
        LocalDateTime expiresAt = now.plusSeconds(jwtTokenService.getExpirationSeconds());

        authSessionRepository.save(AuthSession.forCustomer(customer, tokenHash, now, expiresAt));

        return new CustomerLoginResponse(
                accessToken,
                "Bearer",
                jwtTokenService.getExpirationSeconds(),
                customer.getId(),
                CUSTOMER_ROLE);
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
}

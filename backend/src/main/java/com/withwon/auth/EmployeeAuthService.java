package com.withwon.auth;

import com.withwon.domain.employee.Employee;
import com.withwon.domain.employee.EmployeeRepository;
import jakarta.transaction.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class EmployeeAuthService {

    private static final String EMPLOYEE_ROLE = "EMPLOYEE";

    private final EmployeeRepository employeeRepository;
    private final AuthSessionRepository authSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final Clock clock;

    public EmployeeAuthService(EmployeeRepository employeeRepository,
                               AuthSessionRepository authSessionRepository,
                               PasswordEncoder passwordEncoder,
                               JwtTokenService jwtTokenService) {
        this(employeeRepository, authSessionRepository, passwordEncoder, jwtTokenService, Clock.systemUTC());
    }

    EmployeeAuthService(EmployeeRepository employeeRepository,
                        AuthSessionRepository authSessionRepository,
                        PasswordEncoder passwordEncoder,
                        JwtTokenService jwtTokenService,
                        Clock clock) {
        this.employeeRepository = employeeRepository;
        this.authSessionRepository = authSessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.clock = clock;
    }

    @Transactional
    public EmployeeLoginResponse login(EmployeeLoginRequest request) {
        Employee employee = employeeRepository.findByLoginIdAndActiveTrue(request.employeeNumber())
                .filter(found -> passwordEncoder.matches(request.password(), found.getPasswordHash()))
                .orElseThrow(InvalidCredentialsException::new);

        LocalDateTime now = LocalDateTime.now(clock);
        String accessToken = jwtTokenService.issue(employee.getId(), EMPLOYEE_ROLE);
        String tokenHash = sha256(accessToken);
        LocalDateTime expiresAt = now.plusSeconds(jwtTokenService.getExpirationSeconds());

        authSessionRepository.save(AuthSession.forEmployee(employee, tokenHash, now, expiresAt));

        return new EmployeeLoginResponse(
                accessToken,
                "Bearer",
                jwtTokenService.getExpirationSeconds(),
                employee.getId(),
                EMPLOYEE_ROLE);
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

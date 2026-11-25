package com.withwon.auth;

import com.withwon.domain.employee.Employee;
import com.withwon.domain.employee.EmployeeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeAuthServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private AuthSessionRepository authSessionRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private Employee employee;

    @Test
    void savesEmployeeSessionWhenCredentialsAreValid() {
        EmployeeAuthService service = new EmployeeAuthService(
                employeeRepository,
                authSessionRepository,
                passwordEncoder,
                jwtTokenService,
                Clock.fixed(Instant.parse("2026-11-25T00:00:00Z"), ZoneOffset.UTC));

        when(employeeRepository.findByLoginIdAndActiveTrue("employee01"))
                .thenReturn(Optional.of(employee));
        when(employee.getPasswordHash()).thenReturn("$2a$hashed-password");
        when(employee.getId()).thenReturn(11L);
        when(passwordEncoder.matches("password", "$2a$hashed-password")).thenReturn(true);
        when(jwtTokenService.issue(11L, "EMPLOYEE")).thenReturn("employee-jwt-token");
        when(jwtTokenService.getExpirationSeconds()).thenReturn(3600L);

        EmployeeLoginResponse response = service.login(
                new EmployeeLoginRequest("employee01", "password"));

        assertThat(response.accessToken()).isEqualTo("employee-jwt-token");
        assertThat(response.userId()).isEqualTo(11L);
        assertThat(response.role()).isEqualTo("EMPLOYEE");

        ArgumentCaptor<AuthSession> sessionCaptor = ArgumentCaptor.forClass(AuthSession.class);
        verify(authSessionRepository).save(sessionCaptor.capture());
        assertThat(sessionCaptor.getValue().getTokenHash()).isNotEqualTo("employee-jwt-token");
        assertThat(sessionCaptor.getValue().getExpiresAt())
                .isEqualTo(java.time.LocalDateTime.of(2026, 11, 25, 1, 0));
    }

    @Test
    void rejectsInvalidEmployeePasswordWithoutCreatingSession() {
        EmployeeAuthService service = new EmployeeAuthService(
                employeeRepository,
                authSessionRepository,
                passwordEncoder,
                jwtTokenService,
                Clock.systemUTC());

        when(employeeRepository.findByLoginIdAndActiveTrue("employee01"))
                .thenReturn(Optional.of(employee));
        when(employee.getPasswordHash()).thenReturn("$2a$hashed-password");
        when(passwordEncoder.matches("wrong", "$2a$hashed-password")).thenReturn(false);

        assertThatThrownBy(() -> service.login(
                new EmployeeLoginRequest("employee01", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(authSessionRepository, never()).save(any(AuthSession.class));
    }
}

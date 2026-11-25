package com.withwon.auth;

import com.withwon.domain.customer.Customer;
import com.withwon.domain.customer.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
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
class CustomerAuthServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private AuthSessionRepository authSessionRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private Customer customer;

    @InjectMocks
    private CustomerAuthService customerAuthService;

    @Test
    void savesSessionAndReturnsTokenWhenCredentialsAreValid() {
        CustomerAuthService service = new CustomerAuthService(
                customerRepository,
                authSessionRepository,
                passwordEncoder,
                jwtTokenService,
                Clock.fixed(Instant.parse("2026-11-25T00:00:00Z"), ZoneOffset.UTC));

        when(customerRepository.findByLoginIdAndActiveTrue("customer01"))
                .thenReturn(Optional.of(customer));
        when(customer.getPasswordHash()).thenReturn("$2a$hashed-password");
        when(customer.getId()).thenReturn(7L);
        when(passwordEncoder.matches("password", "$2a$hashed-password")).thenReturn(true);
        when(jwtTokenService.issue(7L, "CUSTOMER")).thenReturn("jwt-token");
        when(jwtTokenService.getExpirationSeconds()).thenReturn(3600L);

        CustomerLoginResponse response = service.login(
                new CustomerLoginRequest("customer01", "password"));

        assertThat(response.accessToken()).isEqualTo("jwt-token");
        assertThat(response.userId()).isEqualTo(7L);
        assertThat(response.role()).isEqualTo("CUSTOMER");

        ArgumentCaptor<AuthSession> sessionCaptor = ArgumentCaptor.forClass(AuthSession.class);
        verify(authSessionRepository).save(sessionCaptor.capture());
        assertThat(sessionCaptor.getValue().getTokenHash()).isNotEqualTo("jwt-token");
        assertThat(sessionCaptor.getValue().getExpiresAt())
                .isEqualTo(java.time.LocalDateTime.of(2026, 11, 25, 1, 0));
    }

    @Test
    void rejectsInvalidPasswordWithoutCreatingSession() {
        CustomerAuthService service = new CustomerAuthService(
                customerRepository,
                authSessionRepository,
                passwordEncoder,
                jwtTokenService,
                Clock.systemUTC());

        when(customerRepository.findByLoginIdAndActiveTrue("customer01"))
                .thenReturn(Optional.of(customer));
        when(customer.getPasswordHash()).thenReturn("$2a$hashed-password");
        when(passwordEncoder.matches("wrong", "$2a$hashed-password")).thenReturn(false);

        assertThatThrownBy(() -> service.login(
                new CustomerLoginRequest("customer01", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(authSessionRepository, never()).save(any(AuthSession.class));
    }
}

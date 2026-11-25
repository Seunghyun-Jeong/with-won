package com.withwon.auth;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final CustomerAuthService customerAuthService;
    private final EmployeeAuthService employeeAuthService;
    private final AuthSessionService authSessionService;

    public AuthController(CustomerAuthService customerAuthService,
                          EmployeeAuthService employeeAuthService,
                          AuthSessionService authSessionService) {
        this.customerAuthService = customerAuthService;
        this.employeeAuthService = employeeAuthService;
        this.authSessionService = authSessionService;
    }

    @PostMapping("/customer/login")
    public ResponseEntity<CustomerLoginResponse> login(
            @Valid @RequestBody CustomerLoginRequest request) {
        return ResponseEntity.ok(customerAuthService.login(request));
    }

    @PostMapping("/employee/login")
    public ResponseEntity<EmployeeLoginResponse> employeeLogin(
            @Valid @RequestBody EmployeeLoginRequest request) {
        return ResponseEntity.ok(employeeAuthService.login(request));
    }

    @GetMapping("/session")
    public ResponseEntity<SessionResponse> session(Authentication authentication) {
        AuthSessionService.SessionPrincipal principal =
                (AuthSessionService.SessionPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(new SessionResponse(principal.userId(), principal.role()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            authSessionService.logout(authorization.substring("Bearer ".length()));
        }
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}

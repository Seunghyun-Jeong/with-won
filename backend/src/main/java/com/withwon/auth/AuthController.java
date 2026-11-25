package com.withwon.auth;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/customer")
public class AuthController {

    private final CustomerAuthService customerAuthService;
    private final EmployeeAuthService employeeAuthService;

    public AuthController(CustomerAuthService customerAuthService,
                          EmployeeAuthService employeeAuthService) {
        this.customerAuthService = customerAuthService;
        this.employeeAuthService = employeeAuthService;
    }

    @PostMapping("/login")
    public ResponseEntity<CustomerLoginResponse> login(
            @Valid @RequestBody CustomerLoginRequest request) {
        return ResponseEntity.ok(customerAuthService.login(request));
    }

    @PostMapping("/employee/login")
    public ResponseEntity<EmployeeLoginResponse> employeeLogin(
            @Valid @RequestBody EmployeeLoginRequest request) {
        return ResponseEntity.ok(employeeAuthService.login(request));
    }
}

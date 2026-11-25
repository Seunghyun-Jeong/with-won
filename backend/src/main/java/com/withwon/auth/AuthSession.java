package com.withwon.auth;

import com.withwon.domain.customer.Customer;
import com.withwon.domain.employee.Employee;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "auth_sessions")
public class AuthSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "last_activity_at", nullable = false)
    private LocalDateTime lastActivityAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    protected AuthSession() {
    }

    private AuthSession(Customer customer, Employee employee, String tokenHash,
                        LocalDateTime lastActivityAt, LocalDateTime expiresAt) {
        this.customer = customer;
        this.employee = employee;
        this.tokenHash = tokenHash;
        this.lastActivityAt = lastActivityAt;
        this.expiresAt = expiresAt;
    }

    public static AuthSession forCustomer(Customer customer, String tokenHash,
                                          LocalDateTime now, LocalDateTime expiresAt) {
        return new AuthSession(customer, null, tokenHash, now, expiresAt);
    }

    public static AuthSession forEmployee(Employee employee, String tokenHash,
                                          LocalDateTime now, LocalDateTime expiresAt) {
        return new AuthSession(null, employee, tokenHash, now, expiresAt);
    }

    public Long getId() {
        return id;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public LocalDateTime getLastActivityAt() {
        return lastActivityAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }

    public void revoke(LocalDateTime revokedAt) {
        this.revokedAt = revokedAt;
    }
}

package com.withwon.customer.incident.controller;

import com.withwon.domain.customer.Customer;

public record CustomerProfileResponse(Long customerId, String name, String phone) {

    public static CustomerProfileResponse from(Customer customer) {
        return new CustomerProfileResponse(customer.getId(), customer.getName(), customer.getPhone());
    }
}

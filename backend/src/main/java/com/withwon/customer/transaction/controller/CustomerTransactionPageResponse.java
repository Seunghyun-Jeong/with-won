package com.withwon.customer.transaction.controller;

import org.springframework.data.domain.Page;

import java.util.List;

public record CustomerTransactionPageResponse(
        List<CustomerTransactionResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static CustomerTransactionPageResponse from(Page<CustomerTransactionResponse> page) {
        return new CustomerTransactionPageResponse(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}

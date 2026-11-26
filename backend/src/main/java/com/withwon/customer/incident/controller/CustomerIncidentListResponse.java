package com.withwon.customer.incident.controller;

import org.springframework.data.domain.Page;

import java.util.List;

public record CustomerIncidentListResponse(
        List<CustomerIncidentListItemResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static CustomerIncidentListResponse from(Page<com.withwon.domain.incident.Incident> incidents) {
        return new CustomerIncidentListResponse(
                incidents.map(CustomerIncidentListItemResponse::from).getContent(),
                incidents.getNumber(),
                incidents.getSize(),
                incidents.getTotalElements(),
                incidents.getTotalPages());
    }
}

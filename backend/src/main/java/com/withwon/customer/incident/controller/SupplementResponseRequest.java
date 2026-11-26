package com.withwon.customer.incident.controller;

import java.util.List;

public record SupplementResponseRequest(
        String message,
        List<Long> evidenceVersionIds,
        List<Long> formVersionIds
) {
}

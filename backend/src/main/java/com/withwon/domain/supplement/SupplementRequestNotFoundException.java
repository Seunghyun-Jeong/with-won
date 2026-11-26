package com.withwon.domain.supplement;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class SupplementRequestNotFoundException extends RuntimeException {

    public SupplementRequestNotFoundException() {
        super("Supplement request was not found.");
    }
}

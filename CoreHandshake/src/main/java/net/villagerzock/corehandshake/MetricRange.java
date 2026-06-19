package net.villagerzock.corehandshake;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public enum MetricRange {
    DAYS,
    HOURS,
    MINUTES;

    public static MetricRange parse(String value) {
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid metric range");
        }
    }

    public String queryValue() {
        return name().toLowerCase();
    }
}

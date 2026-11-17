package com.company.expense.entity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public final class Timestamps {

    private Timestamps() {
    }

    // PostgreSQL TIMESTAMPTZ keeps microseconds; truncating here keeps saved and reloaded values identical.
    public static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}

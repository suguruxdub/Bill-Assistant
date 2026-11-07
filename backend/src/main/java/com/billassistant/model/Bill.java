package com.billassistant.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Bill(
        long id,
        String title,
        BigDecimal amount,
        LocalDate billingDate,
        String category,
        boolean paid
) {
}

package com.tradepulse.notification.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record CreateAlertRequest(
        @NotBlank @Pattern(regexp = "[A-Z]{3,10}") String symbol,
        /** ABOVE or BELOW */
        @NotBlank @Pattern(regexp = "ABOVE|BELOW") String condition,
        @NotNull @DecimalMin("0.00000001") BigDecimal targetPrice
) {}

package com.tradepulse.order.dto.request;

import com.tradepulse.order.domain.enums.OrderSide;
import com.tradepulse.order.domain.enums.OrderType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record PlaceOrderRequest(
        @NotBlank @Pattern(regexp = "[A-Z]{3,10}") String symbol,
        @NotNull OrderSide side,
        @NotNull OrderType orderType,
        @NotNull @DecimalMin("0.00000001") BigDecimal quantity,
        /** Required for LIMIT orders; must be null for MARKET orders. */
        @DecimalMin("0.00000001") BigDecimal price
) {}

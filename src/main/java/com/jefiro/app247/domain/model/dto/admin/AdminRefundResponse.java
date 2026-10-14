package com.jefiro.app247.domain.model.dto.admin;

import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.PaymentAttempt;
import java.math.BigDecimal;
import java.time.Instant;

public record AdminRefundResponse(
        String orderId,
        String status,
        BigDecimal amount,
        Instant requestedAt,
        Instant confirmedAt,
        boolean confirmed
) {
    public static AdminRefundResponse from(Order order) {
        PaymentAttempt attempt = order.getPagamento();
        return new AdminRefundResponse(
                order.getIdOrder(), order.getStatus().getValue(), order.getTotalCobrado(),
                attempt != null ? attempt.getRefundRequestedAt() : null,
                attempt != null ? attempt.getRefundConfirmedAt() : null,
                attempt != null && attempt.getRefundConfirmedAt() != null);
    }
}

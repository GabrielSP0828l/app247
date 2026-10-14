package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.PaymentAttempt;
import com.jefiro.app247.domain.model.enum_type.PagamentoStatus;
import com.jefiro.app247.domain.model.enum_type.order.OrderStatus;
import com.jefiro.app247.infra.exception.ApiBusinessException;
import com.jefiro.app247.infra.repository.OrderRepository;
import com.jefiro.app247.infra.repository.PagamentoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentRefundPersistenceService {
    private final OrderRepository orders;
    private final PagamentoRepository attempts;
    private final AuditLogService audit;

    public PaymentRefundPersistenceService(OrderRepository orders, PagamentoRepository attempts,
                                           AuditLogService audit) {
        this.orders = orders;
        this.attempts = attempts;
        this.audit = audit;
    }

    @Transactional
    public Order markRequested(String orderId) {
        String empresaId = EmpresaContext.require();
        Order order = orders.findForAdminRefund(orderId, empresaId).orElseThrow(() ->
                new ApiBusinessException(HttpStatus.NOT_FOUND, "PURCHASE_NOT_FOUND",
                        "Compra não encontrada"));
        PaymentAttempt attempt = order.getPagamento();
        if (attempt == null || attempt.getProviderOrderId() == null
                || attempt.getProviderOrderId().isBlank()) {
            throw conflict("REFUND_REMOTE_ORDER_MISSING",
                    "Compra sem identificação financeira para reembolso");
        }
        if (order.getStatus() == OrderStatus.REFUNDED
                && attempt.getStatus() == PagamentoStatus.REFUNDED) {
            return order;
        }
        if (order.getStatus() != OrderStatus.PROCESSED
                || attempt.getStatus() != PagamentoStatus.PROCESSED) {
            throw conflict("REFUND_NOT_ALLOWED", "Somente compra aprovada pode ser reembolsada");
        }
        if (attempt.getRefundRequestedAt() == null) {
            attempt.setRefundRequestedAt(Instant.now());
            attempt.setRefundIdempotencyKey("refund-" + UUID.randomUUID());
            attempts.saveAndFlush(attempt);
            audit.record(order.getEmpresa(), "PAYMENT_REFUND_REQUESTED", "Order", orderId,
                    Map.of("status", OrderStatus.PROCESSED.name()),
                    Map.of("status", "REFUND_REQUESTED"), Map.of("fullRefund", true));
        }
        return order;
    }

    private static ApiBusinessException conflict(String code, String message) {
        return new ApiBusinessException(HttpStatus.CONFLICT, code, message);
    }
}

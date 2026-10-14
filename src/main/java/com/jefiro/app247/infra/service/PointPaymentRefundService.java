package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.dto.MercadoPagoRefundResponse;
import com.jefiro.app247.domain.model.dto.admin.AdminRefundResponse;
import com.jefiro.app247.domain.model.enum_type.order.OrderStatus;
import com.jefiro.app247.infra.exception.ExternalServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PointPaymentRefundService {
    private static final Logger log = LoggerFactory.getLogger(PointPaymentRefundService.class);
    private final PaymentRefundPersistenceService persistence;
    private final OrderService orders;
    private final MercadoPagoCobrancaService mercadoPago;
    private final PaymentStateTransitionService transitions;
    private final PaymentReconciliationService reconciliation;

    public PointPaymentRefundService(PaymentRefundPersistenceService persistence,
                                     OrderService orders,
                                     MercadoPagoCobrancaService mercadoPago,
                                     PaymentStateTransitionService transitions,
                                     PaymentReconciliationService reconciliation) {
        this.persistence = persistence;
        this.orders = orders;
        this.mercadoPago = mercadoPago;
        this.transitions = transitions;
        this.reconciliation = reconciliation;
    }

    public AdminRefundResponse refund(String orderId) {
        Order requested = persistence.markRequested(orderId);
        if (requested.getStatus() == OrderStatus.REFUNDED) {
            return AdminRefundResponse.from(requested);
        }
        Order order = orders.getOrderForReconciliation(orderId);
        log.warn("[PAYMENT-REFUND] reembolso integral solicitado orderId={} attemptId={} remoteOrderId={}",
                orderId, order.getPagamento().getIdPagamento(), order.getMpOrderId());
        try {
            MercadoPagoRefundResponse remote = mercadoPago.refundRemoteOrder(order);
            transitions.apply(MercadoPagoOrderState.fromRefund(remote, order.getPagamento()));
        } catch (ExternalServiceException uncertain) {
            log.warn("[PAYMENT-REFUND] resposta não confirmada; reconciliando orderId={} failureType={}",
                    orderId, uncertain.getFailureType());
            if (!reconciliation.reconcileRefund(orderId, "REFUND_REQUEST")) throw uncertain;
        }
        return AdminRefundResponse.from(orders.getOrderForReconciliation(orderId));
    }
}

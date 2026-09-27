package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.dto.OrderResponse;
import com.jefiro.app247.domain.model.dto.PaymentStatusResponse;
import com.jefiro.app247.domain.model.enum_type.order.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.EnumSet;

@Service
public class PointPaymentCancellationService {
    private static final Logger log = LoggerFactory.getLogger(PointPaymentCancellationService.class);
    private static final EnumSet<OrderStatus> TERMINAL = EnumSet.of(
            OrderStatus.PROCESSED, OrderStatus.CANCELED, OrderStatus.EXPIRED,
            OrderStatus.FAILED, OrderStatus.REFUNDED);

    @Autowired PaymentCancellationPersistenceService persistenceService;
    @Autowired OrderService orderService;
    @Autowired PaymentReconciliationService reconciliationService;
    @Autowired MercadoPagoCobrancaService mercadoPagoCobrancaService;
    @Autowired PaymentStateTransitionService transitionService;

    public PaymentStatusResponse cancel(String orderId, String terminalId) {
        persistenceService.markRequested(orderId, terminalId);
        Order order = orderService.getOrderForReconciliation(orderId);
        log.warn("[PAYMENT-CANCEL] cancelamento integral solicitado orderId={} attemptId={} remoteOrderId={}",
                orderId, order.getPagamento().getIdPagamento(), order.getMpOrderId());
        if (TERMINAL.contains(order.getStatus())) {
            return PaymentStatusResponse.from(order, false);
        }

        PaymentStatusResponse reconciled = reconciliationService.reconcileForTerminal(orderId, terminalId);
        order = orderService.getOrderForTerminal(orderId, terminalId);
        if (TERMINAL.contains(order.getStatus())) {
            return PaymentStatusResponse.from(order, reconciled.reconciled());
        }
        if (order.getMpOrderId() == null) {
            log.warn("[PAYMENT-CANCEL] identidade remota ainda desconhecida; tentativa permanece bloqueada orderId={}", orderId);
            return PaymentStatusResponse.from(order, reconciled.reconciled());
        }

        try {
            OrderResponse remote = mercadoPagoCobrancaService.cancelRemoteOrder(order);
            transitionService.apply(MercadoPagoOrderState.from(remote));
            Order current = orderService.getOrderForTerminal(orderId, terminalId);
            log.info("[PAYMENT-CANCEL] resposta autoritativa orderId={} status={}",
                    orderId, current.getStatus());
            return PaymentStatusResponse.from(current, true);
        } catch (RuntimeException error) {
            log.warn("[PAYMENT-CANCEL] cancelamento não confirmado; mantendo bloqueio orderId={} errorType={}",
                    orderId, error.getClass().getSimpleName());
            return reconciliationService.reconcileForTerminal(orderId, terminalId);
        }
    }
}

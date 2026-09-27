package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.PaymentAttempt;
import com.jefiro.app247.domain.model.dto.PaymentStatusResponse;
import com.jefiro.app247.domain.model.enum_type.order.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PointPaymentCancellationServiceTest {
    @Mock PaymentCancellationPersistenceService persistence;
    @Mock OrderService orderService;
    @Mock PaymentReconciliationService reconciliation;
    @Mock MercadoPagoCobrancaService mercadoPago;
    @Mock PaymentStateTransitionService transition;
    PointPaymentCancellationService service;
    Order order;

    @BeforeEach
    void setUp() {
        service = new PointPaymentCancellationService();
        service.persistenceService = persistence;
        service.orderService = orderService;
        service.reconciliationService = reconciliation;
        service.mercadoPagoCobrancaService = mercadoPago;
        service.transitionService = transition;
        order = new Order();
        order.setIdOrder("order-a");
        order.setStatus(OrderStatus.CREATED);
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setIdPagamento("attempt-a");
        attempt.setProviderOrderId("mp-order-a");
        attempt.setCancellationRequestedAt(java.time.Instant.now());
        order.setPagamento(attempt);
    }

    @Test
    void timeoutNaoLiberaTentativaEContinuaMarcadaParaReconciliacao() {
        when(persistence.markRequested("order-a", "terminal-a")).thenReturn(order);
        when(orderService.getOrderForReconciliation("order-a")).thenReturn(order);
        when(reconciliation.reconcileForTerminal("order-a", "terminal-a"))
                .thenReturn(PaymentStatusResponse.from(order, false));
        when(orderService.getOrderForTerminal("order-a", "terminal-a")).thenReturn(order);
        when(mercadoPago.cancelRemoteOrder(order)).thenThrow(new RuntimeException("timeout"));

        PaymentStatusResponse result = service.cancel("order-a", "terminal-a");

        assertThat(result.status().name()).isEqualTo("WAITING_PAYMENT");
        assertThat(result.cancellationRequested()).isTrue();
        verify(reconciliation, times(2)).reconcileForTerminal("order-a", "terminal-a");
    }

    @Test
    void aprovacaoEncontradaAntesDoCancelamentoVenceACorrida() {
        when(persistence.markRequested("order-a", "terminal-a")).thenReturn(order);
        when(orderService.getOrderForReconciliation("order-a")).thenReturn(order);
        Order approved = new Order();
        approved.setIdOrder("order-a");
        approved.setStatus(OrderStatus.PROCESSED);
        approved.setPagamento(order.getPagamento());
        when(reconciliation.reconcileForTerminal("order-a", "terminal-a"))
                .thenReturn(PaymentStatusResponse.from(approved, true));
        when(orderService.getOrderForTerminal("order-a", "terminal-a")).thenReturn(approved);

        PaymentStatusResponse result = service.cancel("order-a", "terminal-a");

        assertThat(result.status().name()).isEqualTo("APPROVED");
        verifyNoInteractions(mercadoPago, transition);
    }
}

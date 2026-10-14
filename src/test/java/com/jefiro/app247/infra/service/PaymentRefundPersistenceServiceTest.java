package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.PaymentAttempt;
import com.jefiro.app247.domain.model.enum_type.PagamentoStatus;
import com.jefiro.app247.domain.model.enum_type.order.OrderStatus;
import com.jefiro.app247.infra.exception.ApiBusinessException;
import com.jefiro.app247.infra.repository.OrderRepository;
import com.jefiro.app247.infra.repository.PagamentoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentRefundPersistenceServiceTest {
    @Mock OrderRepository orders;
    @Mock PagamentoRepository attempts;
    @Mock AuditLogService audit;
    @InjectMocks PaymentRefundPersistenceService service;

    @AfterEach void clear(){EmpresaContext.clear();}

    @Test
    void persisteIntencaoEReutilizaMesmaChaveNoRetry() {
        EmpresaContext.set("empresa-a");
        Order order = approved();
        when(orders.findForAdminRefund("order-a", "empresa-a")).thenReturn(Optional.of(order));

        service.markRequested("order-a");
        String key = order.getPagamento().getRefundIdempotencyKey();
        service.markRequested("order-a");

        assertThat(key).startsWith("refund-");
        assertThat(order.getPagamento().getRefundIdempotencyKey()).isEqualTo(key);
        verify(attempts, times(1)).saveAndFlush(order.getPagamento());
        verify(audit, times(1)).record(eq(order.getEmpresa()), eq("PAYMENT_REFUND_REQUESTED"),
                eq("Order"), eq("order-a"), any(), any(), any());
    }

    @Test
    void naoEncontraOrderDeOutroTenant() {
        EmpresaContext.set("empresa-b");
        when(orders.findForAdminRefund("order-a", "empresa-b")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRequested("order-a"))
                .isInstanceOf(ApiBusinessException.class)
                .extracting(error -> ((ApiBusinessException) error).getCode())
                .isEqualTo("PURCHASE_NOT_FOUND");
        verifyNoInteractions(attempts, audit);
    }

    @Test
    void rejeitaOrderQueNaoEstaAprovada() {
        EmpresaContext.set("empresa-a");
        Order order = approved();
        order.setStatus(OrderStatus.CANCELED);
        when(orders.findForAdminRefund("order-a", "empresa-a")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.markRequested("order-a"))
                .isInstanceOf(ApiBusinessException.class)
                .extracting(error -> ((ApiBusinessException) error).getCode())
                .isEqualTo("REFUND_NOT_ALLOWED");
    }

    private Order approved() {
        Empresa empresa = Empresa.builder().id("empresa-a").build();
        Order order = new Order();
        order.setIdOrder("order-a"); order.setEmpresa(empresa); order.setStatus(OrderStatus.PROCESSED);
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setIdPagamento("attempt-a"); attempt.setAttemptNumber(1);
        attempt.setProviderOrderId("mp-order-a"); attempt.setStatus(PagamentoStatus.PROCESSED);
        order.setPagamento(attempt);
        return order;
    }
}

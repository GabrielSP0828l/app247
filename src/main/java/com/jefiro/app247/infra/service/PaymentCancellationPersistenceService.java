package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.PaymentAttempt;
import com.jefiro.app247.domain.model.enum_type.PagamentoStatus;
import com.jefiro.app247.infra.repository.PagamentoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class PaymentCancellationPersistenceService {
    @Autowired OrderService orderService;
    @Autowired PagamentoRepository pagamentoRepository;

    @Transactional
    public Order markRequested(String orderId, String terminalId) {
        Order visible = orderService.getOrderForTerminal(orderId, terminalId);
        if (visible.getPagamento() == null) {
            throw new IllegalStateException("Order sem tentativa de pagamento");
        }
        PaymentAttempt attempt = pagamentoRepository.findByIdForUpdate(
                visible.getPagamento().getIdPagamento()).orElseThrow(
                () -> new NoSuchElementException("Tentativa de pagamento não existe"));
        Order order = attempt.getOrder();
        if (!orderId.equals(order.getIdOrder())) {
            throw new IllegalStateException("Tentativa pertence a outra Order");
        }
        if (attempt.getStatus().isApproved() || attempt.getStatus().allowsFinancialRetry()) {
            return order;
        }
        if (attempt.getCancellationRequestedAt() == null) {
            attempt.setCancellationRequestedAt(Instant.now());
            attempt.setCancellationIdempotencyKey("cancel-" + UUID.randomUUID());
        }
        attempt.setStatus(PagamentoStatus.CANCELLING);
        pagamentoRepository.saveAndFlush(attempt);
        return order;
    }
}

package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.appclient.LoyaltyAccount;
import com.jefiro.app247.domain.model.appclient.LoyaltyMovement;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.enum_type.order.OrderStatus;
import com.jefiro.app247.infra.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoyaltyRefundReversalTest {
    @Mock AppClienteConfigRepository configs;
    @Mock LoyaltyAccountRepository accounts;
    @Mock LoyaltyMovementRepository movements;
    @InjectMocks LoyaltyAccrualService service;

    @Test
    void estornaPontosExatamenteUmaVezEAceitaSaldoNegativo() {
        Empresa empresa = Empresa.builder().id("empresa-a").build();
        User user = User.builder().idUser("user-a").empresa(empresa).build();
        Order order = new Order();
        order.setIdOrder("order-a"); order.setEmpresa(empresa); order.setUser(user);
        order.setStatus(OrderStatus.REFUNDED);
        LoyaltyAccount account = new LoyaltyAccount();
        account.setEmpresa(empresa); account.setUser(user); account.setPointsBalance(2);
        LoyaltyMovement purchase = new LoyaltyMovement();
        purchase.setPointsDelta(10L);
        when(movements.findByIdempotencyKey("purchase:order-a")).thenReturn(Optional.of(purchase));
        when(accounts.findForUpdate("user-a", "empresa-a")).thenReturn(Optional.of(account));
        when(movements.existsByIdempotencyKey("refund:order-a")).thenReturn(false, true);

        service.reverseRefund(order);
        service.reverseRefund(order);

        assertThat(account.getPointsBalance()).isEqualTo(-8);
        ArgumentCaptor<LoyaltyMovement> saved = ArgumentCaptor.forClass(LoyaltyMovement.class);
        verify(movements, times(1)).save(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo("REFUND_REVERSAL");
        assertThat(saved.getValue().getPointsDelta()).isEqualTo(-10L);
        assertThat(saved.getValue().getIdempotencyKey()).isEqualTo("refund:order-a");
    }

    @Test
    void estornaCashbackComMesmaPrecisaoDoCreditoOriginal() {
        Empresa empresa = Empresa.builder().id("empresa-a").build();
        User user = User.builder().idUser("user-a").empresa(empresa).build();
        Order order = new Order();
        order.setIdOrder("order-a"); order.setEmpresa(empresa); order.setUser(user);
        order.setStatus(OrderStatus.REFUNDED);
        LoyaltyAccount account = new LoyaltyAccount();
        account.setEmpresa(empresa); account.setUser(user);
        account.setCashbackBalance(new BigDecimal("1.000000"));
        LoyaltyMovement purchase = new LoyaltyMovement();
        purchase.setCashbackDelta(new BigDecimal("3.250000"));
        when(movements.findByIdempotencyKey("purchase:order-a")).thenReturn(Optional.of(purchase));
        when(accounts.findForUpdate("user-a", "empresa-a")).thenReturn(Optional.of(account));
        when(movements.existsByIdempotencyKey("refund:order-a")).thenReturn(false);

        service.reverseRefund(order);

        assertThat(account.getCashbackBalance()).isEqualByComparingTo("-2.250000");
    }
}

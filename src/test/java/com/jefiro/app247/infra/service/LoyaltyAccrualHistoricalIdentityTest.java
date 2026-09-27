package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.CustomerIdentity;
import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.enum_type.order.OrderStatus;
import com.jefiro.app247.infra.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class LoyaltyAccrualHistoricalIdentityTest {
    @Mock AppClienteConfigRepository configs;
    @Mock LoyaltyAccountRepository accounts;
    @Mock LoyaltyMovementRepository movements;
    @InjectMocks LoyaltyAccrualService service;

    @Test void identityHistoricaClaimedNaoGeraBeneficioRetroativo() {
        Order order = new Order();
        order.setIdOrder("historica");
        order.setStatus(OrderStatus.PROCESSED);
        order.setCustomerIdentity(new CustomerIdentity());
        order.setUser(null);

        service.accrue(order);

        verifyNoInteractions(configs, accounts, movements);
    }
}

package com.jefiro.app247.infra.event;

import com.jefiro.app247.infra.service.LoyaltyAccrualService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class LoyaltyOrderListener {
    @Autowired private LoyaltyAccrualService service;
    @EventListener public void paid(OrderPaidEvent event){service.accrue(event.order());}
}

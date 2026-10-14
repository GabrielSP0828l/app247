package com.jefiro.app247.infra.event;

import com.jefiro.app247.infra.service.LoyaltyAccrualService;
import com.jefiro.app247.infra.service.AuditLogService;
import com.jefiro.app247.domain.model.enum_type.order.OrderStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class LoyaltyOrderListener {
    @Autowired private LoyaltyAccrualService service;
    @Autowired private AuditLogService audit;
    @EventListener public void paid(OrderPaidEvent event){service.accrue(event.order());}
    @EventListener public void refunded(OrderNotCompletedEvent event){
        if(!event.cancelamento() || event.order().getStatus()!=OrderStatus.REFUNDED)return;
        service.reverseRefund(event.order());
        audit.record(event.order().getEmpresa(),"PAYMENT_REFUND_CONFIRMED","Order",
                event.order().getIdOrder(),java.util.Map.of("status","PROCESSED"),
                java.util.Map.of("status","REFUNDED"),java.util.Map.of("fullRefund",true));
    }
}

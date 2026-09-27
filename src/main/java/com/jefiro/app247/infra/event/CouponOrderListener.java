package com.jefiro.app247.infra.event;

import com.jefiro.app247.infra.service.CouponLifecycleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class CouponOrderListener {
    @Autowired private CouponLifecycleService service;
    @EventListener public void paid(OrderPaidEvent event){service.use(event.order());}
    @EventListener public void notCompleted(OrderNotCompletedEvent event){service.release(event.order());}
    @EventListener public void cancelled(CompraCanceladaEvent event){service.release(event.getOrder());}
}

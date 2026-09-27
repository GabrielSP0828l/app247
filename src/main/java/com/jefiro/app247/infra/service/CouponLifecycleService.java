package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.appclient.CustomerCoupon;
import com.jefiro.app247.infra.repository.CustomerCouponRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Service
public class CouponLifecycleService {
    @Autowired private CustomerCouponRepository coupons;
    @Transactional public void use(Order order){
        coupons.findReservedForOrderUpdate(order.getIdOrder(),CustomerCoupon.CouponStatus.RESERVED)
                .forEach(c->{c.setStatus(CustomerCoupon.CouponStatus.USED);c.setReservedOrder(order);});
    }
    @Transactional public void release(Order order){
        coupons.findReservedForOrderUpdate(order.getIdOrder(),CustomerCoupon.CouponStatus.RESERVED)
                .forEach(c->{c.setReservedOrder(null);c.setStatus(c.getValidade().isAfter(Instant.now())?CustomerCoupon.CouponStatus.AVAILABLE:CustomerCoupon.CouponStatus.EXPIRED);});
    }
}

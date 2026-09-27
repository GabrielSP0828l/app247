package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.appclient.*;
import com.jefiro.app247.domain.model.appclient.AppClienteConfig.LoyaltyMode;
import com.jefiro.app247.domain.model.enum_type.order.OrderStatus;
import com.jefiro.app247.infra.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class LoyaltyAccrualService {
    @Autowired private AppClienteConfigRepository configs;
    @Autowired private LoyaltyAccountRepository accounts;
    @Autowired private LoyaltyMovementRepository movements;

    @Transactional
    public void accrue(Order order){
        if(order==null||order.getStatus()!=OrderStatus.PROCESSED||order.getUser()==null)return;
        String key="purchase:"+order.getIdOrder();if(movements.existsByIdempotencyKey(key))return;
        AppClienteConfig config=configs.findById(order.getEmpresa().getId())
                .orElseGet(()->AppClienteConfig.defaults(order.getEmpresa()));
        if(!config.isFidelidade()||config.getLoyaltyMode()==LoyaltyMode.NONE
                ||(config.getLoyaltyMode()==LoyaltyMode.POINTS&&!config.isPontos())
                ||(config.getLoyaltyMode()==LoyaltyMode.CASHBACK&&!config.isCashback()))return;
        LoyaltyAccount account=accounts.findForUpdate(order.getUser().getIdUser(),order.getEmpresa().getId()).orElseGet(()->{
            LoyaltyAccount a=new LoyaltyAccount();a.setEmpresa(order.getEmpresa());a.setUser(order.getUser());return accounts.saveAndFlush(a);
        });
        LoyaltyMovement movement=new LoyaltyMovement();movement.setEmpresa(order.getEmpresa());movement.setAccount(account);movement.setOrder(order);movement.setType("PURCHASE");movement.setDescription("Benefício da compra "+order.getIdOrder());movement.setIdempotencyKey(key);
        if(config.getLoyaltyMode()==LoyaltyMode.POINTS){
            long earned=order.getTotalCobrado().multiply(config.getPointsPerCurrency()).setScale(0,RoundingMode.FLOOR).longValue();
            account.setPointsBalance(account.getPointsBalance()+earned);movement.setPointsDelta(earned);
        }else{
            BigDecimal earned=MoneyPolicy.persistence(order.getTotalCobrado().multiply(config.getCashbackPercent()).movePointLeft(2));
            account.setCashbackBalance(MoneyPolicy.persistence(account.getCashbackBalance().add(earned)));movement.setCashbackDelta(earned);
        }
        movements.save(movement);
    }
}

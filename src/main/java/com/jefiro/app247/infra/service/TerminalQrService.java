package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.appclient.CustomerCoupon;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.dto.appclient.AppClienteContracts.*;
import com.jefiro.app247.infra.exception.ApiBusinessException;
import com.jefiro.app247.infra.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TerminalQrService {
    @Autowired private StringRedisTemplate redis;
    @Autowired private OrderRepository orders;
    @Autowired private UserRepository users;
    @Autowired private CustomerCouponRepository coupons;
    @Autowired private TerminalRequestAuthorizationService terminalAuthorization;
    @Autowired private OrderPriceFinalizationService priceFinalization;

    @Transactional
    public CustomerLinkConsumeResponse consumeCustomer(String credential,CustomerLinkConsumeRequest request){
        var terminal=terminalAuthorization.authorize(credential,request.terminalId());
        String value=consume("app:customer-token:"+request.token(),"CUSTOMER_TOKEN_INVALID");String[] p=parts(value);
        if(!terminal.getCondominio().getEmpresa().getId().equals(p[1])||!terminal.getCondominio().getIdCondominio().equals(p[2]))throw forbidden("TOKEN_CONTEXT_MISMATCH","Token não pertence a este terminal");
        User user=users.findById(p[0]).filter(u->u.getEmpresa().getId().equals(p[1])).orElseThrow(()->notFound("USER_NOT_FOUND","Usuário não encontrado"));
        Order order=orders.findForTerminalUpdate(request.orderId(),request.terminalId()).orElseThrow(()->notFound("PURCHASE_NOT_FOUND","Order não encontrada para o terminal"));
        if(!order.getEmpresa().getId().equals(p[1])||!order.getCondominio().getIdCondominio().equals(p[2]))throw forbidden("ORDER_CONTEXT_MISMATCH","Order não pertence ao contexto do token");
        if(order.getUser()!=null&&!order.getUser().getIdUser().equals(user.getIdUser()))throw new ApiBusinessException(HttpStatus.CONFLICT,"ORDER_ALREADY_LINKED","Order já vinculada a outro usuário");
        order.setUser(user);return new CustomerLinkConsumeResponse("LINKED",user.getIdUser(),order.getIdOrder());
    }

    @Transactional
    public CouponApplyResponse applyCoupon(String credential,CouponApplyRequest request){
        var terminal=terminalAuthorization.authorize(credential,request.terminalId());
        String value=consume("app:coupon-token:"+request.token(),"COUPON_TOKEN_INVALID");String[] p=parts(value);String empresaId=p[1];
        if(!terminal.getCondominio().getEmpresa().getId().equals(empresaId))throw forbidden("TOKEN_CONTEXT_MISMATCH","Token não pertence a este terminal");
        Order order=orders.findForTerminalUpdate(request.orderId(),request.terminalId()).orElseThrow(()->notFound("PURCHASE_NOT_FOUND","Order não encontrada para o terminal"));
        if(!order.getEmpresa().getId().equals(empresaId))throw forbidden("ORDER_CONTEXT_MISMATCH","Order pertence a outra empresa");
        CustomerCoupon coupon=coupons.findForUpdate(p[0],empresaId).orElseThrow(()->notFound("COUPON_NOT_FOUND","Cupom não encontrado"));
        var calculation=priceFinalization.reserveCoupon(order,coupon,java.time.Instant.now());
        return new CouponApplyResponse("RESERVED",coupon.getId(),
                MoneyPolicy.charged(calculation.discount()),MoneyPolicy.charged(calculation.total()));
    }

    private String consume(String key,String code){String value=redis.opsForValue().getAndDelete(key);if(value==null)throw new ApiBusinessException(HttpStatus.GONE,code,"Token inválido, expirado ou já utilizado");return value;}
    private static String[] parts(String value){String[] p=value.split("\\|",-1);if(p.length<2)throw new ApiBusinessException(HttpStatus.GONE,"TOKEN_INVALID","Token inválido");return p;}
    private static ApiBusinessException notFound(String c,String m){return new ApiBusinessException(HttpStatus.NOT_FOUND,c,m);} private static ApiBusinessException forbidden(String c,String m){return new ApiBusinessException(HttpStatus.FORBIDDEN,c,m);}
}

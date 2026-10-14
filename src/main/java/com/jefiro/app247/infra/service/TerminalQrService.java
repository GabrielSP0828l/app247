package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Carrinho;
import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.appclient.CustomerCoupon;
import com.jefiro.app247.domain.model.appclient.CustomerIdentificationToken;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.dto.appclient.AppClienteContracts.*;
import com.jefiro.app247.domain.model.enum_type.CarrinhoStatus;
import com.jefiro.app247.infra.exception.ApiBusinessException;
import com.jefiro.app247.infra.repository.*;
import com.jefiro.app247.infra.security.AuthenticatedTerminal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.regex.Pattern;

@Service
public class TerminalQrService {
    private static final String CUSTOMER_PREFIX="app247://customer-link/";
    private static final Pattern OPAQUE_TOKEN=Pattern.compile("[A-Za-z0-9_-]{43}");
    @Autowired private StringRedisTemplate redis;
    @Autowired private OrderRepository orders;
    @Autowired private UserRepository users;
    @Autowired private CustomerCouponRepository coupons;
    @Autowired private TerminalRequestAuthorizationService terminalAuthorization;
    @Autowired private OrderPriceFinalizationService priceFinalization;
    @Autowired private CarrinhoRepository carts;
    @Autowired private CustomerIdentificationTokenRepository identificationTokens;
    @Autowired private CustomerLinkRateLimiter rateLimiter;

    @Transactional
    public CustomerLinkConsumeResponse consumeCustomer(AuthenticatedTerminal terminal,
                                                        CustomerLinkConsumeRequest request){
        rateLimiter.check(terminal.terminalId());
        String opaque=normalizeCustomerToken(request.token());
        CustomerIdentificationToken token=identificationTokens.findByHashForUpdate(sha256(opaque))
                .orElseThrow(()->gone("CUSTOMER_LINK_TOKEN_INVALID","QR inválido"));
        Carrinho cart=carts.findByIdForUpdate(request.cartId())
                .orElseThrow(()->notFound("CART_NOT_FOUND","Carrinho não encontrado"));
        requireCartOwnership(terminal,cart);

        User user=token.getUser();
        if(token.getConsumedAt()!=null){
            if(token.getConsumedCart()!=null
                    && token.getConsumedCart().getIdCarrinho().equals(cart.getIdCarrinho())
                    && cart.getUser()!=null && cart.getUser().getIdUser().equals(user.getIdUser())){
                return response(user,cart);
            }
            throw gone("CUSTOMER_LINK_TOKEN_ALREADY_USED","QR já utilizado");
        }
        if(token.getInvalidatedAt()!=null)throw gone("CUSTOMER_LINK_TOKEN_INVALID","QR inválido");
        if(!token.getExpiresAt().isAfter(Instant.now()))
            throw gone("CUSTOMER_LINK_TOKEN_EXPIRED","QR expirado");
        if(user.getCondominio()==null)
            throw new ApiBusinessException(HttpStatus.CONFLICT,"CUSTOMER_NOT_LINKED_TO_CONDOMINIUM",
                    "Cliente não possui condomínio");
        if(!terminal.empresaId().equals(token.getEmpresa().getId())
                ||!terminal.condominioId().equals(token.getCondominio().getIdCondominio())
                ||user.getEmpresa()==null||!terminal.empresaId().equals(user.getEmpresa().getId())
                ||!terminal.condominioId().equals(user.getCondominio().getIdCondominio()))
            throw forbidden("CUSTOMER_LINK_TENANT_MISMATCH","QR não pode ser usado neste Terminal");
        if(cart.getStatus()!=CarrinhoStatus.OPEN||orders.findByCarrinhoIdCarrinho(cart.getIdCarrinho()).isPresent())
            throw new ApiBusinessException(HttpStatus.CONFLICT,"CUSTOMER_LINK_PAYMENT_ALREADY_STARTED",
                    "Pagamento já iniciado");
        if(cart.getUser()!=null&&!cart.getUser().getIdUser().equals(user.getIdUser()))
            throw new ApiBusinessException(HttpStatus.CONFLICT,"CUSTOMER_ALREADY_LINKED",
                    "Compra já vinculada a outro cliente");
        cart.setUser(user);
        token.setConsumedAt(Instant.now());
        token.setConsumedCart(cart);
        return response(user,cart);
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
    private static String normalizeCustomerToken(String raw){
        String value=raw==null?"":raw.strip();
        if(value.startsWith(CUSTOMER_PREFIX))value=value.substring(CUSTOMER_PREFIX.length());
        if(!OPAQUE_TOKEN.matcher(value).matches())throw gone("CUSTOMER_LINK_TOKEN_INVALID","QR inválido");
        return value;
    }
    private static String sha256(String value){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII)));}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException("SHA-256 indisponível",impossible);}
    }
    private static void requireCartOwnership(AuthenticatedTerminal terminal,Carrinho cart){
        if(cart.getTerminal()==null||cart.getEmpresa()==null||cart.getCondominio()==null
                ||!terminal.terminalId().equals(cart.getTerminal().getIdTerminal())
                ||!terminal.empresaId().equals(cart.getEmpresa().getId())
                ||!terminal.condominioId().equals(cart.getCondominio().getIdCondominio()))
            throw forbidden("CART_NOT_OWNED_BY_TERMINAL","Carrinho não pertence ao Terminal");
    }
    private static CustomerLinkConsumeResponse response(User user,Carrinho cart){
        return new CustomerLinkConsumeResponse(true,user.getNome(),cart.getIdCarrinho());
    }
    private static ApiBusinessException gone(String c,String m){return new ApiBusinessException(HttpStatus.GONE,c,m);}
    private static ApiBusinessException notFound(String c,String m){return new ApiBusinessException(HttpStatus.NOT_FOUND,c,m);} private static ApiBusinessException forbidden(String c,String m){return new ApiBusinessException(HttpStatus.FORBIDDEN,c,m);}
}

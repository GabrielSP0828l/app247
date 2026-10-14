package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.*;
import com.jefiro.app247.domain.model.appclient.CustomerIdentificationToken;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.dto.appclient.AppClienteContracts.CustomerLinkConsumeRequest;
import com.jefiro.app247.domain.model.enum_type.CarrinhoStatus;
import com.jefiro.app247.domain.model.terminal.Terminal;
import com.jefiro.app247.infra.exception.ApiBusinessException;
import com.jefiro.app247.infra.repository.*;
import com.jefiro.app247.infra.security.AuthenticatedTerminal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TerminalQrServiceTest {
    private static final String RAW="AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    @Mock org.springframework.data.redis.core.StringRedisTemplate redis;
    @Mock OrderRepository orders;
    @Mock UserRepository users;
    @Mock CustomerCouponRepository coupons;
    @Mock TerminalRequestAuthorizationService terminalAuthorization;
    @Mock OrderPriceFinalizationService priceFinalization;
    @Mock CarrinhoRepository carts;
    @Mock CustomerIdentificationTokenRepository tokens;
    @Mock CustomerLinkRateLimiter rateLimiter;
    @InjectMocks TerminalQrService service;

    Empresa empresa;
    Condominio condominio;
    User user;
    Carrinho cart;
    CustomerIdentificationToken token;
    AuthenticatedTerminal identity;

    @BeforeEach void setup() throws Exception {
        empresa=new Empresa();empresa.setId("empresa-a");
        condominio=new Condominio();condominio.setIdCondominio("cond-a");condominio.setEmpresa(empresa);
        user=new User();user.setIdUser("user-a");user.setNome("Jefferson");user.setEmpresa(empresa);user.setCondominio(condominio);
        Terminal terminal=new Terminal();terminal.setIdTerminal("terminal-a");terminal.setCondominio(condominio);
        cart=new Carrinho();cart.setIdCarrinho("cart-a");cart.setTerminal(terminal);cart.setStatus(CarrinhoStatus.OPEN);
        token=new CustomerIdentificationToken();token.setTokenHash(hash(RAW));token.setUser(user);token.setEmpresa(empresa);
        token.setCondominio(condominio);token.setExpiresAt(Instant.now().plusSeconds(120));
        identity=new AuthenticatedTerminal("terminal-a","empresa-a","cond-a",1);
        when(tokens.findByHashForUpdate(hash(RAW))).thenReturn(Optional.of(token));
        when(carts.findByIdForUpdate("cart-a")).thenReturn(Optional.of(cart));
        when(orders.findByCarrinhoIdCarrinho("cart-a")).thenReturn(Optional.empty());
    }

    @Test void validTokenLinksAuthoritativeCartWithoutIdentityInRequest(){
        var response=service.consumeCustomer(identity,new CustomerLinkConsumeRequest(RAW,"cart-a"));
        assertThat(response.linked()).isTrue();
        assertThat(response.displayName()).isEqualTo("Jefferson");
        assertThat(cart.getUser()).isSameAs(user);
        assertThat(token.getConsumedAt()).isNotNull();
        assertThat(token.getConsumedCart()).isSameAs(cart);
    }

    @Test void expiredTokenIsNotConsumed(){
        token.setExpiresAt(Instant.now().minusSeconds(1));
        assertCode("CUSTOMER_LINK_TOKEN_EXPIRED");
        assertThat(token.getConsumedAt()).isNull();
    }

    @Test void usedTokenOnAnotherCartFails(){
        Carrinho other=new Carrinho();other.setIdCarrinho("other");
        token.setConsumedAt(Instant.now());token.setConsumedCart(other);
        assertCode("CUSTOMER_LINK_TOKEN_ALREADY_USED");
    }

    @Test void retryOnSameCartAndSameCustomerIsIdempotent(){
        token.setConsumedAt(Instant.now());token.setConsumedCart(cart);cart.setUser(user);
        assertThat(service.consumeCustomer(identity,new CustomerLinkConsumeRequest(RAW,"cart-a")).linked()).isTrue();
    }

    @Test void differentTenantDoesNotConsumeToken(){
        identity=new AuthenticatedTerminal("terminal-a","empresa-b","cond-b",1);
        assertCode("CART_NOT_OWNED_BY_TERMINAL");
        assertThat(token.getConsumedAt()).isNull();
    }

    @Test void cartFromAnotherTerminalFailsIdor(){
        identity=new AuthenticatedTerminal("terminal-b","empresa-a","cond-a",1);
        assertCode("CART_NOT_OWNED_BY_TERMINAL");
    }

    @Test void closedCartDoesNotConsume(){
        cart.setStatus(CarrinhoStatus.READY_FOR_PAYMENT);
        assertCode("CUSTOMER_LINK_PAYMENT_ALREADY_STARTED");
        assertThat(token.getConsumedAt()).isNull();
    }

    @Test void existingOrderBlocksLinkEvenIfCartStatusIsStale(){
        when(orders.findByCarrinhoIdCarrinho("cart-a")).thenReturn(Optional.of(new Order()));
        assertCode("CUSTOMER_LINK_PAYMENT_ALREADY_STARTED");
    }

    @Test void anotherCustomerCannotBeSilentlyReplaced(){
        User another=new User();another.setIdUser("user-b");cart.setUser(another);
        assertCode("CUSTOMER_ALREADY_LINKED");
        assertThat(token.getConsumedAt()).isNull();
    }

    @Test void malformedEnvelopeIsRejectedBeforeLookup(){
        assertThatThrownBy(()->service.consumeCustomer(identity,
                new CustomerLinkConsumeRequest("app247://customer-link/partial","cart-a")))
                .isInstanceOfSatisfying(ApiBusinessException.class,
                        error->assertThat(error.getCode()).isEqualTo("CUSTOMER_LINK_TOKEN_INVALID"));
        verify(tokens,never()).findByHashForUpdate(anyString());
    }

    @Test void unknownWellFormedTokenIsInvalid(){
        when(tokens.findByHashForUpdate(hashUnchecked(RAW))).thenReturn(Optional.empty());
        assertCode("CUSTOMER_LINK_TOKEN_INVALID");
    }

    @Test void missingCartFailsWithoutConsuming(){
        when(carts.findByIdForUpdate("cart-a")).thenReturn(Optional.empty());
        assertCode("CART_NOT_FOUND");
        assertThat(token.getConsumedAt()).isNull();
    }

    @Test void customerWithoutCondominiumCannotBeLinked(){
        user.setCondominio(null);
        assertCode("CUSTOMER_NOT_LINKED_TO_CONDOMINIUM");
        assertThat(token.getConsumedAt()).isNull();
    }

    @Test void crossTenantQrFailsWithoutRevealingCustomer(){
        Empresa empresaB=new Empresa();empresaB.setId("empresa-b");
        Condominio condB=new Condominio();condB.setIdCondominio("cond-b");condB.setEmpresa(empresaB);
        Terminal terminalB=new Terminal();terminalB.setIdTerminal("terminal-b");terminalB.setCondominio(condB);
        cart.setTerminal(terminalB);
        identity=new AuthenticatedTerminal("terminal-b","empresa-b","cond-b",1);
        assertCode("CUSTOMER_LINK_TENANT_MISMATCH");
        assertThat(token.getConsumedAt()).isNull();
    }

    private void assertCode(String code){
        assertThatThrownBy(()->service.consumeCustomer(identity,new CustomerLinkConsumeRequest(RAW,"cart-a")))
                .isInstanceOfSatisfying(ApiBusinessException.class,
                        error->assertThat(error.getCode()).isEqualTo(code));
    }

    private static String hash(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.US_ASCII)));
    }
    private static String hashUnchecked(String value){try{return hash(value);}catch(Exception e){throw new IllegalStateException(e);}}
}

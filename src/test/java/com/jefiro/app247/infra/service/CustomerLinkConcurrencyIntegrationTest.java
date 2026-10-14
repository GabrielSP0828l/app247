package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.*;
import com.jefiro.app247.domain.model.appclient.CustomerIdentificationToken;
import com.jefiro.app247.domain.model.auth.RoleUser;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.dto.appclient.AppClienteContracts.CustomerLinkConsumeRequest;
import com.jefiro.app247.domain.model.enum_type.TerminalStatus;
import com.jefiro.app247.domain.model.terminal.Terminal;
import com.jefiro.app247.infra.exception.ApiBusinessException;
import com.jefiro.app247.infra.repository.*;
import com.jefiro.app247.infra.security.AuthenticatedTerminal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties="spring.task.scheduling.enabled=false")
@ActiveProfiles("test")
class CustomerLinkConcurrencyIntegrationTest {
    private static final String RAW="CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC";
    @Autowired TerminalQrService service;
    @Autowired TransactionTemplate transactions;
    @Autowired EmpresaRepository empresas;
    @Autowired CondominioRepository condominios;
    @Autowired UserRepository users;
    @Autowired TerminalRepository terminals;
    @Autowired CarrinhoRepository carts;
    @Autowired CustomerIdentificationTokenRepository tokens;

    @Test void sameTokenHasExactlyOneConsumerAcrossTwoCarts() throws Exception {
        Fixture fixture=transactions.execute(status->fixture());
        ExecutorService pool=Executors.newFixedThreadPool(2);
        CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);
        try{
            List<Future<String>> results=List.of(
                    pool.submit(()->consume(fixture.identity(),fixture.cartA(),ready,start)),
                    pool.submit(()->consume(fixture.identity(),fixture.cartB(),ready,start)));
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<String> outcomes=results.stream().map(f->{try{return f.get(10,TimeUnit.SECONDS);}catch(Exception e){throw new RuntimeException(e);}}).toList();
            assertThat(outcomes).containsExactlyInAnyOrder("LINKED","CUSTOMER_LINK_TOKEN_ALREADY_USED");
        }finally{pool.shutdownNow();}
    }

    private String consume(AuthenticatedTerminal identity,String cartId,
                           CountDownLatch ready,CountDownLatch start)throws Exception{
        ready.countDown();start.await(5,TimeUnit.SECONDS);
        try{service.consumeCustomer(identity,new CustomerLinkConsumeRequest(RAW,cartId));return "LINKED";}
        catch(ApiBusinessException error){return error.getCode();}
    }

    private Fixture fixture(){
        String suffix=java.util.UUID.randomUUID().toString().substring(0,8);
        Empresa empresa=new Empresa();empresa.setRazaoSocial("Empresa "+suffix);empresa.setNomeFantasia("E "+suffix);
        empresa.setCnpj("9"+System.nanoTime());empresa.setEmail(suffix+"@example.test");empresa=empresas.saveAndFlush(empresa);
        Condominio condominio=new Condominio();condominio.setNome("Condominio "+suffix);condominio.setEmpresa(empresa);
        condominio=condominios.saveAndFlush(condominio);
        User user=new User();user.setEmpresa(empresa);user.setCondominio(condominio);user.setNome("Cliente");
        user.setEmail("u"+suffix+"@example.test");user.setSenha("hash");user.setCpf(String.format("%011d",Math.abs(System.nanoTime()%100000000000L)));
        user.setRole(RoleUser.USER);user=users.saveAndFlush(user);
        Terminal terminal=new Terminal();terminal.setCondominio(condominio);terminal.setNome("Terminal");terminal.setCodigo("T-"+suffix);
        terminal.setStatus(TerminalStatus.ONLINE);terminal=terminals.saveAndFlush(terminal);
        Carrinho a=cart(terminal),b=cart(terminal);a=carts.saveAndFlush(a);b=carts.saveAndFlush(b);
        CustomerIdentificationToken token=new CustomerIdentificationToken();token.setTokenHash(hash(RAW));token.setUser(user);
        token.setEmpresa(empresa);token.setCondominio(condominio);token.setExpiresAt(Instant.now().plusSeconds(120));tokens.saveAndFlush(token);
        return new Fixture(new AuthenticatedTerminal(terminal.getIdTerminal(),empresa.getId(),condominio.getIdCondominio(),1),a.getIdCarrinho(),b.getIdCarrinho());
    }

    private Carrinho cart(Terminal terminal){Carrinho cart=new Carrinho();cart.setTerminal(terminal);cart.setSubtotal(BigDecimal.ZERO);return cart;}
    private static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII)));}catch(Exception e){throw new IllegalStateException(e);}}
    private record Fixture(AuthenticatedTerminal identity,String cartA,String cartB){}
}

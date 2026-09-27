package com.jefiro.app247.infra.controller;

import com.jefiro.app247.domain.model.dto.appclient.AppClienteContracts.*;
import com.jefiro.app247.infra.exception.ApiBusinessException;
import com.jefiro.app247.infra.service.TerminalQrService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.task.scheduling.enabled=false") @AutoConfigureMockMvc @ActiveProfiles("test")
class TerminalQrEndpointsIntegrationTest {
    @Autowired MockMvc mvc;
    @MockitoBean TerminalQrService service;
    @Test void terminalConsomeIdentificacaoComCredencialInterna() throws Exception{
        when(service.consumeCustomer(eq("secret"),any())).thenReturn(new CustomerLinkConsumeResponse("LINKED","u1","o1"));
        mvc.perform(post("/terminal/customer-link/consume").header("X-Terminal-Token","secret").contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"opaque\",\"terminalId\":\"t1\",\"orderId\":\"o1\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("LINKED"));
    }
    @Test void terminalAplicaCupomSemCalcularDesconto() throws Exception{
        when(service.applyCoupon(eq("secret"),any())).thenReturn(new CouponApplyResponse("RESERVED","c1",new BigDecimal("20.00"),new BigDecimal("80.00")));
        mvc.perform(post("/terminal/coupons/apply").header("X-Terminal-Token","secret").contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"opaque\",\"terminalId\":\"t1\",\"orderId\":\"o1\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.discount").value(20.00));
    }
    @Test void credencialInvalidaEhRecusada() throws Exception{
        when(service.consumeCustomer(isNull(),any())).thenThrow(new ApiBusinessException(HttpStatus.UNAUTHORIZED,"TERMINAL_AUTH_INVALID","Credencial inválida"));
        mvc.perform(post("/terminal/customer-link/consume").contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"opaque\",\"terminalId\":\"t1\",\"orderId\":\"o1\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("TERMINAL_AUTH_INVALID"));
    }
}

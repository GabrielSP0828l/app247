package com.jefiro.app247.infra.controller;

import com.jefiro.app247.domain.model.dto.appclient.AppClienteContracts.*;
import com.jefiro.app247.infra.service.TerminalQrService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.jefiro.app247.infra.security.AuthenticatedTerminal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/terminal")
public class TerminalQrController {
    @Autowired private TerminalQrService service;
    @PostMapping("/customer-link/consume")
    @Operation(summary="Vincula o cliente autenticado no App ao carrinho do Terminal",
            description="Consome atomicamente um QR opaco de uso único. O Terminal, Empresa e Condomínio são derivados exclusivamente da device credential.",
            security=@SecurityRequirement(name="terminalToken"))
    @ApiResponses({
            @ApiResponse(responseCode="200",description="Cliente vinculado ou retry idempotente no mesmo carrinho"),
            @ApiResponse(responseCode="401",description="TERMINAL_AUTH_INVALID"),
            @ApiResponse(responseCode="403",description="CUSTOMER_LINK_TENANT_MISMATCH ou carrinho de outro Terminal"),
            @ApiResponse(responseCode="409",description="CUSTOMER_ALREADY_LINKED ou CUSTOMER_LINK_PAYMENT_ALREADY_STARTED"),
            @ApiResponse(responseCode="410",description="CUSTOMER_LINK_TOKEN_INVALID, CUSTOMER_LINK_TOKEN_EXPIRED ou CUSTOMER_LINK_TOKEN_ALREADY_USED"),
            @ApiResponse(responseCode="429",description="CUSTOMER_LINK_RATE_LIMITED")
    })
    public CustomerLinkConsumeResponse consume(
            @AuthenticationPrincipal AuthenticatedTerminal terminal,
            @Valid @RequestBody CustomerLinkConsumeRequest request){
        return service.consumeCustomer(terminal,request);
    }
    @PostMapping("/coupons/apply") public CouponApplyResponse apply(@RequestHeader(value="X-Terminal-Token",required=false) String credential,@Valid @RequestBody CouponApplyRequest request){return service.applyCoupon(credential,request);}
}

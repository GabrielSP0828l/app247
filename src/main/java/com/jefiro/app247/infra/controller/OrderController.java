package com.jefiro.app247.infra.controller;

import com.jefiro.app247.domain.model.dto.OrderDetailResponse;
import com.jefiro.app247.domain.model.dto.PaymentStatusResponse;
import com.jefiro.app247.infra.service.OrderService;
import com.jefiro.app247.infra.service.PaymentReconciliationService;
import com.jefiro.app247.infra.service.TerminalRequestAuthorizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/order")
public class OrderController {

    @Autowired
    OrderService service;
    @Autowired
    PaymentReconciliationService reconciliationService;
    @Autowired
    TerminalRequestAuthorizationService terminalAuthorization;

    @GetMapping("/finalizar")
    @Operation(summary = "Finaliza o carrinho do Terminal autenticado",
            description = "Rota legada; o carrinho persistido determina Terminal, Empresa e Condomínio.",
            security = @SecurityRequirement(name = "terminalToken"))
    public ResponseEntity<?> order(@RequestParam String carrinho_id,
                                   @RequestHeader(value = "X-Terminal-Token", required = false) String credential) {
        var cart = service.getCartForAuthorization(carrinho_id);
        var terminal = terminalAuthorization.authorize(credential, cart.getIdTerminal());
        terminalAuthorization.requireCartOwnership(terminal, cart);
        return ResponseEntity.ok(new OrderDetailResponse(service.createOrder(carrinho_id, null)));
    }

    @GetMapping()
    @Operation(summary = "Consulta a Order do carrinho pertencente ao Terminal autenticado",
            security = @SecurityRequirement(name = "terminalToken"))
    public ResponseEntity<?> getOrder(@RequestParam String carrinho_id,
                                      @RequestHeader(value = "X-Terminal-Token", required = false) String credential) {
        var order = service.getOrder(carrinho_id);
        var terminal = terminalAuthorization.authorize(credential, order.getIdTerminal());
        terminalAuthorization.requireOrderOwnership(terminal, order);
        return ResponseEntity.ok(new OrderDetailResponse(order));
    }

    @GetMapping("/{orderId}/status")
    @Operation(summary = "Reconcilia o pagamento da Order para o Terminal autenticado",
            description = "A resposta inclui attemptStatus; UNKNOWN não autoriza retry financeiro.",
            security = @SecurityRequirement(name = "terminalToken"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado local/remoto, incluindo attemptStatus UNKNOWN"),
            @ApiResponse(responseCode = "401", description = "Credencial interna inválida"),
            @ApiResponse(responseCode = "404", description = "Order não pertence ao Terminal")
    })
    public ResponseEntity<PaymentStatusResponse> getStatus(
            @PathVariable String orderId,
            @RequestParam String terminalId,
            @RequestHeader(value = "X-Terminal-Token", required = false) String credential
    ) {
        terminalAuthorization.authorize(credential, terminalId);
        return ResponseEntity.ok(reconciliationService.reconcileForTerminal(orderId, terminalId));
    }


}

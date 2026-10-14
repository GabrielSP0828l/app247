package com.jefiro.app247.infra.controller;

import com.jefiro.app247.domain.model.Carrinho;
import com.jefiro.app247.domain.model.dto.CarrinhoRequest;
import com.jefiro.app247.domain.model.dto.response.CarrinhoResponseDTO;
import com.jefiro.app247.infra.service.CarrinhoService;
import com.jefiro.app247.infra.service.TerminalRequestAuthorizationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.jefiro.app247.infra.security.AuthenticatedTerminal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("carrinho")
public class CarrinhoController {
    @Autowired
    private CarrinhoService service;
    @Autowired
    private TerminalRequestAuthorizationService terminalAuthorization;

    @GetMapping("/{id}")
    @Operation(summary = "Consulta carrinho pertencente ao Terminal autenticado",
            security = @SecurityRequirement(name = "terminalToken"))
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Carrinho do Terminal"),
            @ApiResponse(responseCode = "401", description = "Credencial inválida"),
            @ApiResponse(responseCode = "403", description = "Carrinho de outro Terminal")})
    public ResponseEntity<?> getCarrinho(
            @PathVariable String id,
            @RequestHeader(value = "X-Terminal-Token", required = false) String credential) {
        Carrinho carrinho = service.getById(id);
        var terminal = terminalAuthorization.authorize(credential, carrinho.getIdTerminal());
        terminalAuthorization.requireCartOwnership(terminal, carrinho);
        return ResponseEntity.ok(new CarrinhoResponseDTO(carrinho));
    }

    @PostMapping
    @Operation(summary = "Cria carrinho com preços autoritativos do backend",
            description = "expectedUnitPrice serve apenas para detectar aumento e solicitar nova confirmação; não define o preço persistido.",
            security = @SecurityRequirement(name = "terminalToken"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Carrinho precificado e persistido"),
            @ApiResponse(responseCode = "401", description = "Credencial interna inválida"),
            @ApiResponse(responseCode = "403", description = "Terminal inativo ou não operacional"),
            @ApiResponse(responseCode = "409", description = "PRICE_CHANGED")
    })
    public ResponseEntity<?> addCarinho(
            @RequestHeader(value = "X-Terminal-Token", required = false) String credential,
            @RequestBody @Valid CarrinhoRequest request) {
        terminalAuthorization.authorize(credential, request.terminalId());
        return ResponseEntity.ok(new CarrinhoResponseDTO(service.save(request)));
    }

    @PostMapping("/empty")
    @Operation(summary = "Abre carrinho vazio para identificação opcional do cliente",
            description = "Não inicia pagamento e não cria Order; a identidade vem da device credential.",
            security = @SecurityRequirement(name = "terminalToken"))
    public ResponseEntity<?> abrirCarrinhoVazio(
            @AuthenticationPrincipal AuthenticatedTerminal terminal) {
        return ResponseEntity.ok(new CarrinhoResponseDTO(service.createEmpty(terminal)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza itens de carrinho aberto do Terminal autenticado",
            description = "Preserva o cliente previamente vinculado e revalida preços e ownership.",
            security = @SecurityRequirement(name = "terminalToken"))
    public ResponseEntity<?> atualizarCarrinho(
            @PathVariable String id,
            @RequestHeader(value = "X-Terminal-Token", required = false) String credential,
            @RequestBody @Valid CarrinhoRequest request) {
        var terminal = terminalAuthorization.authorize(credential, request.terminalId());
        Carrinho cart = service.getById(id);
        terminalAuthorization.requireCartOwnership(terminal, cart);
        return ResponseEntity.ok(new CarrinhoResponseDTO(service.replaceOpenCart(id, request)));
    }

}

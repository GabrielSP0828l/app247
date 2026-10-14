package com.jefiro.app247.infra.controller;

import com.jefiro.app247.domain.model.Carrinho;
import com.jefiro.app247.domain.model.dto.CheckoutSessionResponseDTO;
import com.jefiro.app247.infra.service.CarrinhoService;
import com.jefiro.app247.infra.service.CheckoutSessionService;
import com.jefiro.app247.infra.service.TerminalRequestAuthorizationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/checkout")
public class CheckoutSessionController {

    @Autowired
    private CheckoutSessionService service;
    @Autowired
    private CarrinhoService carrinhoService;
    @Autowired private TerminalRequestAuthorizationService terminalAuthorization;

    @GetMapping(value = "/carrinho")
    @Operation(summary = "Cria sessão para carrinho do Terminal autenticado",
            security = @SecurityRequirement(name = "terminalToken"))
    public ResponseEntity<CheckoutSessionResponseDTO> getQrCode(
            @RequestParam String idCarrinho,
            @RequestHeader(value = "X-Terminal-Token", required = false) String credential) {
        Carrinho carrinho = carrinhoService.getById(idCarrinho);
        var terminal = terminalAuthorization.authorize(credential, carrinho.getIdTerminal());
        terminalAuthorization.requireCartOwnership(terminal, carrinho);
        return ResponseEntity.ok(new CheckoutSessionResponseDTO(service.create(carrinho)));
    }

    @GetMapping(value = "/session")
    @Operation(summary = "Checkout móvel legado bloqueado", deprecated = true)
    public ResponseEntity<?> getCarrinho(@RequestParam String idSession) {
        return ResponseEntity.ok(service.getCarrinho(idSession));
    }

    @GetMapping(value = "/qrcode", produces = MediaType.IMAGE_PNG_VALUE)
    @Operation(summary = "Obtém QR da sessão pertencente ao Terminal autenticado",
            security = @SecurityRequirement(name = "terminalToken"))
    public ResponseEntity<byte[]> getQRCode(
            @RequestParam String id,
            @RequestHeader(value = "X-Terminal-Token", required = false) String credential) throws Exception {
        var session = service.getSession(id);
        terminalAuthorization.authorize(credential, session.getTerminalId());
        return ResponseEntity.ok(service.gerarQRCode(id));
    }

    @GetMapping()
    @Operation(summary = "Associação de usuário no checkout móvel bloqueada", deprecated = true)
    public ResponseEntity<?> setUser(@Valid @RequestParam @NotBlank String id, @Valid @RequestParam @NotBlank String session) {
        return ResponseEntity.ok(service.setUser(id, session));
    }

}

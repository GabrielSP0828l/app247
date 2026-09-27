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
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("carrinho")
public class CarrinhoController {
    @Autowired
    private CarrinhoService service;
    @Autowired
    private TerminalRequestAuthorizationService terminalAuthorization;

    @GetMapping("/{id}")
    public ResponseEntity<?> getCarrinho(@PathVariable String id) {
        return
                ResponseEntity.ok(new CarrinhoResponseDTO(service.getById(id)));
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

}

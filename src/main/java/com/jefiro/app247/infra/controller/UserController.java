package com.jefiro.app247.infra.controller;


import com.jefiro.app247.domain.model.dto.*;
import com.jefiro.app247.domain.model.dto.auth.ChangePasswordRequest;
import com.jefiro.app247.infra.service.UserService;
import com.jefiro.app247.infra.service.HumanRequestAuthorizationService;
import com.jefiro.app247.infra.service.PasswordRecoveryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("user")
public class UserController {
    @Autowired
    UserService service;
    @Autowired HumanRequestAuthorizationService authorization;
    @Autowired PasswordRecoveryService passwordRecoveryService;

    @PostMapping("/recuperar")
    @Operation(summary = "Solicita recuperação sem revelar se o CPF existe")
    @ApiResponses({@ApiResponse(responseCode = "202", description = "Solicitação aceita"),
            @ApiResponse(responseCode = "429", description = "PASSWORD_RESET_RATE_LIMITED")})
    public ResponseEntity<Map<String, String>> recuperar(@RequestBody @Valid PasswordRecovery request,
                                                          HttpServletRequest httpRequest) {
        passwordRecoveryService.request(request.cpf(), clientIp(httpRequest));

        return ResponseEntity.accepted().body(
                Map.of("message", "Se o CPF existir, enviamos instruções para recuperação.")
        );
    }

    @PostMapping("/validar")
    @Operation(summary = "Valida código de uso único e emite token de redefinição")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Token temporário emitido"),
            @ApiResponse(responseCode = "400", description = "PASSWORD_RESET_INVALID"),
            @ApiResponse(responseCode = "429", description = "PASSWORD_RESET_RATE_LIMITED")})
    public ResponseEntity<?> validar(@RequestBody @Valid ValidateCodeRequest request,
                                     HttpServletRequest httpRequest) {
        return ResponseEntity.ok(Map.of("token", passwordRecoveryService.validateCode(
                request.cpf(), request.code(), clientIp(httpRequest))));
    }

    @PostMapping("/redefinir-senha")
    @Operation(summary = "Consome token de uso único e redefine a senha")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Senha redefinida"),
            @ApiResponse(responseCode = "400", description = "PASSWORD_RESET_INVALID"),
            @ApiResponse(responseCode = "429", description = "PASSWORD_RESET_RATE_LIMITED")})
    public ResponseEntity<?> redefinirSenha(@RequestBody @Valid ResetPasswordRequest request,
                                             HttpServletRequest httpRequest) {
        passwordRecoveryService.resetPassword(request, clientIp(httpRequest));
        return ResponseEntity.ok(Map.of("message", "Senha redefinida com sucesso"));
    }

    @PostMapping("/alterar-senha")
    @Operation(summary = "Altera a própria senha", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<Map<String, String>> alterarSenha(
            @RequestBody ChangePasswordRequest request
    ) {
        var actor = authorization.requireSelf(request.userId());
        service.alterarSenha(actor, request);
        return ResponseEntity.ok(Map.of("message", "Senha alterada com sucesso"));
    }

    @GetMapping("/{userId}/orders")
    @Operation(summary = "Lista compras com validação de ownership e tenant",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<Page<OrderDTO>> getOrdersByUser(
            @PathVariable String userId,
            Pageable pageable
    ) {
        var target = authorization.requireSelfOrTenantManager(userId);
        return ResponseEntity.ok(service.getOrderByUser(target, pageable));
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Consulta usuário próprio ou do tenant administrado",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<?> getUser(@PathVariable String userId) {
        return ResponseEntity.ok(new UserResponseDTO(authorization.requireSelfOrTenantManager(userId)));
    }

    @PostMapping(value = "foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Atualiza a própria foto", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<?> salvar(@RequestPart(value = "file") MultipartFile file, @RequestParam String id) {
        return ResponseEntity.ok(service.salvarFoto(file, authorization.requireSelf(id)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza perfil com validação de ownership, papel e tenant",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<?> update(@RequestBody UserUpdate update, @PathVariable String id) {
        var actor = authorization.currentUser();
        var target = authorization.requireSelfOrTenantManager(id);
        service.atualizarUsuario(actor, target, update);
        return ResponseEntity.ok(Map.of("message", "Usuário atualizado com sucesso"));
    }

    private String clientIp(HttpServletRequest request) {
        // X-Forwarded-For só pode ser confiado após configuração explícita de proxy confiável.
        return request.getRemoteAddr();
    }
}

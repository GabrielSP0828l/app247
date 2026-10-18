package com.jefiro.app247.infra.controller;

import com.jefiro.app247.domain.model.dto.ValidateEmailRequest;
import com.jefiro.app247.domain.model.dto.auth.AuthDTO;
import com.jefiro.app247.domain.model.dto.auth.ClientRegistrationRequest;
import com.jefiro.app247.domain.model.dto.auth.ClientRegistrationResponse;
import com.jefiro.app247.infra.exception.ApiBusinessException;
import com.jefiro.app247.infra.service.RegistrationRateLimiter;
import com.jefiro.app247.infra.service.RegistrationService;
import com.jefiro.app247.infra.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("auth")
public class AuthController {
    private final UserService users;
    private final RegistrationService registrations;
    private final RegistrationRateLimiter rateLimiter;

    public AuthController(UserService users, RegistrationService registrations,
                          RegistrationRateLimiter rateLimiter) {
        this.users = users;
        this.registrations = registrations;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody @Valid AuthDTO auth) {
        return ResponseEntity.ok(users.login(auth));
    }

    @PostMapping("/login/admin")
    public ResponseEntity<?> loginAdmin(@RequestBody @Valid AuthDTO auth) {
        return ResponseEntity.ok(users.loginAdmin(auth));
    }

    @PostMapping("/register")
    @Operation(summary = "Cria uma conta do App 24/7 Cliente sem vínculo arbitrário de tenant")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Conta criada; CPF ainda não verificado"),
            @ApiResponse(responseCode = "400", description = "Dados ou CPF inválidos"),
            @ApiResponse(responseCode = "409", description = "CPF ou e-mail já cadastrado"),
            @ApiResponse(responseCode = "429", description = "Limite de tentativas excedido")
    })
    public ResponseEntity<ClientRegistrationResponse> create(
            @RequestBody @Valid ClientRegistrationRequest body, HttpServletRequest request) {
        if (!rateLimiter.allow(clientIp(request), body.email(), body.cpf())) {
            throw new ApiBusinessException(HttpStatus.TOO_MANY_REQUESTS, "REGISTRATION_RATE_LIMITED",
                    "Muitas tentativas de cadastro. Tente novamente mais tarde.");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(registrations.register(body));
    }

    @PostMapping("/send-code")
    public ResponseEntity<?> sendCode(
            @RequestBody @Validated(ValidateEmailRequest.SendCode.class) ValidateEmailRequest request) {
        users.sendCode(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/validate-code")
    public ResponseEntity<?> validateCode(
            @RequestBody @Validated(ValidateEmailRequest.VerifyCode.class) ValidateEmailRequest request) {
        users.verificarCode(request);
        return ResponseEntity.ok().build();
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank()
                ? request.getRemoteAddr()
                : forwarded.split(",", 2)[0].trim();
    }
}

package com.jefiro.app247.infra.controller;

import com.jefiro.app247.domain.model.dto.appclient.AdminAppClienteContracts.AppConfigRequest;
import com.jefiro.app247.domain.model.dto.appclient.AdminAppClienteContracts.AppConfigResponse;
import com.jefiro.app247.domain.model.dto.appclient.AdminAppClienteContracts.LoyaltyConfigRequest;
import com.jefiro.app247.domain.model.dto.appclient.AdminAppClienteContracts.LoyaltyConfigResponse;
import com.jefiro.app247.domain.model.dto.appclient.AdminAppClienteContracts.RewardAdminResponse;
import com.jefiro.app247.domain.model.dto.appclient.AdminAppClienteContracts.RewardPage;
import com.jefiro.app247.domain.model.dto.appclient.AdminAppClienteContracts.RewardRequest;
import com.jefiro.app247.infra.service.AdminAppClienteManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/app-cliente")
@Tag(name = "Gestão do App Cliente", description = "Configuração tenant-aware disponível para ADMIN e GERENTE")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "JWT ausente ou inválido"),
        @ApiResponse(responseCode = "403", description = "Role não autorizada ou tenant incompatível"),
        @ApiResponse(responseCode = "400", description = "Configuração inválida")
})
public class AdminAppClienteController {
    @Autowired private AdminAppClienteManagementService service;

    @GetMapping("/config")
    @Operation(summary = "Consulta a configuração do App Cliente da Empresa autenticada")
    public AppConfigResponse getConfig() {
        return service.getAppConfig();
    }

    @PutMapping("/config")
    @Operation(summary = "Atualiza a configuração do App Cliente da Empresa autenticada")
    public AppConfigResponse putConfig(@Valid @RequestBody AppConfigRequest request) {
        return service.putAppConfig(request);
    }

    @GetMapping("/fidelidade")
    @Operation(summary = "Consulta a configuração de fidelidade da Empresa autenticada")
    public LoyaltyConfigResponse getLoyalty() {
        return service.getLoyaltyConfig();
    }

    @PutMapping("/fidelidade")
    @Operation(summary = "Configura NONE, POINTS ou CASHBACK para a Empresa autenticada")
    public LoyaltyConfigResponse putLoyalty(@Valid @RequestBody LoyaltyConfigRequest request) {
        return service.putLoyaltyConfig(request);
    }

    @GetMapping("/recompensas")
    @Operation(summary = "Lista recompensas exclusivamente da Empresa autenticada")
    public RewardPage listRewards(@RequestParam(required = false) Boolean ativo,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size) {
        return service.listRewards(ativo, page, size);
    }

    @PostMapping("/recompensas")
    @Operation(summary = "Cria uma recompensa na Empresa autenticada")
    public ResponseEntity<RewardAdminResponse> createReward(@Valid @RequestBody RewardRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createReward(request));
    }

    @GetMapping("/recompensas/{id}")
    @Operation(summary = "Consulta uma recompensa da Empresa autenticada")
    @ApiResponse(responseCode = "404", description = "Recompensa inexistente ou pertencente a outro tenant")
    public RewardAdminResponse getReward(@PathVariable String id) {
        return service.getReward(id);
    }

    @PutMapping("/recompensas/{id}")
    @Operation(summary = "Atualiza uma recompensa da Empresa autenticada")
    @ApiResponse(responseCode = "404", description = "Recompensa inexistente ou pertencente a outro tenant")
    public RewardAdminResponse updateReward(@PathVariable String id,
                                            @Valid @RequestBody RewardRequest request) {
        return service.updateReward(id, request);
    }

    @DeleteMapping("/recompensas/{id}")
    @Operation(summary = "Desativa uma recompensa sem excluir seu histórico")
    @ApiResponse(responseCode = "404", description = "Recompensa inexistente ou pertencente a outro tenant")
    public ResponseEntity<Void> deleteReward(@PathVariable String id) {
        service.deactivateReward(id);
        return ResponseEntity.noContent().build();
    }
}

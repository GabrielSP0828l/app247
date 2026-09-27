package com.jefiro.app247.domain.model.dto.appclient;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class AdminAppClienteContracts {
    private AdminAppClienteContracts() {
    }

    public record AppConfigRequest(
            @NotNull Boolean catalogoAtivo,
            @NotNull Boolean promocoesAtivas,
            @NotNull Boolean fidelidadeAtiva,
            @NotNull Boolean qrIdentificacaoAtivo,
            @NotNull Boolean preferenciasAtivas,
            @NotNull Boolean suporteAtivo) {
    }

    public record AppConfigResponse(
            boolean catalogoAtivo,
            boolean promocoesAtivas,
            boolean fidelidadeAtiva,
            boolean qrIdentificacaoAtivo,
            boolean preferenciasAtivas,
            boolean suporteAtivo,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record LoyaltyConfigRequest(
            @NotNull Boolean ativo,
            @NotBlank @Size(max = 20) String modo,
            BigDecimal pontosPorReal,
            BigDecimal cashbackPercentual) {
    }

    public record LoyaltyConfigResponse(
            boolean ativo,
            String modo,
            BigDecimal pontosPorReal,
            BigDecimal cashbackPercentual,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record RewardRequest(
            @NotBlank @Size(max = 120) String nome,
            @Size(max = 1000) String descricao,
            @NotBlank @Size(max = 30) String tipoDesconto,
            @NotNull BigDecimal valorDesconto,
            @NotNull Long custoPontos,
            @NotNull BigDecimal valorMinimoCompra,
            BigDecimal descontoMaximo,
            @NotNull @Min(1) @Max(3650) Integer validadeDias,
            @Min(1) @Max(1_000_000) Integer limiteUsoPorUsuario,
            Boolean ativo) {
    }

    public record RewardAdminResponse(
            String id,
            String nome,
            String descricao,
            String tipoDesconto,
            BigDecimal valorDesconto,
            long custoPontos,
            BigDecimal valorMinimoCompra,
            BigDecimal descontoMaximo,
            int validadeDias,
            int limiteUsoPorUsuario,
            boolean ativo,
            Instant validade,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record RewardPage(
            List<RewardAdminResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }
}

package com.jefiro.app247.domain.model.dto.appclient;

import com.jefiro.app247.domain.model.enum_type.ProdutoCategoria;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class AppClienteContracts {
    private AppClienteContracts() {}
    public record UserSummary(String id,String nome,String cpf,String email){}
    public record CondominioSummary(String id,String nome){}
    public record Features(boolean catalogo,boolean promocoes,boolean fidelidade,boolean pontos,
        boolean cashback,boolean recompensas,boolean cupons,boolean qrIdentificacao,boolean preferencias,boolean suporte){}
    public record Bootstrap(UserSummary user,CondominioSummary condominio,Features features,String loyaltyMode,Instant serverTime){}
    public record Promotion(String id,String nome,String tipo,BigDecimal valor){}
    public record CatalogItem(String id,String nome,String descricao,String foto,ProdutoCategoria categoria,String unidade,
        BigDecimal precoOriginal,BigDecimal precoFinal,Promotion promocao){}
    public record PageResponse<T>(List<T> content,int page,int size,long totalElements,int totalPages){}
    public record PurchaseSummary(String id,String status,BigDecimal subtotal,BigDecimal desconto,BigDecimal total,Instant createdAt,Instant paidAt){}
    public record PurchaseItem(String id,String produtoId,String codigoInterno,String codigoBarras,String nome,String unidade,
        BigDecimal quantidade,BigDecimal precoOriginal,BigDecimal precoFinal,String tipoPromocao,BigDecimal valorPromocao,
        BigDecimal desconto,BigDecimal subtotal){}
    public record PurchaseDetail(String id,String status,BigDecimal subtotal,BigDecimal desconto,BigDecimal total,
        Instant createdAt,Instant paidAt,List<PurchaseItem> items){}
    public record ReceiptSendRequest(@NotBlank String canal,@NotBlank String destinatario){}
    public record Accepted(String status){}
    public record TokenResponse(String token,Instant expiresAt,Instant serverTime){}
    public record LoyaltyMovementResponse(String id,String type,Long pointsDelta,BigDecimal cashbackDelta,String description,Instant createdAt){}
    public record Loyalty(boolean enabled,String mode,Long pointsBalance,BigDecimal cashbackBalance,RewardResponse nextReward,List<LoyaltyMovementResponse> movements){}
    public record RewardResponse(String id,String nome,String tipoDesconto,BigDecimal valorDesconto,long custoPontos,
        BigDecimal valorMinimoCompra,BigDecimal descontoMaximo,Instant validade,boolean eligible,String unavailableReason){}
    public record RedemptionResponse(String id,String couponId,String status){}
    public record CouponResponse(String id,String nome,String tipo,BigDecimal valor,BigDecimal valorMinimoCompra,
        BigDecimal descontoMaximo,Instant validade,String status){}
    public record PreferenceRequest(@NotNull Boolean autoEmail,@NotNull Boolean autoWhatsapp){}
    public record PreferenceResponse(boolean autoEmail,boolean autoWhatsapp){}
    public record SupportRequest(@NotBlank @Size(max=50) String categoria,@NotBlank @Size(max=2000) String mensagem){}
    public record SupportResponse(String protocolo,String status){}
    public record CustomerLinkConsumeRequest(@NotBlank String token,@NotBlank String terminalId,@NotBlank String orderId){}
    public record CustomerLinkConsumeResponse(String status,String userId,String orderId){}
    public record CouponApplyRequest(@NotBlank String token,@NotBlank String terminalId,@NotBlank String orderId){}
    public record CouponApplyResponse(String status,String couponId,BigDecimal discount,BigDecimal total){}
    public record CouponMinimumError(String code,BigDecimal minimumAmount,BigDecimal currentAmount,BigDecimal missingAmount){}
}

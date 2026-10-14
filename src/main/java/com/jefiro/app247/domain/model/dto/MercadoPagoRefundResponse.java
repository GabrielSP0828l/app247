package com.jefiro.app247.domain.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MercadoPagoRefundResponse(
        String id,
        String status,
        @JsonProperty("status_detail") String statusDetail,
        Transactions transactions
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Transactions(List<Refund> refunds) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Refund(
            String id,
            @JsonProperty("transaction_id") String transactionId,
            String amount,
            String status
    ) {}

    public String firstRefundId() {
        return transactions != null && transactions.refunds() != null
                && !transactions.refunds().isEmpty()
                ? transactions.refunds().get(0).id() : null;
    }
}

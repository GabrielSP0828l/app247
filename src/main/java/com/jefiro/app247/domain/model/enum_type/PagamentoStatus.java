package com.jefiro.app247.domain.model.enum_type;

public enum PagamentoStatus {
    PROCESSED, CANCELED, EXPIRED, FAILED, REFUNDED,
    ACTION_REQUIRED, PENDING, UNKNOWN, CANCELLING;

    public boolean isActive() {
        return this == PENDING || this == ACTION_REQUIRED
                || this == UNKNOWN || this == CANCELLING;
    }

    public boolean allowsFinancialRetry() {
        return this == FAILED || this == CANCELED || this == EXPIRED;
    }

    public boolean isApproved() {
        return this == PROCESSED || this == REFUNDED;
    }
}

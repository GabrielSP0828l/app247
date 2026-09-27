package com.jefiro.app247.domain.model.appclient;

import java.util.Locale;

public enum RewardDiscountType {
    PERCENTUAL,
    VALOR_FIXO;

    public static RewardDiscountType fromContract(String value) {
        if (value == null) throw new IllegalArgumentException("Tipo de desconto é obrigatório");
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Tipo de desconto deve ser PERCENTUAL ou VALOR_FIXO");
        }
    }
}

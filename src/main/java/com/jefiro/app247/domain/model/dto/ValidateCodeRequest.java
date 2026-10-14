package com.jefiro.app247.domain.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ValidateCodeRequest(
        @NotBlank @Pattern(regexp = "\\d{6}", message = "Código deve conter 6 dígitos")
        String code,
        @Pattern(
                regexp = "\\d{11}",
                message = "CPF deve conter 11 dígitos numéricos"
        )
        @NotBlank
        String cpf,
        String email,
        String nome

) {
}

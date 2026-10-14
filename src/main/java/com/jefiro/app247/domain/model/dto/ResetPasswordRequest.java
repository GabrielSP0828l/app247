package com.jefiro.app247.domain.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank @Pattern(regexp = "\\d{11}")
        String cpf,
        @NotBlank
        String token,
        @NotBlank @Size(min = 8, max = 100)
        String novaSenha
) {
}

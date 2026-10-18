package com.jefiro.app247.domain.model.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Usuário é obrigatório")
        String userId,
        @NotBlank(message = "Senha atual é obrigatória")
        @Size(max = 72, message = "Senha atual inválida")
        String oldPassword,
        @NotBlank(message = "Nova senha é obrigatória")
        @Size(min = 8, max = 72, message = "A nova senha deve ter entre 8 e 72 caracteres")
        String newPassword
) {}

package com.jefiro.app247.domain.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Campos nulos não são alterados; campos presentes não podem ficar em branco. */
public record UserUpdate(
        @Size(max = 100, message = "Nome deve ter no máximo 100 caracteres")
        @Pattern(regexp = ".*\\S.*", message = "Nome não pode ficar em branco")
        String nome,
        @Size(max = 100, message = "Sobrenome deve ter no máximo 100 caracteres")
        String sobrenome,
        @Size(max = 180, message = "E-mail deve ter no máximo 180 caracteres")
        @Email(message = "E-mail inválido")
        @Pattern(regexp = ".*\\S.*", message = "E-mail não pode ficar em branco")
        String email,
        @Pattern(regexp = "[0-9()+\\-\\s]{10,20}", message = "Telefone inválido")
        String telefone,
        Boolean ativo,
        @Size(max = 36, message = "Condomínio inválido")
        String condominioId
) {
}

package com.jefiro.app247.domain.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Compartilhado por {@code /auth/send-code} ({@link SendCode}) e {@code /auth/validate-code} ({@link VerifyCode}). */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ValidateEmailRequest {
    public interface SendCode {}
    public interface VerifyCode {}

    @NotBlank(message = "E-mail é obrigatório", groups = {SendCode.class, VerifyCode.class})
    @Email(message = "E-mail inválido", groups = {SendCode.class, VerifyCode.class})
    @Size(max = 180, message = "E-mail deve ter no máximo 180 caracteres", groups = {SendCode.class, VerifyCode.class})
    String email;
    @NotBlank(message = "Nome é obrigatório", groups = SendCode.class)
    @Size(max = 100, message = "Nome deve ter no máximo 100 caracteres", groups = {SendCode.class, VerifyCode.class})
    String nome;
    @NotBlank(message = "Código é obrigatório", groups = VerifyCode.class)
    @Pattern(regexp = "\\d{6}", message = "Código deve conter 6 dígitos", groups = VerifyCode.class)
    String code;

}

package com.jefiro.app247.infra.service;

import com.jefiro.app247.infra.exception.ApiBusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class CpfService {
    public String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    public String requireValid(String value) {
        String cpf = normalize(value);
        if (!isValid(cpf)) {
            throw new ApiBusinessException(HttpStatus.BAD_REQUEST, "INVALID_CPF", "Confira o CPF informado.");
        }
        return cpf;
    }

    public boolean isValid(String cpf) {
        if (cpf == null || !cpf.matches("\\d{11}") || cpf.chars().distinct().count() == 1) return false;
        return digit(cpf, 9) == cpf.charAt(9) - '0' && digit(cpf, 10) == cpf.charAt(10) - '0';
    }

    private int digit(String cpf, int length) {
        int sum = 0;
        for (int i = 0; i < length; i++) sum += (cpf.charAt(i) - '0') * (length + 1 - i);
        int remainder = (sum * 10) % 11;
        return remainder == 10 ? 0 : remainder;
    }
}

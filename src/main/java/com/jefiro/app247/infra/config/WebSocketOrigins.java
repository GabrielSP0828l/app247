package com.jefiro.app247.infra.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class WebSocketOrigins {
    @Value("${app.websocket.allowed-origins:}") private String configured;

    public String[] values() {
        if (configured == null || configured.isBlank()) return new String[0];
        return Arrays.stream(configured.split(",")).map(String::trim)
                .filter(value -> !value.isBlank() && !"*".equals(value)).toArray(String[]::new);
    }
}

package com.jefiro.app247.infra.service;

import com.jefiro.app247.infra.repository.EmpresaRepository;
import com.jefiro.app247.infra.security.AuthenticatedTerminal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.Map;

/**
 * Sinaliza a mesma credencial de Terminal usada de origens diferentes em pouco tempo,
 * indício de credencial copiada do equipamento. Só observa: nunca bloqueia a requisição,
 * e falha de Redis ou de auditoria não afeta a operação do Terminal.
 */
@Service
public class TerminalCredentialAnomalyService {
    private static final Logger log = LoggerFactory.getLogger(TerminalCredentialAnomalyService.class);
    static final String LAST_ORIGIN_PREFIX = "app247:terminal-origin:last:";
    static final String ALERT_PREFIX = "app247:terminal-origin:alert:";
    static final String AUDIT_ACTION = "TERMINAL_CREDENTIAL_ORIGIN_CHANGED";

    @Autowired private StringRedisTemplate redis;
    @Autowired private AuditLogService auditLogService;
    @Autowired private EmpresaRepository empresas;
    @Autowired private PlatformTransactionManager transactionManager;
    @Value("${app.terminal.anomaly.enabled:true}") private boolean enabled;
    @Value("${app.terminal.anomaly.origin-window:PT10M}") private Duration originWindow;
    @Value("${app.terminal.anomaly.alert-cooldown:PT1H}") private Duration alertCooldown;

    public void observe(AuthenticatedTerminal terminal, String origin) {
        if (!enabled || terminal == null || origin == null || origin.isBlank()) return;
        String previous;
        try {
            String key = LAST_ORIGIN_PREFIX + terminal.terminalId();
            previous = redis.opsForValue().getAndSet(key, origin);
            redis.expire(key, originWindow);
            if (previous == null || previous.equals(origin)) return;
            Boolean firstAlert = redis.opsForValue().setIfAbsent(
                    ALERT_PREFIX + terminal.terminalId(), origin, alertCooldown);
            if (!Boolean.TRUE.equals(firstAlert)) return;
        } catch (RuntimeException unavailable) {
            log.debug("[TERMINAL-ANOMALY] Redis indisponível; observação ignorada");
            return;
        }

        log.warn("[TERMINAL-ANOMALY] credencial usada de nova origem: terminalId={}, credentialVersion={}, "
                        + "previousOrigin={}, currentOrigin={}",
                terminal.terminalId(), terminal.credentialVersion(), previous, origin);
        record(terminal, previous, origin);
    }

    private void record(AuthenticatedTerminal terminal, String previous, String origin) {
        try {
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            transaction.executeWithoutResult(status -> auditLogService.record(
                    empresas.getReferenceById(terminal.empresaId()), AUDIT_ACTION, "TERMINAL",
                    terminal.terminalId(), null, null, Map.of(
                            "previousOrigin", previous,
                            "currentOrigin", origin,
                            "credentialVersion", terminal.credentialVersion(),
                            "windowSeconds", originWindow.toSeconds())));
        } catch (RuntimeException failure) {
            log.warn("[TERMINAL-ANOMALY] não foi possível auditar: terminalId={}", terminal.terminalId(), failure);
        }
    }
}

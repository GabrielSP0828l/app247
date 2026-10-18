package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.infra.repository.EmpresaRepository;
import com.jefiro.app247.infra.security.AuthenticatedTerminal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TerminalCredentialAnomalyServiceTest {
    private static final String LAST = TerminalCredentialAnomalyService.LAST_ORIGIN_PREFIX + "terminal-a";
    private static final String ALERT = TerminalCredentialAnomalyService.ALERT_PREFIX + "terminal-a";

    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> values;
    @Mock AuditLogService auditLogService;
    @Mock EmpresaRepository empresas;
    @Mock PlatformTransactionManager transactionManager;
    @InjectMocks TerminalCredentialAnomalyService service;

    final AuthenticatedTerminal terminal = new AuthenticatedTerminal("terminal-a", "empresa-a", "cond-a", 3);
    final Empresa empresa = new Empresa();

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "originWindow", Duration.ofMinutes(10));
        ReflectionTestUtils.setField(service, "alertCooldown", Duration.ofHours(1));
        empresa.setId("empresa-a");
        when(redis.opsForValue()).thenReturn(values);
        when(empresas.getReferenceById("empresa-a")).thenReturn(empresa);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }

    @Test
    void primeiraOrigemApenasRegistraSemAlertar() {
        when(values.getAndSet(LAST, "10.0.0.1")).thenReturn(null);

        service.observe(terminal, "10.0.0.1");

        verify(redis).expire(LAST, Duration.ofMinutes(10));
        verify(values, never()).setIfAbsent(anyString(), anyString(), any(Duration.class));
        verifyNoInteractions(auditLogService);
    }

    @Test
    void mesmaOrigemNaoAlerta() {
        when(values.getAndSet(LAST, "10.0.0.1")).thenReturn("10.0.0.1");

        service.observe(terminal, "10.0.0.1");

        verifyNoInteractions(auditLogService);
    }

    @Test
    void origemDiferenteNaJanelaAuditaUmaVez() {
        when(values.getAndSet(LAST, "200.1.1.1")).thenReturn("10.0.0.1");
        when(values.setIfAbsent(ALERT, "200.1.1.1", Duration.ofHours(1))).thenReturn(true);

        service.observe(terminal, "200.1.1.1");

        verify(auditLogService).record(eq(empresa), eq(TerminalCredentialAnomalyService.AUDIT_ACTION),
                eq("TERMINAL"), eq("terminal-a"), isNull(), isNull(), argThat((Map<String, ?> metadata) ->
                        "10.0.0.1".equals(metadata.get("previousOrigin"))
                                && "200.1.1.1".equals(metadata.get("currentOrigin"))
                                && Integer.valueOf(3).equals(metadata.get("credentialVersion"))));
        verify(transactionManager).commit(any());
    }

    @Test
    void alertaEmCooldownNaoRepeteAuditoria() {
        when(values.getAndSet(LAST, "200.1.1.1")).thenReturn("10.0.0.1");
        when(values.setIfAbsent(ALERT, "200.1.1.1", Duration.ofHours(1))).thenReturn(false);

        service.observe(terminal, "200.1.1.1");

        verifyNoInteractions(auditLogService);
    }

    @Test
    void redisIndisponivelNaoAfetaRequisicao() {
        when(values.getAndSet(anyString(), anyString()))
                .thenThrow(new RedisConnectionFailureException("down"));

        assertThatCode(() -> service.observe(terminal, "200.1.1.1")).doesNotThrowAnyException();
        verifyNoInteractions(auditLogService);
    }

    @Test
    void falhaDeAuditoriaNaoAfetaRequisicao() {
        when(values.getAndSet(LAST, "200.1.1.1")).thenReturn("10.0.0.1");
        when(values.setIfAbsent(ALERT, "200.1.1.1", Duration.ofHours(1))).thenReturn(true);
        doThrow(new IllegalStateException("db down")).when(auditLogService)
                .record(any(), anyString(), anyString(), anyString(), any(), any(), any());

        assertThatCode(() -> service.observe(terminal, "200.1.1.1")).doesNotThrowAnyException();
    }

    @Test
    void desabilitadoNaoConsultaRedis() {
        ReflectionTestUtils.setField(service, "enabled", false);

        service.observe(terminal, "200.1.1.1");

        verifyNoInteractions(redis, auditLogService);
    }
}

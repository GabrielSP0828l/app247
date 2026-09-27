package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.appclient.AppClienteConfig;
import com.jefiro.app247.domain.model.appclient.AppClienteConfig.LoyaltyMode;
import com.jefiro.app247.domain.model.appclient.Reward;
import com.jefiro.app247.domain.model.appclient.RewardDiscountType;
import com.jefiro.app247.domain.model.dto.appclient.AdminAppClienteContracts.AppConfigRequest;
import com.jefiro.app247.domain.model.dto.appclient.AdminAppClienteContracts.AppConfigResponse;
import com.jefiro.app247.domain.model.dto.appclient.AdminAppClienteContracts.LoyaltyConfigRequest;
import com.jefiro.app247.domain.model.dto.appclient.AdminAppClienteContracts.LoyaltyConfigResponse;
import com.jefiro.app247.domain.model.dto.appclient.AdminAppClienteContracts.RewardAdminResponse;
import com.jefiro.app247.domain.model.dto.appclient.AdminAppClienteContracts.RewardPage;
import com.jefiro.app247.domain.model.dto.appclient.AdminAppClienteContracts.RewardRequest;
import com.jefiro.app247.infra.exception.ApiBusinessException;
import com.jefiro.app247.infra.repository.AppClienteConfigRepository;
import com.jefiro.app247.infra.repository.EmpresaRepository;
import com.jefiro.app247.infra.repository.RewardRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

@Service
public class AdminAppClienteManagementService {
    private static final long MAX_REWARD_POINTS = 1_000_000_000_000L;
    private static final BigDecimal MAX_POINTS_PER_REAL = new BigDecimal("1000000.000000");

    @Autowired private AppClienteConfigRepository configs;
    @Autowired private EmpresaRepository empresas;
    @Autowired private RewardRepository rewards;

    @Transactional(readOnly = true)
    public AppConfigResponse getAppConfig() {
        return appConfigResponse(findOrDefaults(currentEmpresa()));
    }

    @Transactional
    public AppConfigResponse putAppConfig(AppConfigRequest request) {
        Empresa empresa = lockedEmpresa();
        AppClienteConfig config = findOrDefaults(empresa);
        if (request.fidelidadeAtiva() && config.getLoyaltyMode() == LoyaltyMode.NONE) {
            throw invalidLoyalty("Configure POINTS ou CASHBACK antes de ativar a fidelidade");
        }
        config.setCatalogo(request.catalogoAtivo());
        config.setPromocoes(request.promocoesAtivas());
        config.setFidelidade(request.fidelidadeAtiva());
        config.setQrIdentificacao(request.qrIdentificacaoAtivo());
        config.setPreferencias(request.preferenciasAtivas());
        config.setSuporte(request.suporteAtivo());
        config.synchronizeDerivedFlags();
        return appConfigResponse(configs.saveAndFlush(config));
    }

    @Transactional(readOnly = true)
    public LoyaltyConfigResponse getLoyaltyConfig() {
        return loyaltyConfigResponse(findOrDefaults(currentEmpresa()));
    }

    @Transactional
    public LoyaltyConfigResponse putLoyaltyConfig(LoyaltyConfigRequest request) {
        Empresa empresa = lockedEmpresa();
        AppClienteConfig config = findOrDefaults(empresa);
        LoyaltyMode mode = parseLoyaltyMode(request.modo());
        validateLoyalty(request.ativo(), mode, request.pontosPorReal(), request.cashbackPercentual());
        config.setFidelidade(request.ativo());
        config.setLoyaltyMode(mode);
        config.setPointsPerCurrency(mode == LoyaltyMode.POINTS ? request.pontosPorReal() : null);
        config.setCashbackPercent(mode == LoyaltyMode.CASHBACK ? request.cashbackPercentual() : null);
        config.synchronizeDerivedFlags();
        return loyaltyConfigResponse(configs.saveAndFlush(config));
    }

    @Transactional(readOnly = true)
    public RewardPage listRewards(Boolean ativo, int page, int size) {
        validatePage(page, size);
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Reward> result = ativo == null
                ? rewards.findByEmpresaId(EmpresaContext.require(), pageable)
                : rewards.findByEmpresaIdAndAtivo(EmpresaContext.require(), ativo, pageable);
        return new RewardPage(result.getContent().stream().map(this::rewardResponse).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public RewardAdminResponse getReward(String id) {
        return rewardResponse(ownedReward(id));
    }

    @Transactional
    public RewardAdminResponse createReward(RewardRequest request) {
        validateReward(request);
        Reward reward = new Reward();
        reward.setEmpresa(lockedEmpresa());
        applyReward(reward, request);
        return rewardResponse(rewards.saveAndFlush(reward));
    }

    @Transactional
    public RewardAdminResponse updateReward(String id, RewardRequest request) {
        validateReward(request);
        lockedEmpresa();
        Reward reward = ownedReward(id);
        applyReward(reward, request);
        return rewardResponse(rewards.saveAndFlush(reward));
    }

    @Transactional
    public void deactivateReward(String id) {
        lockedEmpresa();
        Reward reward = ownedReward(id);
        reward.setAtivo(false);
        rewards.saveAndFlush(reward);
    }

    private AppClienteConfig findOrDefaults(Empresa empresa) {
        return configs.findById(empresa.getId()).orElseGet(() -> AppClienteConfig.defaults(empresa));
    }

    private Empresa currentEmpresa() {
        return empresas.findById(EmpresaContext.require()).orElseThrow(() ->
                new ApiBusinessException(HttpStatus.NOT_FOUND, "COMPANY_NOT_FOUND", "Empresa autenticada não encontrada"));
    }

    private Empresa lockedEmpresa() {
        return empresas.findByIdForUpdate(EmpresaContext.require()).orElseThrow(() ->
                new ApiBusinessException(HttpStatus.NOT_FOUND, "COMPANY_NOT_FOUND", "Empresa autenticada não encontrada"));
    }

    private Reward ownedReward(String id) {
        return rewards.findByIdAndEmpresaId(id, EmpresaContext.require()).orElseThrow(() ->
                new ApiBusinessException(HttpStatus.NOT_FOUND, "REWARD_NOT_FOUND", "Recompensa não encontrada"));
    }

    private void applyReward(Reward reward, RewardRequest request) {
        reward.setNome(request.nome().strip());
        reward.setDescricao(request.descricao() == null || request.descricao().isBlank()
                ? null : request.descricao().strip());
        reward.setTipoDesconto(parseDiscountType(request.tipoDesconto()));
        reward.setValorDesconto(request.valorDesconto());
        reward.setCustoPontos(request.custoPontos());
        reward.setValorMinimoCompra(request.valorMinimoCompra());
        reward.setDescontoMaximo(request.descontoMaximo());
        reward.setValidadeDias(request.validadeDias());
        reward.setLimiteUsoPorUsuario(request.limiteUsoPorUsuario() == null ? 1 : request.limiteUsoPorUsuario());
        reward.setValidade(Instant.now().plus(request.validadeDias(), ChronoUnit.DAYS));
        reward.setAtivo(request.ativo() == null || request.ativo());
    }

    private void validateLoyalty(boolean active, LoyaltyMode mode, BigDecimal points, BigDecimal cashback) {
        if (mode == LoyaltyMode.NONE) {
            if (active || points != null || cashback != null) {
                throw invalidLoyalty("NONE exige fidelidade inativa e valores nulos");
            }
            return;
        }
        if (mode == LoyaltyMode.POINTS) {
            if (points == null || points.signum() <= 0 || points.compareTo(MAX_POINTS_PER_REAL) > 0
                    || cashback != null || !fitsDecimal(points, 15, 6)) {
                throw invalidLoyalty("POINTS exige pontosPorReal positivo, compatível com DECIMAL(15,6), e cashbackPercentual nulo");
            }
            return;
        }
        if (cashback == null || cashback.signum() <= 0 || cashback.compareTo(new BigDecimal("100")) > 0
                || points != null || !fitsDecimal(cashback, 9, 6)) {
            throw invalidLoyalty("CASHBACK exige percentual entre 0 e 100, compatível com DECIMAL(9,6), e pontosPorReal nulo");
        }
    }

    private void validateReward(RewardRequest request) {
        RewardDiscountType type = parseDiscountType(request.tipoDesconto());
        BigDecimal value = request.valorDesconto();
        BigDecimal minimum = request.valorMinimoCompra();
        BigDecimal maximum = request.descontoMaximo();
        if (request.nome() == null || request.nome().isBlank()
                || request.custoPontos() == null || request.custoPontos() <= 0 || request.custoPontos() > MAX_REWARD_POINTS
                || request.validadeDias() == null || request.validadeDias() < 1 || request.validadeDias() > 3650
                || minimum == null || minimum.signum() < 0 || !fitsDecimal(minimum, 15, 6)
                || value == null || value.signum() <= 0 || !fitsDecimal(value, 15, 6)
                || maximum != null && (maximum.signum() <= 0 || !fitsDecimal(maximum, 15, 6))) {
            throw invalidReward("Campos financeiros, custo ou validade da recompensa são inválidos");
        }
        int perUser = request.limiteUsoPorUsuario() == null ? 1 : request.limiteUsoPorUsuario();
        if (perUser < 1 || perUser > 1_000_000) {
            throw invalidReward("limiteUsoPorUsuario deve estar entre 1 e 1000000");
        }
        if (type == RewardDiscountType.PERCENTUAL && value.compareTo(new BigDecimal("100")) > 0) {
            throw invalidReward("Desconto percentual deve ser maior que zero e menor ou igual a 100");
        }
    }

    private static LoyaltyMode parseLoyaltyMode(String value) {
        try {
            return LoyaltyMode.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new ApiBusinessException(HttpStatus.BAD_REQUEST, "INVALID_LOYALTY_MODE", "Modo de fidelidade inválido");
        }
    }

    private static RewardDiscountType parseDiscountType(String value) {
        try {
            return RewardDiscountType.fromContract(value);
        } catch (RuntimeException exception) {
            throw invalidReward("tipoDesconto deve ser PERCENTUAL ou VALOR_FIXO");
        }
    }

    private static boolean fitsDecimal(BigDecimal value, int precision, int scale) {
        BigDecimal normalized = value.stripTrailingZeros();
        int actualScale = Math.max(normalized.scale(), 0);
        int integerDigits = Math.max(normalized.precision() - normalized.scale(), 0);
        return actualScale <= scale && integerDigits <= precision - scale;
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiBusinessException(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION", "size deve estar entre 1 e 100");
        }
    }

    private AppConfigResponse appConfigResponse(AppClienteConfig config) {
        return new AppConfigResponse(config.isCatalogo(), config.isPromocoes(), config.isFidelidade(),
                config.isQrIdentificacao(), config.isPreferencias(), config.isSuporte(),
                config.getCreatedAt(), config.getUpdatedAt());
    }

    private LoyaltyConfigResponse loyaltyConfigResponse(AppClienteConfig config) {
        return new LoyaltyConfigResponse(config.isFidelidade(), config.getLoyaltyMode().name(),
                config.getPointsPerCurrency(), config.getCashbackPercent(),
                config.getCreatedAt(), config.getUpdatedAt());
    }

    private RewardAdminResponse rewardResponse(Reward reward) {
        return new RewardAdminResponse(reward.getId(), reward.getNome(), reward.getDescricao(),
                reward.getTipoDesconto().name(), MoneyPolicy.charged(reward.getValorDesconto()),
                reward.getCustoPontos(), MoneyPolicy.charged(reward.getValorMinimoCompra()),
                reward.getDescontoMaximo() == null ? null : MoneyPolicy.charged(reward.getDescontoMaximo()),
                reward.getValidadeDias(), reward.getLimiteUsoPorUsuario(), reward.isAtivo(), reward.getValidade(),
                reward.getCreatedAt(), reward.getUpdatedAt());
    }

    private static ApiBusinessException invalidLoyalty(String message) {
        return new ApiBusinessException(HttpStatus.BAD_REQUEST, "INVALID_LOYALTY_CONFIG", message);
    }

    private static ApiBusinessException invalidReward(String message) {
        return new ApiBusinessException(HttpStatus.BAD_REQUEST, "INVALID_REWARD", message);
    }
}

package com.jefiro.app247.domain.model;

import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.appclient.CustomerCoupon;
import com.jefiro.app247.domain.model.enum_type.OriginRequest;
import com.jefiro.app247.domain.model.enum_type.order.OrderStatus;
import com.jefiro.app247.domain.model.enum_type.order.StatusDetail;
import com.jefiro.app247.domain.model.terminal.Terminal;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "char(36)", length = 36, nullable = false)
    private String idOrder;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "condominio_id", nullable = false)
    private Condominio condominio;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "terminal_id", nullable = false)
    private Terminal terminal;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "carrinho_id", nullable = false, unique = true)
    private Carrinho carrinho;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_identity_id")
    private CustomerIdentity customerIdentity;
    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    @OrderBy("createdAt ASC")
    private List<OrderItem> items = new ArrayList<>();
    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
    @OrderBy("attemptNumber ASC")
    private List<PaymentAttempt> paymentAttempts = new ArrayList<>();
    @Column(nullable = false, precision = 15, scale = 6)
    private BigDecimal subtotal;
    @Column(name = "subtotal_pos_promocoes", nullable = false, precision = 15, scale = 6)
    private BigDecimal subtotalPosPromocoes;
    @Column(name = "desconto_promocoes", nullable = false, precision = 15, scale = 6)
    private BigDecimal descontoPromocoes;
    @Column(name = "desconto_cupom", nullable = false, precision = 15, scale = 6)
    private BigDecimal descontoCupom;
    @Column(nullable = false, precision = 15, scale = 6)
    private BigDecimal desconto;
    @Column(name = "total_calculado", nullable = false, precision = 15, scale = 6)
    private BigDecimal totalCalculado;
    @Column(name = "total_cobrado", nullable = false, precision = 15, scale = 6)
    private BigDecimal totalCobrado;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id")
    private CustomerCoupon coupon;
    @Column(name = "price_finalized_at")
    private Instant priceFinalizedAt;
    @Enumerated(EnumType.STRING)
    @Column(name = "origin_request", nullable = false, length = 30)
    private OriginRequest originRequest;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status = OrderStatus.PENDING;
    @Version
    @Column(nullable = false)
    private Long version;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "paid_at")
    private Instant paidAt;
    @Transient
    private String detachedTerminalId;

    public Order(Carrinho carrinho) {
        this.carrinho = carrinho;
        empresa = carrinho.getEmpresa();
        condominio = carrinho.getCondominio();
        terminal = carrinho.getTerminal();
        user = carrinho.getUser();
        customerIdentity = carrinho.getCustomerIdentity();
        originRequest = OriginRequest.TERMINAL;
        snapshotItens();
    }

    public Order(Carrinho carrinho, User user) {
        this(carrinho);
        this.user = user;
    }

    public void snapshotItens() {
        if (carrinho == null || carrinho.getItems() == null || !items.isEmpty()) return;
        for (CartItem item : carrinho.getItems()) items.add(OrderItem.snapshot(this, item));
    }

    public BigDecimal getTotal() {
        return totalCobrado;
    }

    public void setTotal(BigDecimal total) {
        this.totalCobrado = total;
    }

    public String getIdTerminal() {
        return terminal != null ? terminal.getIdTerminal() : detachedTerminalId;
    }

    public void setIdTerminal(String value) {
        detachedTerminalId = value;
    }

    public PaymentAttempt getPagamento() {
        return paymentAttempts == null ? null : paymentAttempts.stream().max(Comparator.comparing(PaymentAttempt::getAttemptNumber, Comparator.nullsFirst(Integer::compareTo))).orElse(null);
    }

    public boolean isPriceFinalized() {
        return priceFinalizedAt != null;
    }

    public void setPagamento(PaymentAttempt attempt) {
        if (attempt == null) return;
        if (paymentAttempts == null) paymentAttempts = new ArrayList<>();
        attempt.setOrder(this);
        if (attempt.getEmpresa() == null) attempt.setEmpresa(empresa);
        if (!paymentAttempts.contains(attempt)) paymentAttempts.add(attempt);
    }

    public String getMpOrderId() {
        PaymentAttempt p = getPagamento();
        return p != null ? p.getProviderOrderId() : null;
    }

    public void setMpOrderId(String value) {
        requireAttempt().setProviderOrderId(value);
    }

    public String getMpType() {
        return "point";
    }

    public void setMpType(String ignored) {
    }

    public String getMpUserId() {
        PaymentAttempt p = getPagamento();
        return p != null ? p.getProviderUserId() : null;
    }

    public void setMpUserId(String value) {
        requireAttempt().setProviderUserId(value);
    }

    public OrderStatus getMpStatus() {
        return status;
    }

    public void setMpStatus(OrderStatus value) {
        status = value;
    }

    public StatusDetail getMpStatusDetail() {
        PaymentAttempt p = getPagamento();
        return p == null ? null : StatusDetail.findByValue(p.getStatusDetail());
    }

    public void setMpStatusDetail(StatusDetail value) {
        requireAttempt().setStatusDetail(value != null ? value.getValue() : null);
    }

    public String getMpTerminalId() {
        PaymentAttempt p = getPagamento();
        return p != null ? p.getProviderTerminalId() : null;
    }

    public void setMpTerminalId(String value) {
        requireAttempt().setProviderTerminalId(value);
    }

    public Integer getMpEventVersion() {
        PaymentAttempt p = getPagamento();
        return p != null ? p.getProviderEventVersion() : null;
    }

    public void setMpEventVersion(Integer value) {
        requireAttempt().setProviderEventVersion(value);
    }

    public Instant getMpEventDate() {
        PaymentAttempt p = getPagamento();
        return p == null ? null : p.getProviderEventAt();
    }

    public void setMpEventDate(Instant value) {
        requireAttempt().setProviderEventAt(value);
    }

    private PaymentAttempt requireAttempt() {
        PaymentAttempt p = getPagamento();
        if (p == null) throw new IllegalStateException("Order sem tentativa de pagamento");
        return p;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (status == null) status = OrderStatus.PENDING;
        initializeLegacyFinancialSnapshot();
        snapshotItens();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Compatibilidade de persistência para compras históricas criadas antes do
     * snapshot P1-C. Elas continuam sem {@code priceFinalizedAt} e, portanto,
     * jamais ficam aptas a uma nova cobrança sem passar pelo finalizador.
     */
    private void initializeLegacyFinancialSnapshot() {
        if (subtotalPosPromocoes != null && descontoPromocoes != null
                && descontoCupom != null) return;
        if (subtotal == null || totalCalculado == null || totalCobrado == null) return;
        subtotal = com.jefiro.app247.infra.service.MoneyPolicy.persistence(subtotal);
        totalCalculado = com.jefiro.app247.infra.service.MoneyPolicy.persistence(totalCalculado);
        totalCobrado = com.jefiro.app247.infra.service.MoneyPolicy.chargedForPersistence(totalCalculado);
        subtotalPosPromocoes = totalCalculado;
        descontoPromocoes = com.jefiro.app247.infra.service.MoneyPolicy.persistence(
                subtotal.subtract(subtotalPosPromocoes));
        descontoCupom = com.jefiro.app247.infra.service.MoneyPolicy.persistence(BigDecimal.ZERO);
        desconto = com.jefiro.app247.infra.service.MoneyPolicy.persistence(descontoPromocoes);
    }
}

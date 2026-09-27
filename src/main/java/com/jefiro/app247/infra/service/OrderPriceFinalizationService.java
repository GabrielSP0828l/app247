package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.CartItem;
import com.jefiro.app247.domain.model.Carrinho;
import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.PaymentAttempt;
import com.jefiro.app247.domain.model.appclient.CustomerCoupon;
import com.jefiro.app247.domain.model.appclient.RewardDiscountType;
import com.jefiro.app247.domain.model.enum_type.CarrinhoStatus;
import com.jefiro.app247.domain.model.enum_type.PagamentoStatus;
import com.jefiro.app247.domain.model.enum_type.order.OrderStatus;
import com.jefiro.app247.infra.exception.ApiBusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;

/** Única pipeline de composição e congelamento do preço financeiro da Order. */
@Service
public class OrderPriceFinalizationService {
    private static final BigDecimal ZERO = MoneyPolicy.persistence(BigDecimal.ZERO);

    public void initializeSnapshot(Order order, Carrinho cart) {
        if (order == null || cart == null || order.isPriceFinalized()
                || order.getPaymentAttempts() == null || !order.getPaymentAttempts().isEmpty()) {
            throw priceNotFinalized("Order não pode mais receber um novo snapshot financeiro");
        }
        BigDecimal original = BigDecimal.ZERO;
        BigDecimal afterPromotions = BigDecimal.ZERO;
        for (CartItem item : cart.getItems()) {
            original = original.add(item.getOriginalPrice().multiply(item.getQuantity()));
            afterPromotions = afterPromotions.add(item.getCalculatedSubtotal());
        }
        original = MoneyPolicy.persistence(original);
        afterPromotions = MoneyPolicy.persistence(afterPromotions);
        if (original.signum() <= 0 || afterPromotions.signum() <= 0
                || afterPromotions.compareTo(original) > 0) {
            throw priceNotFinalized("Snapshot promocional da Order é inválido");
        }
        order.setSubtotal(original);
        order.setSubtotalPosPromocoes(afterPromotions);
        order.setDescontoPromocoes(MoneyPolicy.persistence(original.subtract(afterPromotions)));
        order.setDescontoCupom(ZERO);
        order.setDesconto(order.getDescontoPromocoes());
        order.setTotalCalculado(afterPromotions);
        order.setTotalCobrado(MoneyPolicy.chargedForPersistence(afterPromotions));
        order.setCoupon(null);
    }

    public CouponCalculation reserveCoupon(Order order, CustomerCoupon coupon, Instant now) {
        requireEditable(order);
        if (order.getCoupon() != null) {
            throw conflict("ORDER_ALREADY_HAS_COUPON", "A Order já possui um cupom");
        }
        validateCouponTenantAndOwner(order, coupon);
        requireCouponAvailable(coupon, now);

        BigDecimal base = MoneyPolicy.persistence(order.getSubtotalPosPromocoes());
        BigDecimal minimum = MoneyPolicy.persistence(coupon.getValorMinimoCompra());
        if (base.compareTo(minimum) < 0) {
            throw new CouponMinimumException(minimum, base);
        }
        BigDecimal discount = coupon.getTipo() == RewardDiscountType.PERCENTUAL
                ? base.multiply(coupon.getValor().movePointLeft(2))
                : coupon.getValor();
        if (coupon.getDescontoMaximo() != null) discount = discount.min(coupon.getDescontoMaximo());
        discount = MoneyPolicy.persistence(discount.min(base));
        BigDecimal preciseFinal = MoneyPolicy.persistence(base.subtract(discount));

        order.setCoupon(coupon);
        order.setDescontoCupom(discount);
        order.setDesconto(MoneyPolicy.persistence(order.getDescontoPromocoes().add(discount)));
        order.setTotalCalculado(preciseFinal);
        order.setTotalCobrado(MoneyPolicy.chargedForPersistence(preciseFinal));
        coupon.setStatus(CustomerCoupon.CouponStatus.RESERVED);
        coupon.setReservedOrder(order);
        return new CouponCalculation(base, discount, order.getTotalCobrado());
    }

    public void finalizeForPayment(Order order, Instant now) {
        if (order.isPriceFinalized()) {
            validateFrozen(order, now);
            return;
        }
        if (order.getStatus() != OrderStatus.PENDING || order.getCarrinho() == null
                || order.getCarrinho().getStatus() != CarrinhoStatus.READY_FOR_PAYMENT) {
            throw priceNotFinalized("Order não está pronta para fechamento financeiro");
        }
        validateFinancialIdentity(order);
        validateCouponReservation(order, now);
        order.setPriceFinalizedAt(now);
        order.getCarrinho().setStatus(CarrinhoStatus.PAYMENT_PENDING);
    }

    public void prepareFinancialRetry(Order order, CustomerCoupon coupon, Instant now) {
        if (!order.isPriceFinalized()) {
            throw priceNotFinalized("Order ainda não possui preço final congelado");
        }
        validateFinancialIdentity(order);
        PaymentAttempt current = order.getPagamento();
        if (current == null || !current.getStatus().allowsFinancialRetry()) {
            throw conflict("ACTIVE_PAYMENT_ATTEMPT_EXISTS",
                    "A tentativa atual ainda não permite retry financeiro");
        }
        if (order.getCoupon() != null) {
            if (coupon == null || !coupon.getId().equals(order.getCoupon().getId())) {
                throw conflict("COUPON_RESERVATION_MISMATCH",
                        "A reserva do cupom não corresponde à Order");
            }
            requireCouponAvailable(coupon, now);
            validateCouponTenantAndOwner(order, coupon);
            coupon.setStatus(CustomerCoupon.CouponStatus.RESERVED);
            coupon.setReservedOrder(order);
        }
        validateCouponReservation(order, now);
        order.setStatus(OrderStatus.PENDING);
        order.getCarrinho().setStatus(CarrinhoStatus.PAYMENT_PENDING);
    }

    public void validateFrozen(Order order, Instant now) {
        if (!order.isPriceFinalized()) {
            throw priceNotFinalized("Order ainda não possui preço final congelado");
        }
        validateFinancialIdentity(order);
        validateCouponReservation(order, now);
    }

    private void validateFinancialIdentity(Order order) {
        if (order.getSubtotal() == null || order.getSubtotalPosPromocoes() == null
                || order.getDescontoPromocoes() == null || order.getDescontoCupom() == null
                || order.getDesconto() == null || order.getTotalCalculado() == null
                || order.getTotalCobrado() == null || order.getSubtotal().signum() <= 0
                || order.getSubtotalPosPromocoes().signum() <= 0
                || order.getTotalCalculado().signum() <= 0
                || order.getTotalCobrado().signum() <= 0
                || order.getSubtotal().compareTo(order.getSubtotalPosPromocoes()) < 0
                || order.getSubtotalPosPromocoes().compareTo(order.getTotalCalculado()) < 0
                || order.getDescontoPromocoes().compareTo(
                        MoneyPolicy.persistence(order.getSubtotal().subtract(order.getSubtotalPosPromocoes()))) != 0
                || order.getDescontoCupom().compareTo(
                        MoneyPolicy.persistence(order.getSubtotalPosPromocoes().subtract(order.getTotalCalculado()))) != 0
                || order.getDesconto().compareTo(
                        MoneyPolicy.persistence(order.getDescontoPromocoes().add(order.getDescontoCupom()))) != 0
                || order.getTotalCobrado().compareTo(
                        MoneyPolicy.chargedForPersistence(order.getTotalCalculado())) != 0) {
            throw priceNotFinalized("Snapshot financeiro da Order é inconsistente");
        }
    }

    private void validateCouponReservation(Order order, Instant now) {
        if (order.getDescontoCupom().signum() == 0) {
            if (order.getCoupon() != null) {
                throw conflict("COUPON_RESERVATION_MISMATCH",
                        "Order sem desconto não pode possuir cupom associado");
            }
            return;
        }
        CustomerCoupon coupon = order.getCoupon();
        if (coupon == null) throw conflict("COUPON_NOT_RESERVED", "Cupom não está reservado");
        if (!coupon.getValidade().isAfter(now)) throw conflict("COUPON_EXPIRED", "Cupom expirado");
        if (coupon.getStatus() == CustomerCoupon.CouponStatus.USED) {
            throw conflict("COUPON_ALREADY_USED", "Cupom já utilizado");
        }
        if (coupon.getStatus() != CustomerCoupon.CouponStatus.RESERVED
                || coupon.getReservedOrder() == null
                || !order.getIdOrder().equals(coupon.getReservedOrder().getIdOrder())) {
            throw conflict("COUPON_RESERVATION_MISMATCH",
                    "Cupom não está reservado para esta Order");
        }
    }

    private void requireEditable(Order order) {
        if (order == null || order.isPriceFinalized()
                || order.getStatus() != OrderStatus.PENDING
                || order.getCarrinho() == null
                || order.getCarrinho().getStatus() != CarrinhoStatus.READY_FOR_PAYMENT
                || order.getPaymentAttempts() != null && !order.getPaymentAttempts().isEmpty()) {
            throw conflict("ORDER_NOT_EDITABLE", "Order já foi fechada para pagamento");
        }
    }

    private void validateCouponTenantAndOwner(Order order, CustomerCoupon coupon) {
        if (coupon == null || coupon.getEmpresa() == null || order.getEmpresa() == null
                || !order.getEmpresa().getId().equals(coupon.getEmpresa().getId())) {
            throw conflict("COUPON_RESERVATION_MISMATCH", "Cupom pertence a outro tenant");
        }
        if (order.getUser() == null || coupon.getUser() == null
                || !order.getUser().getIdUser().equals(coupon.getUser().getIdUser())) {
            throw new ApiBusinessException(HttpStatus.FORBIDDEN, "COUPON_OWNER_MISMATCH",
                    "Cupom não pertence ao cliente vinculado à Order");
        }
    }

    private void requireCouponAvailable(CustomerCoupon coupon, Instant now) {
        if (!coupon.getValidade().isAfter(now)) {
            throw conflict("COUPON_EXPIRED", "Cupom expirado");
        }
        if (coupon.getStatus() == CustomerCoupon.CouponStatus.USED) {
            throw conflict("COUPON_ALREADY_USED", "Cupom já utilizado");
        }
        if (coupon.getStatus() != CustomerCoupon.CouponStatus.AVAILABLE
                || coupon.getReservedOrder() != null) {
            throw conflict("COUPON_NOT_AVAILABLE", "Cupom não está disponível");
        }
    }

    private static ApiBusinessException priceNotFinalized(String message) {
        return conflict("ORDER_PRICE_NOT_FINALIZED", message);
    }

    private static ApiBusinessException conflict(String code, String message) {
        return new ApiBusinessException(HttpStatus.CONFLICT, code, message);
    }

    public record CouponCalculation(BigDecimal baseAmount, BigDecimal discount, BigDecimal total) {}

    public static class CouponMinimumException extends ApiBusinessException {
        private final BigDecimal minimum;
        private final BigDecimal current;

        public CouponMinimumException(BigDecimal minimum, BigDecimal current) {
            super(HttpStatus.CONFLICT, "COUPON_MINIMUM_NOT_REACHED",
                    "Valor mínimo do cupom não atingido");
            this.minimum = minimum;
            this.current = current;
        }

        public BigDecimal minimum() { return minimum; }
        public BigDecimal current() { return current; }
    }
}

package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Carrinho;
import com.jefiro.app247.domain.model.CartItem;
import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.appclient.CustomerCoupon;
import com.jefiro.app247.domain.model.appclient.RewardDiscountType;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.enum_type.CarrinhoStatus;
import com.jefiro.app247.domain.model.enum_type.order.OrderStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderPriceFinalizationServiceTest {
    private final OrderPriceFinalizationService service = new OrderPriceFinalizationService();

    @Test
    void snapshotSeparaPrecoOriginalPromocaoETotalPosPromocao() {
        Fixture fixture = fixture("150.00", "120.00");

        service.initializeSnapshot(fixture.order, fixture.cart);

        assertThat(fixture.order.getSubtotal()).isEqualByComparingTo("150.000000");
        assertThat(fixture.order.getSubtotalPosPromocoes()).isEqualByComparingTo("120.000000");
        assertThat(fixture.order.getDescontoPromocoes()).isEqualByComparingTo("30.000000");
        assertThat(fixture.order.getDescontoCupom()).isEqualByComparingTo("0.000000");
        assertThat(fixture.order.getTotalCobrado()).isEqualByComparingTo("120.000000");
    }

    @Test
    void cupomDezPorCentoSobreCentoEVinteRespeitaMinimoEMaximo() {
        Fixture fixture = fixture("150.00", "120.00");
        service.initializeSnapshot(fixture.order, fixture.cart);
        CustomerCoupon coupon = coupon(fixture, RewardDiscountType.PERCENTUAL,
                "10", "100", "25");

        var result = service.reserveCoupon(fixture.order, coupon, Instant.now());
        service.finalizeForPayment(fixture.order, Instant.now());

        assertThat(result.baseAmount()).isEqualByComparingTo("120.000000");
        assertThat(result.discount()).isEqualByComparingTo("12.000000");
        assertThat(result.total()).isEqualByComparingTo("108.000000");
        assertThat(fixture.order.getDesconto()).isEqualByComparingTo("42.000000");
        assertThat(coupon.getStatus()).isEqualTo(CustomerCoupon.CouponStatus.RESERVED);
        assertThat(coupon.getReservedOrder()).isSameAs(fixture.order);
        assertThat(fixture.cart.getStatus()).isEqualTo(CarrinhoStatus.PAYMENT_PENDING);
    }

    @Test
    void minimoNaoAtingidoInformaDiferencaENaoReserva() {
        Fixture fixture = fixture("80.00", "80.00");
        service.initializeSnapshot(fixture.order, fixture.cart);
        CustomerCoupon coupon = coupon(fixture, RewardDiscountType.PERCENTUAL,
                "10", "100", "25");

        assertThatThrownBy(() -> service.reserveCoupon(fixture.order, coupon, Instant.now()))
                .isInstanceOf(OrderPriceFinalizationService.CouponMinimumException.class)
                .satisfies(error -> {
                    var minimum = (OrderPriceFinalizationService.CouponMinimumException) error;
                    assertThat(minimum.minimum().subtract(minimum.current()))
                            .isEqualByComparingTo("20.000000");
                });
        assertThat(coupon.getStatus()).isEqualTo(CustomerCoupon.CouponStatus.AVAILABLE);
        assertThat(fixture.order.getCoupon()).isNull();
    }

    @Test
    void descontoPercentualEhLimitadoAoMaximoDeVinteECinco() {
        Fixture fixture = fixture("400.00", "400.00");
        service.initializeSnapshot(fixture.order, fixture.cart);
        CustomerCoupon coupon = coupon(fixture, RewardDiscountType.PERCENTUAL,
                "10", "0", "25");

        var result = service.reserveCoupon(fixture.order, coupon, Instant.now());

        assertThat(result.discount()).isEqualByComparingTo("25.000000");
        assertThat(result.total()).isEqualByComparingTo("375.000000");
    }

    @Test
    void valorFixoUsaBigDecimalENuncaUltrapassaBase() {
        Fixture fixture = fixture("20.00", "20.00");
        service.initializeSnapshot(fixture.order, fixture.cart);
        CustomerCoupon coupon = coupon(fixture, RewardDiscountType.VALOR_FIXO,
                "50", "0", null);

        var result = service.reserveCoupon(fixture.order, coupon, Instant.now());

        assertThat(result.discount()).isEqualByComparingTo("20.000000");
        assertThat(result.total()).isEqualByComparingTo("0.000000");
    }

    @Test
    void precoCongeladoNaoMudaQuandoSnapshotDoCarrinhoMuda() {
        Fixture fixture = fixture("120.00", "100.00");
        service.initializeSnapshot(fixture.order, fixture.cart);
        service.finalizeForPayment(fixture.order, Instant.now());
        fixture.item.setOriginalPrice(new BigDecimal("999.00"));
        fixture.item.setCalculatedSubtotal(new BigDecimal("999.00"));

        service.validateFrozen(fixture.order, Instant.now());

        assertThat(fixture.order.getSubtotal()).isEqualByComparingTo("120.000000");
        assertThat(fixture.order.getTotalCobrado()).isEqualByComparingTo("100.000000");
    }

    private Fixture fixture(String original, String afterPromotion) {
        Empresa empresa = Empresa.builder().id("empresa-a").build();
        User user = User.builder().idUser("user-a").empresa(empresa).build();
        Carrinho cart = new Carrinho();
        cart.setStatus(CarrinhoStatus.READY_FOR_PAYMENT);
        cart.setEmpresa(empresa);
        CartItem item = new CartItem();
        item.setOriginalPrice(new BigDecimal(original));
        item.setCalculatedSubtotal(new BigDecimal(afterPromotion));
        item.setQuantity(BigDecimal.ONE);
        cart.addItem(item);
        Order order = new Order();
        order.setIdOrder("order-a");
        order.setEmpresa(empresa);
        order.setUser(user);
        order.setCarrinho(cart);
        order.setStatus(OrderStatus.PENDING);
        return new Fixture(order, cart, item, empresa, user);
    }

    private CustomerCoupon coupon(Fixture fixture, RewardDiscountType type,
                                  String value, String minimum, String maximum) {
        CustomerCoupon coupon = new CustomerCoupon();
        coupon.setId("coupon-a");
        coupon.setEmpresa(fixture.empresa);
        coupon.setUser(fixture.user);
        coupon.setTipo(type);
        coupon.setValor(new BigDecimal(value));
        coupon.setValorMinimoCompra(new BigDecimal(minimum));
        coupon.setDescontoMaximo(maximum == null ? null : new BigDecimal(maximum));
        coupon.setValidade(Instant.now().plusSeconds(3600));
        coupon.setStatus(CustomerCoupon.CouponStatus.AVAILABLE);
        return coupon;
    }

    private record Fixture(Order order, Carrinho cart, CartItem item,
                           Empresa empresa, User user) {}
}

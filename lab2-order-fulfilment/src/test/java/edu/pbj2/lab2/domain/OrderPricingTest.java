package edu.pbj2.lab2.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class OrderPricingTest {

  @Test
  void standardOrderAbove100GetsFreeShipping() {
    Map<String, Object> p = OrderPricing.calculate(89.90, 2, "REGULAR", "STANDARD");

    assertThat(p.get("subtotal")).isEqualTo(179.80);
    assertThat(p.get("discount")).isEqualTo(0.0);
    assertThat(p.get("shippingFee")).isEqualTo(0.0);
    assertThat(p.get("tax")).isEqualTo(35.96);
    assertThat(p.get("orderTotal")).isEqualTo(215.76);
    assertThat(p.get("currency")).isEqualTo("EUR");
  }

  @Test
  void vipGetsTenPercentDiscountAndSameDayFee() {
    Map<String, Object> p = OrderPricing.calculate(1899.00, 1, "VIP", "SAME_DAY");

    assertThat(p.get("discount")).isEqualTo(189.90);
    assertThat(p.get("tax")).isEqualTo(341.82);
    assertThat(p.get("shippingFee")).isEqualTo(29.99);
    assertThat(p.get("orderTotal")).isEqualTo(2080.91);
  }

  @Test
  void smallStandardOrderPaysShipping() {
    Map<String, Object> p = OrderPricing.calculate(20.00, 1, "NEW", "STANDARD");

    assertThat(p.get("shippingFee")).isEqualTo(4.99);
    assertThat(p.get("orderTotal")).isEqualTo(28.99);
  }
}

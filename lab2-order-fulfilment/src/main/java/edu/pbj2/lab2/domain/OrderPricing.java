package edu.pbj2.lab2.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Educational pricing rules:
 * <ul>
 *   <li>discount: VIP customers 10% of the subtotal,</li>
 *   <li>shipping: STANDARD 4.99 (free from 100 EUR net), EXPRESS 14.99, SAME_DAY 29.99,</li>
 *   <li>VAT 20% on the discounted subtotal,</li>
 *   <li>orderTotal = subtotal - discount + VAT + shipping.</li>
 * </ul>
 */
public final class OrderPricing {

  public static final String CURRENCY = "EUR";
  static final BigDecimal VAT_RATE = new BigDecimal("0.20");

  private OrderPricing() {}

  public static Map<String, Object> calculate(double unitPrice, int quantity, String customerCategory,
      String deliveryPreference) {
    BigDecimal subtotal = money(BigDecimal.valueOf(unitPrice).multiply(BigDecimal.valueOf(quantity)));
    BigDecimal discount = "VIP".equals(customerCategory)
        ? money(subtotal.multiply(new BigDecimal("0.10")))
        : money(BigDecimal.ZERO);
    BigDecimal net = subtotal.subtract(discount);
    BigDecimal shipping = switch (deliveryPreference) {
      case "SAME_DAY" -> new BigDecimal("29.99");
      case "EXPRESS" -> new BigDecimal("14.99");
      default -> net.compareTo(new BigDecimal("100")) >= 0 ? money(BigDecimal.ZERO) : new BigDecimal("4.99");
    };
    BigDecimal tax = money(net.multiply(VAT_RATE));
    BigDecimal total = money(net.add(tax).add(shipping));

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("subtotal", subtotal.doubleValue());
    out.put("discount", discount.doubleValue());
    out.put("shippingFee", shipping.doubleValue());
    out.put("tax", tax.doubleValue());
    out.put("orderTotal", total.doubleValue());
    out.put("currency", CURRENCY);
    return out;
  }

  private static BigDecimal money(BigDecimal v) {
    return v.setScale(2, RoundingMode.HALF_UP);
  }
}

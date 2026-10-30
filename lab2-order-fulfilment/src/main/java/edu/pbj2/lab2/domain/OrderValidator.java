package edu.pbj2.lab2.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure order validation against the synthetic customer registry and product catalogue. */
public class OrderValidator {

  static final Set<String> DELIVERY_PREFERENCES = Set.of("STANDARD", "EXPRESS", "SAME_DAY");

  private final CustomerRegistry customers;
  private final InventoryService inventory;

  public OrderValidator(CustomerRegistry customers, InventoryService inventory) {
    this.customers = customers;
    this.inventory = inventory;
  }

  /** @return process variables to merge into the instance */
  public Map<String, Object> validate(Map<String, Object> v) {
    List<String> errors = new ArrayList<>();
    Map<String, Object> out = new LinkedHashMap<>();

    if (text(v, "orderId") == null) {
      errors.add("orderId is required");
    }

    String customerRef = text(v, "customerReference");
    if (customerRef == null) {
      errors.add("customerReference is required");
    } else {
      customers.find(customerRef).ifPresentOrElse(c -> {
        out.put("customerName", c.name());
        out.put("customerCategory", c.category());
      }, () -> errors.add("customerReference " + customerRef + " is not a known customer"));
    }

    String productId = text(v, "productId");
    if (productId == null) {
      errors.add("productId is required");
    } else {
      inventory.product(productId).ifPresentOrElse(p -> {
        out.put("productName", p.name());
        out.put("unitPrice", p.unitPrice());
      }, () -> errors.add("productId " + productId + " does not exist in the catalogue"));
    }

    Object qty = v.get("quantity");
    if (!(qty instanceof Number n) || n.doubleValue() != Math.rint(n.doubleValue())) {
      errors.add("quantity must be a whole number");
    } else if (n.intValue() < 1 || n.intValue() > 100) {
      errors.add("quantity must be between 1 and 100 (was " + n.intValue() + ")");
    }

    String delivery = text(v, "deliveryPreference");
    if (delivery == null || !DELIVERY_PREFERENCES.contains(delivery)) {
      errors.add("deliveryPreference must be one of " + DELIVERY_PREFERENCES);
    }

    String token = text(v, "paymentToken");
    if (token == null || !token.startsWith("tok_")) {
      errors.add("paymentToken must be a mock payment token (tok_...) - never real card data");
    }

    out.put("orderValid", errors.isEmpty());
    out.put("validationErrors", errors);
    return out;
  }

  private static String text(Map<String, Object> v, String key) {
    Object value = v.get(key);
    return value == null || value.toString().isBlank() ? null : value.toString().trim();
  }
}

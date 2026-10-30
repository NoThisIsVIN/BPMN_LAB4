package edu.pbj2.lab2.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OrderValidatorAndInventoryTest {

  private final InventoryService inventory = InventoryService.withSyntheticCatalogue();
  private final OrderValidator validator = new OrderValidator(CustomerRegistry.synthetic(), inventory);

  private static Map<String, Object> order() {
    Map<String, Object> v = new HashMap<>();
    v.put("orderId", "ORD-TEST-1");
    v.put("customerReference", "CUST-0002");
    v.put("productId", "PRD-1001");
    v.put("quantity", 2);
    v.put("deliveryPreference", "EXPRESS");
    v.put("paymentToken", "tok_test_approve");
    return v;
  }

  @Test
  void validOrderIsEnrichedWithCustomerAndProduct() {
    Map<String, Object> out = validator.validate(order());

    assertThat(out.get("orderValid")).isEqualTo(true);
    assertThat(out.get("customerCategory")).isEqualTo("VIP");
    assertThat(out.get("productName")).isEqualTo("Wireless headphones");
    assertThat(out.get("unitPrice")).isEqualTo(89.90);
  }

  @Test
  void unknownProductAndBadQuantityAreReported() {
    Map<String, Object> v = order();
    v.put("productId", "PRD-9999");
    v.put("quantity", 0);
    v.put("paymentToken", "4111111111111111");

    Map<String, Object> out = validator.validate(v);

    assertThat(out.get("orderValid")).isEqualTo(false);
    assertThat((List<String>) out.get("validationErrors")).containsExactlyInAnyOrder(
        "productId PRD-9999 does not exist in the catalogue",
        "quantity must be between 1 and 100 (was 0)",
        "paymentToken must be a mock payment token (tok_...) - never real card data");
  }

  @Test
  void reservationReducesStockAndReleaseRestoresIt() {
    assertThat(inventory.reserve("PRD-LOW-01", 5)).isEmpty();

    var reservation = inventory.reserve("PRD-LOW-01", 2).orElseThrow();
    assertThat(inventory.available("PRD-LOW-01")).isZero();

    assertThat(inventory.release(reservation.id()).orElseThrow().status()).isEqualTo("RELEASED");
    assertThat(inventory.available("PRD-LOW-01")).isEqualTo(2);
    // releasing twice must not create stock out of thin air
    inventory.release(reservation.id());
    assertThat(inventory.available("PRD-LOW-01")).isEqualTo(2);
  }
}

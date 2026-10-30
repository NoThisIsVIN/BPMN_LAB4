package edu.pbj2.lab2.faults;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * In-memory switches that simulate a broken dependency, so an incident can be produced
 * on purpose, diagnosed in Operate, "repaired" by switching the fault off, and retried.
 */
@Component
public class FaultInjection {

  /** Inventory worker cannot reach the (simulated) inventory service. */
  public static final String INVENTORY_SERVICE_DOWN = "inventory-service-down";
  /** Mock payment gateway answers every charge with HTTP 503. */
  public static final String PAYMENT_GATEWAY_DOWN = "payment-gateway-down";

  private final Map<String, Boolean> faults = new ConcurrentHashMap<>(Map.of(
      INVENTORY_SERVICE_DOWN, false,
      PAYMENT_GATEWAY_DOWN, false));

  public boolean isActive(String fault) {
    return faults.getOrDefault(fault, false);
  }

  public void set(String fault, boolean active) {
    if (!faults.containsKey(fault)) {
      throw new IllegalArgumentException("Unknown fault '" + fault + "'. Known faults: " + faults.keySet());
    }
    faults.put(fault, active);
  }

  public Map<String, Boolean> all() {
    return new TreeMap<>(faults);
  }
}

package edu.pbj2.lab2.faults;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * In-memory switches that simulate a broken dependency, so an incident (or a fallback path) can be
 * produced on purpose, diagnosed in Operate, "repaired" by switching the fault off, and retried.
 */
@Component
public class FaultInjection {

  /** Inventory worker cannot reach the (simulated) inventory service -> retries -> incident. */
  public static final String INVENTORY_SERVICE_DOWN = "inventory-service-down";
  /** Primary payment provider answers every charge with HTTP 503 -> fallback to the backup provider. */
  public static final String PRIMARY_PAYMENT_DOWN = "primary-payment-down";
  /** Backup payment provider answers every charge with HTTP 503 -> retries -> incident. */
  public static final String BACKUP_PAYMENT_DOWN = "backup-payment-down";
  /** Primary carrier booking API is unavailable -> BPMN error -> backup carrier. */
  public static final String PRIMARY_CARRIER_DOWN = "primary-carrier-down";

  private final Map<String, Boolean> faults = new ConcurrentHashMap<>(Map.of(
      INVENTORY_SERVICE_DOWN, false,
      PRIMARY_PAYMENT_DOWN, false,
      BACKUP_PAYMENT_DOWN, false,
      PRIMARY_CARRIER_DOWN, false));

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

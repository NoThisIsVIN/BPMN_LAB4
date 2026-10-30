package edu.pbj2.lab1.faults;

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

  /** Validation worker cannot reach its (simulated) reference-data service. */
  public static final String VALIDATION_REFERENCE_DATA = "validation-reference-data";
  /** Mock credit bureau answers every call with HTTP 503. */
  public static final String CREDIT_BUREAU_DOWN = "credit-bureau-down";

  private final Map<String, Boolean> faults = new ConcurrentHashMap<>(Map.of(
      VALIDATION_REFERENCE_DATA, false,
      CREDIT_BUREAU_DOWN, false));

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

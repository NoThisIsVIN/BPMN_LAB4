package edu.pbj2.lab3.faults;

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

  /** Mock scheduling API answers the slot query with HTTP 503 (registration still works). */
  public static final String SCHEDULING_API_DOWN = "scheduling-api-down";
  /**
   * Another patient grabs every slot we try to book (HTTP 409) until the next slot query; the
   * switch turns itself off then, which reproduces "slot taken while booking -> re-query".
   */
  public static final String BOOKING_CONFLICT_ONCE = "booking-conflict-once";

  private final Map<String, Boolean> faults = new ConcurrentHashMap<>(Map.of(
      SCHEDULING_API_DOWN, false,
      BOOKING_CONFLICT_ONCE, false));

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

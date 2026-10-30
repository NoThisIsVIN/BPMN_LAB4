package edu.pbj2.lab1.faults;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * In-memory switches that simulate a broken dependency, so failures can be produced on purpose:
 * an incident that is diagnosed in Operate, repaired by switching the fault off, and retried, or a
 * fallback path (backup credit bureau) that the process takes by itself.
 */
@Component
public class FaultInjection {

  /** Validation worker cannot reach its (simulated) reference-data service -> incident. */
  public static final String VALIDATION_REFERENCE_DATA = "validation-reference-data";
  /** Primary (CIBIL-like) credit bureau answers HTTP 503 -> process falls back to the backup bureau. */
  public static final String PRIMARY_BUREAU_DOWN = "primary-bureau-down";
  /** Backup credit bureau answers HTTP 503 -> with both down, a loan officer decides without a score. */
  public static final String BACKUP_BUREAU_DOWN = "backup-bureau-down";

  private final Map<String, Boolean> faults = new ConcurrentHashMap<>(Map.of(
      VALIDATION_REFERENCE_DATA, false,
      PRIMARY_BUREAU_DOWN, false,
      BACKUP_BUREAU_DOWN, false));

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

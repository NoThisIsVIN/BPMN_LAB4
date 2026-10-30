package edu.pbj2.lab2.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * How often a customer may try another payment method after a decline, and how long the process waits
 * for it. The wait is a BPMN timer whose duration comes from the variable {@code paymentRetryTimeout},
 * so a test can use seconds while the default stays realistic.
 */
public final class PaymentRetryPolicy {

  public static final String DEFAULT_TIMEOUT = "PT30M";
  public static final int DEFAULT_MAX_ATTEMPTS = 2;

  private PaymentRetryPolicy() {}

  /** Defaults for the retry variables, only for those the order did not bring along. */
  public static Map<String, Object> defaults(Map<String, Object> vars) {
    Map<String, Object> out = new LinkedHashMap<>();
    if (vars.get("paymentRetryTimeout") == null) {
      out.put("paymentRetryTimeout", DEFAULT_TIMEOUT);
    }
    if (!(vars.get("maxPaymentAttempts") instanceof Number)) {
      out.put("maxPaymentAttempts", DEFAULT_MAX_ATTEMPTS);
    }
    out.put("paymentAttempts", 1);
    return out;
  }

  /** The gateway "Another payment attempt allowed?" evaluates the same rule in FEEL. */
  public static boolean retryAllowed(int attempts, int maxAttempts) {
    return attempts < maxAttempts;
  }
}

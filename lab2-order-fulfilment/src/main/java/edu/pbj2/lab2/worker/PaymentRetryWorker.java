package edu.pbj2.lab2.worker;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Send task "Ask customer for another payment method" (job type order.request-payment-method).
 * Tells the customer the card was declined and counts the attempt; the process then waits for the
 * message {@code payment-method-updated} (correlation key = orderId) carrying a new mock token.
 */
@Component
public class PaymentRetryWorker {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentRetryWorker.class);

  @JobWorker(type = "order.request-payment-method")
  public Map<String, Object> requestNewPaymentMethod(@Variable String orderId, @Variable String customerReference,
      @Variable String declineReason, @Variable Integer paymentAttempts, @Variable String paymentRetryTimeout) {
    int attempts = (paymentAttempts == null ? 1 : paymentAttempts) + 1;
    String message = "Your payment for order " + orderId + " was declined (" + declineReason + "). Please choose "
        + "another payment method within " + paymentRetryTimeout + " to keep your order.";
    LOG.info("[{}] EMAIL to customer {} (ASK_NEW_PAYMENT_METHOD): {} -> attempt {}", orderId, customerReference,
        message, attempts);

    Map<String, Object> n = new LinkedHashMap<>();
    n.put("template", "ASK_NEW_PAYMENT_METHOD");
    n.put("to", customerReference);
    n.put("message", message);
    n.put("sentAt", OffsetDateTime.now().toString());
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("paymentAttempts", attempts);
    out.put("customerNotification", n);
    return out;
  }
}

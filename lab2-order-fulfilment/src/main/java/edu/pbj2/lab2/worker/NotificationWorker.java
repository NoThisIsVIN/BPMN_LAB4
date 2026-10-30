package edu.pbj2.lab2.worker;

import io.camunda.client.annotation.CustomHeaders;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Send tasks. The message template comes from the task header "template" in the BPMN, so one
 * worker serves every customer notification. Messages are logged instead of e-mailed.
 */
@Component
public class NotificationWorker {

  private static final Logger LOG = LoggerFactory.getLogger(NotificationWorker.class);

  @JobWorker(type = "order.notify-customer")
  public Map<String, Object> notifyCustomer(ActivatedJob job, @CustomHeaders Map<String, String> headers) {
    Map<String, Object> v = job.getVariablesAsMap();
    String orderId = String.valueOf(v.get("orderId"));
    String template = headers.getOrDefault("template", "GENERIC");
    String message = switch (template) {
      case "ORDER_CONFIRMED" -> "Order " + orderId + " is paid and on its way. Tracking number " + v.get("trackingNumber")
          + ", estimated delivery " + v.get("estimatedDelivery") + ".";
      case "ORDER_INVALID" -> "Order " + orderId + " could not be accepted: " + v.get("validationErrors");
      case "OUT_OF_STOCK" -> "Order " + orderId + " was cancelled: only " + v.get("availableQuantity")
          + " unit(s) of " + v.get("productId") + " in stock.";
      case "PAYMENT_DECLINED" -> "Order " + orderId + " was cancelled: payment declined (" + v.get("declineReason") + ").";
      case "CANCELLED_BY_REVIEW" -> "Order " + orderId + " was cancelled after manual verification: "
          + v.get("verificationComment");
      default -> "Update on order " + orderId;
    };
    LOG.info("[{}] EMAIL to customer {} ({}): {}", orderId, v.get("customerReference"), template, message);

    Map<String, Object> n = new LinkedHashMap<>();
    n.put("template", template);
    n.put("to", v.get("customerReference"));
    n.put("message", message);
    n.put("sentAt", OffsetDateTime.now().toString());
    return Map.of("customerNotification", n);
  }
}

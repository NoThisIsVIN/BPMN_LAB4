package edu.pbj2.lab2.mock;

import edu.pbj2.lab2.faults.FaultInjection;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mock payment gateway, called by the REST Outbound Connector of task "Process payment".
 * Accepts only mock tokens – never real card data or credentials.
 *
 * <ul>
 *   <li>{@code tok_test_approve} -> 200, status APPROVED</li>
 *   <li>{@code tok_test_decline_funds} -> 200, status DECLINED "Insufficient funds"</li>
 *   <li>{@code tok_test_decline_fraud} -> 200, status DECLINED "Suspected fraud"</li>
 *   <li>{@code tok_test_gateway_error} -> 503</li>
 *   <li>fault {@code payment-gateway-down} -> 503 for every request</li>
 *   <li>any other token -> 200, status DECLINED "Unknown payment token"</li>
 * </ul>
 */
@RestController
@RequestMapping("/mock/payment-gateway")
public class PaymentGatewayController {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentGatewayController.class);
  private final FaultInjection faults;

  public PaymentGatewayController(FaultInjection faults) {
    this.faults = faults;
  }

  @PostMapping("/charges")
  public ResponseEntity<Map<String, Object>> charge(@RequestBody Map<String, Object> request) {
    String token = String.valueOf(request.get("paymentToken"));
    LOG.info("Charge requested: {}", request);

    if (faults.isActive(FaultInjection.PAYMENT_GATEWAY_DOWN) || "tok_test_gateway_error".equals(token)) {
      LOG.warn("Payment gateway responding 503");
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(Map.of("error", "Payment gateway temporarily unavailable", "status", 503));
    }

    String declineReason = switch (token) {
      case "tok_test_approve" -> null;
      case "tok_test_decline_funds" -> "Insufficient funds";
      case "tok_test_decline_fraud" -> "Suspected fraud";
      default -> "Unknown payment token";
    };
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("transactionId", "TXN-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase());
    body.put("orderId", request.get("orderId"));
    body.put("amount", request.get("amount"));
    body.put("currency", request.get("currency"));
    body.put("status", declineReason == null ? "APPROVED" : "DECLINED");
    body.put("declineReason", declineReason);
    body.put("processedAt", OffsetDateTime.now().toString());
    LOG.info("Charge result: {}", body);
    return ResponseEntity.ok(body);
  }
}

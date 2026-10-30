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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Two mock payment providers, called by the REST Outbound Connectors of the tasks
 * "Charge payment – primary provider" ({@code POST /mock/payment-gateway/primary/charges}) and
 * "Charge payment – backup provider" ({@code POST /mock/payment-gateway/backup/charges}).
 * Accepts only mock tokens – never real card data or credentials.
 *
 * <ul>
 *   <li>{@code tok_test_approve} -> 200, status APPROVED</li>
 *   <li>{@code tok_test_decline_funds} -> 200, status DECLINED "Insufficient funds"</li>
 *   <li>{@code tok_test_decline_fraud} -> 200, status DECLINED "Suspected fraud"</li>
 *   <li>any other token -> 200, status DECLINED "Unknown payment token"</li>
 *   <li>fault {@code primary-payment-down} / {@code backup-payment-down} -> 503 from that provider</li>
 * </ul>
 * A decline is a business answer (HTTP 200), a 5xx is a technical failure of the provider.
 */
@RestController
@RequestMapping("/mock/payment-gateway")
public class PaymentGatewayController {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentGatewayController.class);
  private final FaultInjection faults;

  public PaymentGatewayController(FaultInjection faults) {
    this.faults = faults;
  }

  @PostMapping("/{provider}/charges")
  public ResponseEntity<Map<String, Object>> charge(@PathVariable String provider,
      @RequestBody Map<String, Object> request) {
    String token = String.valueOf(request.get("paymentToken"));
    LOG.info("Charge requested at {} provider: {}", provider, request);

    boolean down = switch (provider) {
      case "primary" -> faults.isActive(FaultInjection.PRIMARY_PAYMENT_DOWN);
      case "backup" -> faults.isActive(FaultInjection.BACKUP_PAYMENT_DOWN);
      default -> true;
    };
    if (down) {
      LOG.warn("{} payment provider responding 503", provider);
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(Map.of("error", capitalise(provider) + " payment provider temporarily unavailable", "status", 503));
    }

    String declineReason = switch (token) {
      case "tok_test_approve" -> null;
      case "tok_test_decline_funds" -> "Insufficient funds";
      case "tok_test_decline_fraud" -> "Suspected fraud";
      default -> "Unknown payment token";
    };
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("provider", provider.toUpperCase());
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

  private static String capitalise(String s) {
    return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }
}

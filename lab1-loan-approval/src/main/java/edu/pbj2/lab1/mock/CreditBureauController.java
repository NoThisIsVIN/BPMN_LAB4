package edu.pbj2.lab1.mock;

import edu.pbj2.lab1.faults.FaultInjection;
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
 * Mock credit bureau, called by the REST Outbound Connector of task "Request credit assessment".
 *
 * <p>Deterministic behaviour for testing:
 * <ul>
 *   <li>Known synthetic applicant IDs return a fixed score (see {@link #REGISTRY}).</li>
 *   <li>{@code APP-ERR-500} returns HTTP 500, {@code APP-TIMEOUT} answers after 8 s (connector timeout is 5 s).</li>
 *   <li>Fault {@code credit-bureau-down} returns HTTP 503 for every request.</li>
 *   <li>Any other ID gets a stable pseudo-random score between 560 and 799.</li>
 * </ul>
 */
@RestController
@RequestMapping("/mock/credit-bureau")
public class CreditBureauController {

  private static final Logger LOG = LoggerFactory.getLogger(CreditBureauController.class);

  /** Synthetic applicants used by the test scenarios. */
  static final Map<String, Integer> REGISTRY = Map.of(
      "APP-1001", 762,
      "APP-1002", 512,
      "APP-1003", 648,
      "APP-1004", 731,
      "APP-1005", 705,
      "APP-1008", 744);

  private final FaultInjection faults;

  public CreditBureauController(FaultInjection faults) {
    this.faults = faults;
  }

  @PostMapping("/assessments")
  public ResponseEntity<Map<String, Object>> assess(@RequestBody Map<String, Object> request) throws InterruptedException {
    String applicantId = String.valueOf(request.get("applicantId"));
    LOG.info("Credit assessment requested: {}", request);

    if (faults.isActive(FaultInjection.CREDIT_BUREAU_DOWN)) {
      return error(HttpStatus.SERVICE_UNAVAILABLE, "Credit bureau temporarily unavailable");
    }
    if ("APP-ERR-500".equals(applicantId)) {
      return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal scoring engine error");
    }
    if ("APP-TIMEOUT".equals(applicantId)) {
      Thread.sleep(8_000);
    }

    int score = REGISTRY.getOrDefault(applicantId, 560 + Math.floorMod(applicantId.hashCode(), 240));
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("bureauReference", "CB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    body.put("applicantId", applicantId);
    body.put("creditScore", score);
    body.put("riskBand", riskBand(score));
    body.put("activeCreditAccounts", 1 + Math.floorMod(applicantId.hashCode(), 5));
    body.put("assessedAt", OffsetDateTime.now().toString());
    LOG.info("Credit assessment result: {}", body);
    return ResponseEntity.ok(body);
  }

  static String riskBand(int score) {
    if (score >= 750) return "A";
    if (score >= 700) return "B";
    if (score >= 650) return "C";
    if (score >= 550) return "D";
    return "E";
  }

  private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
    LOG.warn("Credit bureau responding {}: {}", status.value(), message);
    return ResponseEntity.status(status).body(Map.of("error", message, "status", status.value()));
  }
}

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
 * Two mock credit bureaus, called by the REST Outbound Connectors "Request credit score – primary bureau"
 * and "… – backup bureau". Both score the same synthetic applicants, like two real bureaus holding the same
 * credit history.
 *
 * <p>Deterministic behaviour for testing:
 * <ul>
 *   <li>Known synthetic applicant IDs return a fixed score (see {@link #REGISTRY}).</li>
 *   <li>Primary only: {@code APP-ERR-500} returns HTTP 500 and {@code APP-TIMEOUT} answers after 8 s
 *       (the connector timeout is 5 s), so the process falls back to the backup bureau.</li>
 *   <li>Faults {@code primary-bureau-down} / {@code backup-bureau-down} return HTTP 503 for every request.</li>
 *   <li>Any other ID gets a stable pseudo-random score between 560 and 799.</li>
 * </ul>
 */
@RestController
@RequestMapping("/mock/credit-bureau")
public class CreditBureauController {

  private static final Logger LOG = LoggerFactory.getLogger(CreditBureauController.class);

  /** Synthetic applicants used by the test scenarios. */
  static final Map<String, Integer> REGISTRY = Map.ofEntries(
      Map.entry("APP-1001", 762),
      Map.entry("APP-1002", 512),
      Map.entry("APP-1003", 648),
      Map.entry("APP-1004", 731),
      Map.entry("APP-1005", 705),
      Map.entry("APP-1008", 744),
      Map.entry("APP-ERR-500", 741),
      Map.entry("APP-TIMEOUT", 726));

  private final FaultInjection faults;

  public CreditBureauController(FaultInjection faults) {
    this.faults = faults;
  }

  @PostMapping("/primary/assessments")
  public ResponseEntity<Map<String, Object>> primary(@RequestBody Map<String, Object> request) throws InterruptedException {
    String applicantId = String.valueOf(request.get("applicantId"));
    LOG.info("PRIMARY bureau: assessment requested {}", request);
    if (faults.isActive(FaultInjection.PRIMARY_BUREAU_DOWN)) {
      return error("PRIMARY", HttpStatus.SERVICE_UNAVAILABLE, "Primary credit bureau temporarily unavailable");
    }
    if ("APP-ERR-500".equals(applicantId)) {
      return error("PRIMARY", HttpStatus.INTERNAL_SERVER_ERROR, "Internal scoring engine error");
    }
    if ("APP-TIMEOUT".equals(applicantId)) {
      Thread.sleep(8_000);
    }
    return ResponseEntity.ok(assessment("CB", applicantId));
  }

  @PostMapping("/backup/assessments")
  public ResponseEntity<Map<String, Object>> backup(@RequestBody Map<String, Object> request) {
    String applicantId = String.valueOf(request.get("applicantId"));
    LOG.info("BACKUP bureau: assessment requested {}", request);
    if (faults.isActive(FaultInjection.BACKUP_BUREAU_DOWN)) {
      return error("BACKUP", HttpStatus.SERVICE_UNAVAILABLE, "Backup credit bureau temporarily unavailable");
    }
    return ResponseEntity.ok(assessment("BB", applicantId));
  }

  private static Map<String, Object> assessment(String prefix, String applicantId) {
    int score = REGISTRY.getOrDefault(applicantId, 560 + Math.floorMod(applicantId.hashCode(), 240));
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("bureauReference", prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    body.put("applicantId", applicantId);
    body.put("creditScore", score);
    body.put("riskBand", riskBand(score));
    body.put("activeCreditAccounts", 1 + Math.floorMod(applicantId.hashCode(), 5));
    body.put("assessedAt", OffsetDateTime.now().toString());
    LOG.info("{} bureau result: {}", prefix, body);
    return body;
  }

  static String riskBand(int score) {
    if (score >= 750) return "A";
    if (score >= 700) return "B";
    if (score >= 650) return "C";
    if (score >= 550) return "D";
    return "E";
  }

  private static ResponseEntity<Map<String, Object>> error(String bureau, HttpStatus status, String message) {
    LOG.warn("{} bureau responding {}: {}", bureau, status.value(), message);
    return ResponseEntity.status(status).body(Map.of("error", message, "status", status.value()));
  }
}

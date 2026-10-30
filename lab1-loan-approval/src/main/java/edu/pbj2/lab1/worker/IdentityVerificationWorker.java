package edu.pbj2.lab1.worker;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Service task "Verify identity (KYC)" (job type loan.verify-identity).
 *
 * <p>Synthetic know-your-customer check: the document check passes for every synthetic applicant except the
 * ones on the synthetic watch-list ({@code APP-KYC-...} references), which fail identity verification.
 */
@Component
public class IdentityVerificationWorker {

  private static final Logger LOG = LoggerFactory.getLogger(IdentityVerificationWorker.class);

  static boolean onWatchList(String applicantId) {
    return applicantId != null && applicantId.startsWith("APP-KYC-");
  }

  @JobWorker(type = "loan.verify-identity")
  public Map<String, Object> verify(@Variable String applicationId, @Variable String applicantId,
      @Variable String fullName) {
    boolean verified = !onWatchList(applicantId);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("identityVerified", verified);
    out.put("kycReference", "KYC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    if (!verified) {
      out.put("decisionReason", "Identity could not be verified (document mismatch / watch-list hit)");
      out.put("decisionSource", "KYC CHECK");
    }
    LOG.info("[{}] KYC for {} ({}): verified={}", applicationId, fullName, applicantId, verified);
    return out;
  }
}

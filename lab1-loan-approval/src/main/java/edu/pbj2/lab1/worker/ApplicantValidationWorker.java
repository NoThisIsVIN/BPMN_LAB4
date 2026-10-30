package edu.pbj2.lab1.worker;

import edu.pbj2.lab1.faults.FaultInjection;
import edu.pbj2.lab1.validation.ApplicantValidator;
import edu.pbj2.lab1.validation.MalformedVariableException;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.exception.CamundaError;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Service task "Validate applicant data" (job type loan.validate-applicant). */
@Component
public class ApplicantValidationWorker {

  private static final Logger LOG = LoggerFactory.getLogger(ApplicantValidationWorker.class);
  private static final Duration RETRY_BACKOFF = Duration.ofSeconds(5);

  private final FaultInjection faults;
  private final ApplicantValidator validator;

  public ApplicantValidationWorker(FaultInjection faults, @Value("${loan.reference-apr}") double referenceApr) {
    this.faults = faults;
    this.validator = new ApplicantValidator(referenceApr);
  }

  @JobWorker(type = "loan.validate-applicant")
  public Map<String, Object> validate(ActivatedJob job) {
    String applicationId = String.valueOf(job.getVariablesAsMap().get("applicationId"));

    if (faults.isActive(FaultInjection.VALIDATION_REFERENCE_DATA)) {
      // Transient dependency outage: let Zeebe retry with back-off; when retries reach 0 an incident is raised.
      int retriesLeft = job.getRetries() - 1;
      LOG.error("[{}] reference-data service unavailable, failing job (retries left: {})", applicationId, retriesLeft);
      throw CamundaError.jobError(
          "Validation reference-data service unavailable (connection refused). "
              + "Fix: restore the service (POST /admin/faults/validation-reference-data?enabled=false) and retry.",
          null, retriesLeft, RETRY_BACKOFF);
    }

    try {
      Map<String, Object> result = validator.validate(job.getVariablesAsMap());
      result.put("validatedAt", OffsetDateTime.now().toString());
      LOG.info("[{}] validation passed={} errors={}", applicationId, result.get("validationPassed"),
          result.get("validationErrors"));
      return result;
    } catch (MalformedVariableException e) {
      // Data problem: retrying will not help, so raise the incident immediately (retries = 0).
      LOG.error("[{}] {}", applicationId, e.getMessage());
      throw CamundaError.jobError(e.getMessage(), null, 0);
    }
  }
}

package edu.pbj2.lab3.worker;

import edu.pbj2.lab3.validation.MalformedVariableException;
import edu.pbj2.lab3.validation.RegistrationValidator;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.exception.CamundaError;
import java.time.OffsetDateTime;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Service task "Validate registration" (job type triage.validate-registration). */
@Component
public class RegistrationValidationWorker {

  private static final Logger LOG = LoggerFactory.getLogger(RegistrationValidationWorker.class);
  private final RegistrationValidator validator = new RegistrationValidator();

  @JobWorker(type = "triage.validate-registration")
  public Map<String, Object> validate(ActivatedJob job) {
    Object patientReference = job.getVariablesAsMap().get("patientReference");
    try {
      Map<String, Object> result = validator.validate(job.getVariablesAsMap());
      result.put("registrationValidatedAt", OffsetDateTime.now().toString());
      LOG.info("[{}] registration valid={} errors={}", patientReference, result.get("registrationValid"),
          result.get("registrationErrors"));
      return result;
    } catch (MalformedVariableException e) {
      // Data problem: retrying will not help, so raise the incident immediately (retries = 0).
      LOG.error("[{}] {}", patientReference, e.getMessage());
      throw CamundaError.jobError(e.getMessage(), null, 0);
    }
  }
}

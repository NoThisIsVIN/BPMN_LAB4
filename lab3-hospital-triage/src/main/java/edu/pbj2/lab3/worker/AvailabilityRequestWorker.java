package edu.pbj2.lab3.worker;

import edu.pbj2.lab3.scheduling.SchedulingClient;
import edu.pbj2.lab3.scheduling.SearchWindow;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.exception.CamundaError;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

/**
 * Service task "Request appointment availability" (job type triage.request-availability).
 * Builds the search window from the triage target and the patient's preference and registers an
 * availability request with the scheduling system; the REST Connector then retrieves the slots.
 */
@Component
public class AvailabilityRequestWorker {

  private static final Logger LOG = LoggerFactory.getLogger(AvailabilityRequestWorker.class);
  private final SchedulingClient scheduling;

  public AvailabilityRequestWorker(SchedulingClient scheduling) {
    this.scheduling = scheduling;
  }

  @JobWorker(type = "triage.request-availability")
  public Map<String, Object> requestAvailability(
      ActivatedJob job,
      @Variable String patientReference,
      @Variable String department,
      @Variable String timeOfDay,
      @Variable String preferredDate,
      @Variable Integer maxWaitDays,
      @Variable String urgencyCategory) {
    SearchWindow window = SearchWindow.of(LocalDate.now(), maxWaitDays == null ? 14 : maxWaitDays,
        preferredDate == null ? null : LocalDate.parse(preferredDate));

    Map<String, Object> request = new LinkedHashMap<>();
    request.put("patientReference", patientReference);
    request.put("department", department);
    request.put("fromDate", window.from().toString());
    request.put("toDate", window.to().toString());
    request.put("timeOfDay", timeOfDay == null ? "ANY" : timeOfDay);
    request.put("urgencyCategory", urgencyCategory);

    try {
      Map<String, Object> response = scheduling.registerAvailabilityRequest(request);
      LOG.info("[{}] availability request {} for {} {}..{}", patientReference,
          response.get("availabilityRequestId"), department, window.from(), window.to());
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("availabilityRequestId", response.get("availabilityRequestId"));
      out.put("searchFromDate", window.from().toString());
      out.put("searchToDate", window.to().toString());
      return out;
    } catch (RestClientException e) {
      int retriesLeft = job.getRetries() - 1;
      LOG.error("[{}] scheduling system not reachable ({}), retries left {}", patientReference, e.getMessage(),
          retriesLeft);
      throw CamundaError.jobError("Scheduling system not reachable: " + e.getMessage(), null, retriesLeft,
          Duration.ofSeconds(5));
    }
  }
}

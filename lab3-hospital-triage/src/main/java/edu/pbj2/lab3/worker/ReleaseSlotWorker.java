package edu.pbj2.lab3.worker;

import edu.pbj2.lab3.scheduling.SchedulingClient;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.exception.CamundaError;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

/**
 * Service task "Release appointment slot" (job type triage.release-slot): the patient did not confirm in
 * time, so the booking is cancelled and the slot becomes free for other patients. A manually assigned
 * slot (no booking reference in the scheduling system) only needs its status updated.
 */
@Component
public class ReleaseSlotWorker {

  private static final Logger LOG = LoggerFactory.getLogger(ReleaseSlotWorker.class);
  private final SchedulingClient scheduling;

  public ReleaseSlotWorker(SchedulingClient scheduling) {
    this.scheduling = scheduling;
  }

  @JobWorker(type = "triage.release-slot")
  public Map<String, Object> release(ActivatedJob job, @Variable String patientReference,
                                     @Variable String bookingReference) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("patientConfirmation", "NOT_CONFIRMED");
    out.put("bookingStatus", "RELEASED");
    if (bookingReference == null) {
      LOG.info("[{}] no system booking to cancel (manual slot)", patientReference);
      return out;
    }
    try {
      Map<String, Object> response = scheduling.cancel(bookingReference);
      out.put("releasedAt", response.get("releasedAt"));
      LOG.info("[{}] booking {} released", patientReference, bookingReference);
      return out;
    } catch (RestClientException e) {
      throw CamundaError.jobError("Could not release booking " + bookingReference + ": " + e.getMessage(), null,
          job.getRetries() - 1, Duration.ofSeconds(5));
    }
  }
}

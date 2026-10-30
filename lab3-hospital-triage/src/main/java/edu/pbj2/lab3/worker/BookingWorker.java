package edu.pbj2.lab3.worker;

import edu.pbj2.lab3.scheduling.SchedulingClient;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.exception.CamundaError;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

/**
 * Service tasks "Book appointment" and "Book re-queried slot" (job type triage.book-appointment).
 * Tries the retrieved slots in order. When every one of them was taken by someone else in the meantime
 * (HTTP 409) the worker throws BPMN error SLOT_TAKEN, which the process handles with a re-query
 * (first time) or the scheduling staff (second time). A technical failure is retried and becomes an incident.
 */
@Component
public class BookingWorker {

  private static final Logger LOG = LoggerFactory.getLogger(BookingWorker.class);
  private final SchedulingClient scheduling;

  public BookingWorker(SchedulingClient scheduling) {
    this.scheduling = scheduling;
  }

  @JobWorker(type = "triage.book-appointment")
  public Map<String, Object> book(
      ActivatedJob job,
      @Variable String patientReference,
      @Variable String availabilityRequestId,
      @Variable List<Map<String, Object>> availableSlots) {
    int conflicts = 0;
    try {
      for (Map<String, Object> slot : availableSlots) {
        try {
          Map<String, Object> booking = scheduling.book(Map.of(
              "availabilityRequestId", availabilityRequestId,
              "slotId", slot.get("slotId"),
              "patientReference", patientReference));
          @SuppressWarnings("unchecked")
          Map<String, Object> booked = (Map<String, Object>) booking.get("slot");
          Map<String, Object> out = new LinkedHashMap<>();
          out.put("bookingStatus", booking.get("status"));
          out.put("bookingReference", booking.get("bookingReference"));
          out.put("bookingSite", booked.get("site"));
          out.put("appointmentSlot", "%s %s - %s, %s, %s".formatted(booked.get("date"), booked.get("time"),
              booked.get("department"), booked.get("clinician"), booked.get("location")));
          out.put("appointmentDetails", booked);
          out.put("slotConflicts", conflicts);
          LOG.info("[{}] booked {}", patientReference, out);
          return out;
        } catch (HttpClientErrorException.Conflict taken) {
          conflicts++;
          LOG.warn("[{}] slot {} already taken, trying next", patientReference, slot.get("slotId"));
        }
      }
    } catch (RestClientException e) {
      throw CamundaError.jobError("Booking failed: " + e.getMessage(), null, job.getRetries() - 1,
          Duration.ofSeconds(5));
    }
    LOG.warn("[{}] all {} retrieved slots were taken -> BPMN error SLOT_TAKEN", patientReference, conflicts);
    throw CamundaError.bpmnError("SLOT_TAKEN",
        "All " + conflicts + " retrieved slots were taken by other patients before booking",
        Map.of("slotConflicts", conflicts));
  }
}

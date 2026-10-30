package edu.pbj2.lab3.worker;

import io.camunda.client.annotation.CustomHeaders;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Send tasks. The message template comes from the task header "template" in the BPMN, so one
 * worker per audience serves every notification. Messages are logged instead of sent (synthetic data).
 */
@Component
public class NotificationWorker {

  private static final Logger LOG = LoggerFactory.getLogger(NotificationWorker.class);

  @JobWorker(type = "triage.notify-patient")
  public Map<String, Object> notifyPatient(
      @CustomHeaders Map<String, String> headers,
      @Variable String patientReference,
      @Variable String appointmentSlot,
      @Variable String bookingReference,
      @Variable List<String> registrationErrors,
      @Variable String confirmationTimeout) {
    String template = headers.getOrDefault("template", "GENERIC");
    String message = switch (template) {
      case "APPOINTMENT_CONFIRMED" -> "Your appointment is confirmed: " + appointmentSlot
          + (bookingReference == null ? "" : " (booking " + bookingReference + ")");
      case "MISSING_DETAILS" -> "We could not register your appointment request. Please provide: " + registrationErrors;
      case "WAITLISTED" -> "No suitable appointment is available right now. You have been added to the waiting list.";
      case "CONFIRMATION_REQUEST" -> "Please confirm your appointment " + appointmentSlot
          + " within " + confirmationTimeout + ", otherwise the slot is released for other patients.";
      case "CONFIRMATION_REMINDER" -> "Reminder: please confirm your appointment " + appointmentSlot + ".";
      case "SLOT_RELEASED" -> "Your appointment " + appointmentSlot
          + " was not confirmed in time and has been released. Please contact us to book again.";
      default -> "Update on your appointment request";
    };
    LOG.info("[{}] PATIENT MESSAGE ({}): {}", patientReference, template, message);
    return Map.of("patientNotification", notification(template, patientReference, message));
  }

  @JobWorker(type = "triage.notify-staff")
  public Map<String, Object> notifyStaff(
      @CustomHeaders Map<String, String> headers,
      @Variable String patientReference,
      @Variable String triageReason,
      @Variable String urgentEscalationAfter) {
    String template = headers.getOrDefault("template", "GENERIC");
    String recipient;
    String message;
    switch (template) {
      case "ED_HANDOVER" -> {
        recipient = "emergency-department";
        message = "Hand-over: patient " + patientReference + " referred after immediate assessment (" + triageReason + ")";
      }
      case "DUTY_MANAGER_ESCALATION" -> {
        recipient = "duty-manager";
        message = "Urgent assessment for patient " + patientReference + " not picked up within " + urgentEscalationAfter;
      }
      default -> {
        recipient = "clinical-staff";
        message = "Update for patient " + patientReference;
      }
    }
    LOG.info("[{}] STAFF MESSAGE to {} ({}): {}", patientReference, recipient, template, message);
    return Map.of("staffNotification", notification(template, recipient, message));
  }

  private static Map<String, Object> notification(String template, String to, String message) {
    Map<String, Object> n = new LinkedHashMap<>();
    n.put("template", template);
    n.put("to", to);
    n.put("message", message);
    n.put("sentAt", OffsetDateTime.now().toString());
    return n;
  }
}

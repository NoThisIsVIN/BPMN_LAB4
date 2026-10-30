package edu.pbj2.lab1.worker;

import io.camunda.client.annotation.CustomHeaders;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Send tasks. The message template comes from the task header "template" in the BPMN, so one
 * worker serves every notification. Messages are logged instead of e-mailed (synthetic data).
 */
@Component
public class NotificationWorker {

  private static final Logger LOG = LoggerFactory.getLogger(NotificationWorker.class);

  @JobWorker(type = "loan.notify-applicant")
  public Map<String, Object> notifyApplicant(
      @CustomHeaders Map<String, String> headers,
      @Variable String applicationId,
      @Variable String email,
      @Variable(name = "decisionReason") String decisionReason) {
    String template = headers.getOrDefault("template", "GENERIC");
    String message = switch (template) {
      case "LOAN_APPROVED" -> "Your loan application " + applicationId + " has been approved. Your offer is ready.";
      case "LOAN_REJECTED" -> "Your loan application " + applicationId + " was declined. Reason: " + decisionReason;
      case "APPLICATION_INCOMPLETE" -> "Your loan application " + applicationId
          + " is incomplete or invalid. Please correct it and apply again.";
      default -> "Update on loan application " + applicationId;
    };
    LOG.info("[{}] EMAIL to {} ({}): {}", applicationId, email, template, message);
    return Map.of("applicantNotification", notification(template, email, message));
  }

  @JobWorker(type = "loan.notify-officers")
  public Map<String, Object> remindOfficers(@CustomHeaders Map<String, String> headers, @Variable String applicationId) {
    String message = "Reminder: loan application " + applicationId + " has been waiting for review for more than 24 hours.";
    LOG.info("[{}] TEAM MESSAGE to loan-officers: {}", applicationId, message);
    return Map.of("officerReminder", notification(headers.getOrDefault("template", "REVIEW_OVERDUE"), "loan-officers", message));
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

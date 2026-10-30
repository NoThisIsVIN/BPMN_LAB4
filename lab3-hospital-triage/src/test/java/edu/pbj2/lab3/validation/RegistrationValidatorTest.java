package edu.pbj2.lab3.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RegistrationValidatorTest {

  private final RegistrationValidator validator = new RegistrationValidator();

  private static Map<String, Object> validRequest() {
    Map<String, Object> v = new HashMap<>();
    v.put("patientReference", "SYN-P-0001");
    v.put("patientAge", 42);
    v.put("appointmentPreference", Map.of(
        "department", "GENERAL_PRACTICE", "preferredDate", "2026-11-03", "timeOfDay", "MORNING"));
    v.put("symptomsSummary", "Mild seasonal cough for a few days");
    v.put("painLevel", 2);
    v.put("symptomDurationDays", 4);
    v.put("highFever", false);
    return v;
  }

  @Test
  void validRequestIsFlattenedAndFlagsDefaultToFalse() {
    Map<String, Object> out = validator.validate(validRequest());

    assertThat(out.get("registrationValid")).isEqualTo(true);
    assertThat((List<?>) out.get("registrationErrors")).isEmpty();
    assertThat(out).containsEntry("department", "GENERAL_PRACTICE")
        .containsEntry("timeOfDay", "MORNING")
        .containsEntry("preferredDate", "2026-11-03")
        .containsEntry("chestPain", false)
        .containsEntry("breathingDifficulty", false)
        .containsEntry("highFever", false);
  }

  @Test
  void collectsAllRegistrationErrors() {
    Map<String, Object> v = validRequest();
    v.remove("patientReference");
    v.put("painLevel", 12);
    v.put("appointmentPreference", Map.of("department", "NEUROSURGERY"));

    Map<String, Object> out = validator.validate(v);

    assertThat(out.get("registrationValid")).isEqualTo(false);
    assertThat((List<String>) out.get("registrationErrors")).contains(
        "patientReference is required",
        "painLevel must be between 0 and 10")
        .anyMatch(e -> e.startsWith("appointmentPreference.department must be one of"));
  }

  @Test
  void rejectsNonSyntheticPatientReference() {
    Map<String, Object> v = validRequest();
    v.put("patientReference", "NHS-123-456");

    assertThat((List<String>) validator.validate(v).get("registrationErrors"))
        .containsExactly("patientReference must be a synthetic reference like SYN-P-0001");
  }

  @Test
  void nonBooleanUrgencyFlagIsAMalformedVariable() {
    Map<String, Object> v = validRequest();
    v.put("chestPain", "yes");

    assertThatThrownBy(() -> validator.validate(v))
        .isInstanceOf(MalformedVariableException.class)
        .hasMessageContaining("chestPain");
  }

  @Test
  void timerDurationsDefaultToBusinessValuesAndCanBeOverridden() {
    Map<String, Object> v = validRequest();
    v.put("confirmationTimeout", "PT10S");

    Map<String, Object> out = validator.validate(v);

    assertThat(out).containsEntry("confirmationTimeout", "PT10S")
        .containsEntry("confirmationReminderAfter", "PT12H")
        .containsEntry("urgentEscalationAfter", "PT15M");
  }

  @Test
  void invalidTimerDurationIsARegistrationError() {
    Map<String, Object> v = validRequest();
    v.put("urgentEscalationAfter", "15 minutes");

    Map<String, Object> out = validator.validate(v);

    assertThat(out.get("registrationValid")).isEqualTo(false);
    assertThat((List<String>) out.get("registrationErrors"))
        .containsExactly("urgentEscalationAfter must be an ISO-8601 duration such as PT15M");
  }
}

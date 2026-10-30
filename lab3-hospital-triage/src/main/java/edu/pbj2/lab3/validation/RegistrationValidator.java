package edu.pbj2.lab3.validation;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Pure validation of an appointment request. No Camunda types here, so the rules are
 * unit-testable on their own. Synthetic patient references only (SYN-P-####).
 */
public class RegistrationValidator {

  public static final Set<String> DEPARTMENTS =
      Set.of("GENERAL_PRACTICE", "CARDIOLOGY", "ORTHOPAEDICS", "RESPIRATORY", "DERMATOLOGY");
  static final Set<String> TIMES_OF_DAY = Set.of("MORNING", "AFTERNOON", "ANY");
  private static final Pattern PATIENT_REFERENCE = Pattern.compile("^SYN-P-\\d{4}$");
  private static final List<String> URGENCY_FLAGS =
      List.of("chestPain", "breathingDifficulty", "highFever", "chronicCondition");

  /** @return process variables to merge into the instance */
  public Map<String, Object> validate(Map<String, Object> v) {
    List<String> errors = new ArrayList<>();
    Map<String, Object> out = new LinkedHashMap<>();

    Object ref = v.get("patientReference");
    if (ref == null || ref.toString().isBlank()) {
      errors.add("patientReference is required");
    } else if (!PATIENT_REFERENCE.matcher(ref.toString()).matches()) {
      errors.add("patientReference must be a synthetic reference like SYN-P-0001");
    }

    Double age = number(v, "patientAge");
    if (age == null) {
      errors.add("patientAge is required");
    } else if (age < 0 || age > 120) {
      errors.add("patientAge must be between 0 and 120");
    }

    Object summary = v.get("symptomsSummary");
    if (summary == null || summary.toString().trim().length() < 5) {
      errors.add("symptomsSummary is required (at least 5 characters)");
    }

    Double pain = number(v, "painLevel");
    if (pain == null) {
      errors.add("painLevel is required");
    } else if (pain < 0 || pain > 10) {
      errors.add("painLevel must be between 0 and 10");
    }

    Double duration = number(v, "symptomDurationDays");
    if (duration == null) {
      errors.add("symptomDurationDays is required");
    } else if (duration < 0) {
      errors.add("symptomDurationDays cannot be negative");
    }

    // Urgency indicators: missing means "not reported" (false); anything other than a boolean is malformed.
    for (String flag : URGENCY_FLAGS) {
      Object value = v.get(flag);
      if (value == null) {
        out.put(flag, false);
      } else if (value instanceof Boolean b) {
        out.put(flag, b);
      } else {
        throw new MalformedVariableException("Variable '" + flag + "' must be true/false but was \"" + value
            + "\". Correct the variable in Operate and retry the job.");
      }
    }

    Object pref = v.get("appointmentPreference");
    if (!(pref instanceof Map<?, ?> preference)) {
      errors.add("appointmentPreference {department, preferredDate, timeOfDay} is required");
    } else {
      Object department = preference.get("department");
      if (department == null || !DEPARTMENTS.contains(department.toString())) {
        errors.add("appointmentPreference.department must be one of " + DEPARTMENTS);
      } else {
        out.put("department", department.toString());
      }
      Object timeOfDay = preference.get("timeOfDay");
      String tod = timeOfDay == null ? "ANY" : timeOfDay.toString();
      if (!TIMES_OF_DAY.contains(tod)) {
        errors.add("appointmentPreference.timeOfDay must be one of " + TIMES_OF_DAY);
      } else {
        out.put("timeOfDay", tod);
      }
      Object preferred = preference.get("preferredDate");
      if (preferred != null) {
        try {
          out.put("preferredDate", LocalDate.parse(preferred.toString()).toString());
        } catch (DateTimeParseException e) {
          errors.add("appointmentPreference.preferredDate must be an ISO date (yyyy-MM-dd)");
        }
      }
    }

    out.put("registrationValid", errors.isEmpty());
    out.put("registrationErrors", errors);
    return out;
  }

  /** Missing -> null (a validation error); present but not numeric -> MalformedVariableException. */
  private static Double number(Map<String, Object> v, String key) {
    Object value = v.get(key);
    if (value == null) {
      return null;
    }
    if (value instanceof Number n) {
      return n.doubleValue();
    }
    throw new MalformedVariableException(
        "Variable '" + key + "' must be numeric but was the " + value.getClass().getSimpleName()
            + " \"" + value + "\". Correct the variable in Operate and retry the job.");
  }
}

package edu.pbj2.lab1.validation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Pure validation + affordability calculation for a loan application. No Camunda types here,
 * so the rules are unit-testable on their own.
 *
 * <p>Two different outcomes, because they need different handling in the process:
 * <ul>
 *   <li><b>complete</b> – every field is present and well-formed. Missing / malformed fields can be fixed by the
 *       applicant, so the process asks for the missing information and validates again.</li>
 *   <li><b>eligible</b> – the (complete) application meets the product limits (age, amount, term, income).
 *       Not being eligible cannot be fixed by resubmitting, so it goes straight to the rejection notice.</li>
 * </ul>
 */
public class ApplicantValidator {

  static final Set<String> EMPLOYMENT_STATUSES =
      Set.of("EMPLOYED", "SELF_EMPLOYED", "UNEMPLOYED", "RETIRED", "STUDENT");
  private static final Pattern EMAIL = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");
  private static final Pattern APPLICANT_ID = Pattern.compile("^APP-[A-Z0-9-]+$");

  private final double referenceApr;

  public ApplicantValidator(double referenceApr) {
    this.referenceApr = referenceApr;
  }

  /** @return process variables to merge into the instance */
  public Map<String, Object> validate(Map<String, Object> v) {
    // ---- completeness (correctable by the applicant) ----
    List<String> errors = new ArrayList<>();
    requireText(v, "applicationId", errors);
    String applicantId = requireText(v, "applicantId", errors);
    if (applicantId != null && !APPLICANT_ID.matcher(applicantId).matches()) {
      errors.add("applicantId must look like APP-1234");
    }
    requireText(v, "fullName", errors);
    String email = requireText(v, "email", errors);
    if (email != null && !EMAIL.matcher(email).matches()) {
      errors.add("email is not a valid address");
    }
    requireText(v, "loanPurpose", errors);
    String employment = requireText(v, "employmentStatus", errors);
    if (employment != null && !EMPLOYMENT_STATUSES.contains(employment)) {
      errors.add("employmentStatus must be one of " + EMPLOYMENT_STATUSES);
    }
    Double age = requireNumber(v, "age", errors);
    Double income = requireNumber(v, "monthlyIncome", errors);
    Double amount = requireNumber(v, "loanAmount", errors);
    Double term = requireNumber(v, "loanTermMonths", errors);
    Double debt = number(v, "monthlyDebtPayments");
    if (debt == null) {
      debt = 0.0;
    } else if (debt < 0) {
      errors.add("monthlyDebtPayments cannot be negative");
    }

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("applicationComplete", errors.isEmpty());
    out.put("validationErrors", errors);
    if (!errors.isEmpty()) {
      out.put("applicantEligible", false);
      out.put("eligibilityIssues", List.of());
      return out;
    }

    // ---- eligibility (product limits, not correctable) ----
    List<String> issues = new ArrayList<>();
    if (age < 18 || age > 75) {
      issues.add("age must be between 18 and 75 (was " + age.intValue() + ")");
    }
    if (income <= 0) {
      issues.add("monthly income must be greater than 0");
    }
    if (amount < 1_000 || amount > 500_000) {
      issues.add("loan amount must be between 1 000 and 500 000");
    }
    if (term < 6 || term > 360) {
      issues.add("loan term must be between 6 and 360 months");
    }
    out.put("applicantEligible", issues.isEmpty());
    out.put("eligibilityIssues", issues);
    if (!issues.isEmpty()) {
      out.put("decisionReason", "Not eligible: " + String.join("; ", issues));
      out.put("decisionSource", "ELIGIBILITY CHECK");
      return out;
    }

    double installment = annuity(amount, referenceApr, term.intValue());
    out.put("monthlyInstallment", round(installment, 2));
    out.put("debtToIncomeRatio", round((debt + installment) / income, 4));
    out.put("loanToIncomeRatio", round(amount / (income * 12), 4));
    out.put("monthlyDebtPayments", debt);
    return out;
  }

  static double annuity(double principal, double apr, int months) {
    double r = apr / 12;
    return principal * r / (1 - Math.pow(1 + r, -months));
  }

  private static double round(double value, int places) {
    return BigDecimal.valueOf(value).setScale(places, RoundingMode.HALF_UP).doubleValue();
  }

  private static String requireText(Map<String, Object> v, String key, List<String> errors) {
    Object value = v.get(key);
    if (value == null || value.toString().isBlank()) {
      errors.add(key + " is required");
      return null;
    }
    return value.toString().trim();
  }

  private static Double requireNumber(Map<String, Object> v, String key, List<String> errors) {
    Double n = number(v, key);
    if (n == null) {
      errors.add(key + " is required");
    }
    return n;
  }

  /** Missing -> null; present but not numeric -> MalformedVariableException (an operator must fix it). */
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

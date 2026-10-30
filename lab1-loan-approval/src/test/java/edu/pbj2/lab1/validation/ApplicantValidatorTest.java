package edu.pbj2.lab1.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ApplicantValidatorTest {

  private final ApplicantValidator validator = new ApplicantValidator(0.09);

  private static Map<String, Object> validApplication() {
    Map<String, Object> v = new HashMap<>();
    v.put("applicationId", "LA-TEST-1");
    v.put("applicantId", "APP-1001");
    v.put("fullName", "Test Applicant");
    v.put("email", "test.applicant@example.org");
    v.put("age", 34);
    v.put("employmentStatus", "EMPLOYED");
    v.put("monthlyIncome", 6200);
    v.put("monthlyDebtPayments", 400);
    v.put("loanAmount", 25000);
    v.put("loanTermMonths", 60);
    v.put("loanPurpose", "Home renovation");
    return v;
  }

  @Test
  void validApplicationPassesAndComputesRatios() {
    Map<String, Object> out = validator.validate(validApplication());

    assertThat(out.get("validationPassed")).isEqualTo(true);
    assertThat((List<?>) out.get("validationErrors")).isEmpty();
    // 25 000 over 60 months at 9% APR -> 518.96 per month
    assertThat(out.get("monthlyInstallment")).isEqualTo(518.96);
    assertThat((double) out.get("debtToIncomeRatio")).isEqualTo(0.1482);
    assertThat((double) out.get("loanToIncomeRatio")).isEqualTo(0.336);
  }

  @Test
  void collectsAllValidationErrors() {
    Map<String, Object> v = validApplication();
    v.remove("email");
    v.put("age", 16);
    v.put("loanAmount", 500);

    Map<String, Object> out = validator.validate(v);

    assertThat(out.get("validationPassed")).isEqualTo(false);
    assertThat((List<String>) out.get("validationErrors")).containsExactlyInAnyOrder(
        "email is required",
        "age must be between 18 and 75 (was 16)",
        "loanAmount must be between 1 000 and 500 000");
    assertThat(out).doesNotContainKey("debtToIncomeRatio");
  }

  @Test
  void nonNumericIncomeIsAMalformedVariable() {
    Map<String, Object> v = validApplication();
    v.put("monthlyIncome", "five thousand");

    assertThatThrownBy(() -> validator.validate(v))
        .isInstanceOf(MalformedVariableException.class)
        .hasMessageContaining("monthlyIncome");
  }
}

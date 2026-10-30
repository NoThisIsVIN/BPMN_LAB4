package edu.pbj2.lab1.worker;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Service task "Generate loan offer" (job type loan.generate-offer). */
@Component
public class LoanOfferWorker {

  private static final Logger LOG = LoggerFactory.getLogger(LoanOfferWorker.class);

  /** Educational pricing grid: annual interest rate per credit risk band. */
  static double rateFor(String riskBand) {
    if (riskBand == null) {
      return 0.109; // credit bureau unavailable, officer approved manually
    }
    return switch (riskBand) {
      case "A" -> 0.065;
      case "B" -> 0.079;
      case "C" -> 0.099;
      default -> 0.119;
    };
  }

  @JobWorker(type = "loan.generate-offer")
  public Map<String, Object> generateOffer(
      @Variable String applicationId,
      @Variable Double loanAmount,
      @Variable Integer loanTermMonths,
      @Variable(name = "creditRiskBand") String creditRiskBand) {
    double rate = rateFor(creditRiskBand);
    double r = rate / 12;
    double installment = loanAmount * r / (1 - Math.pow(1 + r, -loanTermMonths));

    Map<String, Object> offer = new LinkedHashMap<>();
    offer.put("offerId", "OFF-" + applicationId);
    offer.put("approvedAmount", loanAmount);
    offer.put("interestRate", rate);
    offer.put("offerMonthlyInstallment",
        BigDecimal.valueOf(installment).setScale(2, RoundingMode.HALF_UP).doubleValue());
    offer.put("offerValidUntil", LocalDate.now().plusDays(30).toString());
    LOG.info("[{}] offer generated: {}", applicationId, offer);
    return offer;
  }
}

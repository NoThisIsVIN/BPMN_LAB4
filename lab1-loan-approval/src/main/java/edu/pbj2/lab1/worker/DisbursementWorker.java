package edu.pbj2.lab1.worker;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Service task "Disburse loan" (job type loan.disburse) – a synthetic payout, no real money moves. */
@Component
public class DisbursementWorker {

  private static final Logger LOG = LoggerFactory.getLogger(DisbursementWorker.class);

  @JobWorker(type = "loan.disburse")
  public Map<String, Object> disburse(@Variable String applicationId, @Variable Double approvedAmount,
      @Variable String offerId) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("disbursementReference", "DSB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    out.put("disbursedAmount", approvedAmount);
    out.put("disbursedAt", OffsetDateTime.now().toString());
    LOG.info("[{}] offer {} accepted, disbursed {}", applicationId, offerId, out);
    return out;
  }
}

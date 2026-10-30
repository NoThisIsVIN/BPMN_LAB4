package edu.pbj2.lab2.worker;

import edu.pbj2.lab2.domain.OrderPricing;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Service task "Calculate order total" (job type order.calculate-total). */
@Component
public class PricingWorker {

  private static final Logger LOG = LoggerFactory.getLogger(PricingWorker.class);

  @JobWorker(type = "order.calculate-total")
  public Map<String, Object> calculate(@Variable String orderId, @Variable Double unitPrice,
      @Variable Integer quantity, @Variable String customerCategory, @Variable String deliveryPreference) {
    Map<String, Object> pricing = OrderPricing.calculate(unitPrice, quantity, customerCategory, deliveryPreference);
    LOG.info("[{}] pricing: {}", orderId, pricing);
    return pricing;
  }
}

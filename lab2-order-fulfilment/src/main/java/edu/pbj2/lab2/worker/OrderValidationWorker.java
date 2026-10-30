package edu.pbj2.lab2.worker;

import edu.pbj2.lab2.domain.CustomerRegistry;
import edu.pbj2.lab2.domain.InventoryService;
import edu.pbj2.lab2.domain.OrderValidator;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Service task "Validate order" (job type order.validate). */
@Component
public class OrderValidationWorker {

  private static final Logger LOG = LoggerFactory.getLogger(OrderValidationWorker.class);
  private final OrderValidator validator;

  public OrderValidationWorker(CustomerRegistry customers, InventoryService inventory) {
    this.validator = new OrderValidator(customers, inventory);
  }

  @JobWorker(type = "order.validate")
  public Map<String, Object> validate(ActivatedJob job) {
    Map<String, Object> vars = job.getVariablesAsMap();
    Map<String, Object> result = validator.validate(vars);
    LOG.info("[{}] order valid={} errors={}", vars.get("orderId"), result.get("orderValid"), result.get("validationErrors"));
    return result;
  }
}

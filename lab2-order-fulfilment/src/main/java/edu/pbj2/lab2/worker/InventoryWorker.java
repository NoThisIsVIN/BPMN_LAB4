package edu.pbj2.lab2.worker;

import edu.pbj2.lab2.domain.InventoryService;
import edu.pbj2.lab2.faults.FaultInjection;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Service tasks backed by the simulated inventory service:
 * "Check inventory &amp; reserve stock" (order.check-inventory) and "Release reserved stock" (order.release-stock).
 */
@Component
public class InventoryWorker {

  private static final Logger LOG = LoggerFactory.getLogger(InventoryWorker.class);
  private static final Duration RETRY_BACKOFF = Duration.ofSeconds(5);

  private final InventoryService inventory;
  private final FaultInjection faults;

  public InventoryWorker(InventoryService inventory, FaultInjection faults) {
    this.inventory = inventory;
    this.faults = faults;
  }

  /**
   * Completed/failed explicitly (autoComplete = false) so that the incident shown in Operate carries a
   * clean, operator-readable error message instead of a Java stack trace.
   */
  @JobWorker(type = "order.check-inventory", autoComplete = false)
  public void checkAndReserve(JobClient client, ActivatedJob job, @Variable String orderId,
      @Variable String productId, @Variable Integer quantity) {
    if (faults.isActive(FaultInjection.INVENTORY_SERVICE_DOWN)) {
      // Transient outage: Zeebe retries with back-off; when retries reach 0 an incident is raised.
      int retriesLeft = job.getRetries() - 1;
      LOG.error("[{}] inventory service unavailable, failing job (retries left: {})", orderId, retriesLeft);
      client.newFailCommand(job.getKey())
          .retries(retriesLeft)
          .retryBackoff(RETRY_BACKOFF)
          .errorMessage("Inventory service unavailable (HTTP 503 from inventory-api). "
              + "Fix: restore the service (POST /admin/faults/inventory-service-down?enabled=false) and retry.")
          .send()
          .join();
      return;
    }

    Map<String, Object> out = new LinkedHashMap<>();
    var product = inventory.product(productId).orElseThrow();
    out.put("warehouse", product.warehouse());
    inventory.reserve(productId, quantity).ifPresentOrElse(r -> {
      out.put("stockStatus", "AVAILABLE");
      out.put("reservationId", r.id());
      out.put("reservationStatus", r.status());
      out.put("availableQuantity", inventory.available(productId) + quantity);
    }, () -> {
      out.put("stockStatus", "INSUFFICIENT");
      out.put("availableQuantity", inventory.available(productId));
    });
    LOG.info("[{}] inventory check {} x{}: {}", orderId, productId, quantity, out);
    client.newCompleteCommand(job.getKey()).variables(out).send().join();
  }

  @JobWorker(type = "order.release-stock")
  public Map<String, Object> release(@Variable String orderId, @Variable String reservationId) {
    String status = inventory.release(reservationId).map(r -> r.status()).orElse("NOT_FOUND");
    LOG.info("[{}] reservation {} -> {}", orderId, reservationId, status);
    return Map.of("reservationStatus", status);
  }
}

package edu.pbj2.lab2.worker;

import edu.pbj2.lab2.domain.InventoryService;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Service task "Create fulfilment request" (job type order.create-shipment). */
@Component
public class ShipmentWorker {

  private static final Logger LOG = LoggerFactory.getLogger(ShipmentWorker.class);
  private final InventoryService inventory;

  public ShipmentWorker(InventoryService inventory) {
    this.inventory = inventory;
  }

  static String carrierFor(String priority) {
    return switch (priority == null ? "" : priority) {
      case "CRITICAL" -> "COURIER-SAME-DAY";
      case "HIGH" -> "EXPRESS-PARCEL";
      default -> "STANDARD-PARCEL";
    };
  }

  @JobWorker(type = "order.create-shipment")
  public Map<String, Object> createShipment(@Variable String orderId, @Variable String reservationId,
      @Variable String deliveryPriority, @Variable Integer slaHours, @Variable String warehouse) {
    inventory.commit(reservationId);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("shipmentId", "SHP-" + orderId);
    out.put("trackingNumber", "TRK" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase());
    out.put("carrier", carrierFor(deliveryPriority));
    out.put("dispatchWarehouse", warehouse);
    out.put("estimatedDelivery", OffsetDateTime.now().plusHours(slaHours == null ? 96 : slaHours)
        .truncatedTo(ChronoUnit.MINUTES).toString());
    out.put("reservationStatus", "COMMITTED");
    LOG.info("[{}] fulfilment request created: {}", orderId, out);
    return out;
  }
}

package edu.pbj2.lab2.worker;

import edu.pbj2.lab2.domain.InventoryService;
import edu.pbj2.lab2.faults.FaultInjection;
import io.camunda.client.annotation.CustomHeaders;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.exception.CamundaError;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Service tasks "Book shipment – primary carrier" and "Book shipment – backup carrier"
 * (job type order.book-shipment, task header {@code carrier} = PRIMARY | BACKUP).
 */
@Component
public class ShipmentWorker {

  private static final Logger LOG = LoggerFactory.getLogger(ShipmentWorker.class);
  private final InventoryService inventory;
  private final FaultInjection faults;

  public ShipmentWorker(InventoryService inventory, FaultInjection faults) {
    this.inventory = inventory;
    this.faults = faults;
  }

  /** Service level of the primary carrier contract, chosen from the DMN delivery priority. */
  static String primaryServiceFor(String priority) {
    return switch (priority == null ? "" : priority) {
      case "CRITICAL" -> "COURIER-SAME-DAY";
      case "HIGH" -> "EXPRESS-PARCEL";
      default -> "STANDARD-PARCEL";
    };
  }

  @JobWorker(type = "order.book-shipment")
  public Map<String, Object> bookShipment(@CustomHeaders Map<String, String> headers, @Variable String orderId,
      @Variable String reservationId, @Variable String deliveryPriority, @Variable Integer slaHours,
      @Variable String warehouse) {
    String carrierRole = headers.getOrDefault("carrier", "PRIMARY");

    if ("PRIMARY".equals(carrierRole) && faults.isActive(FaultInjection.PRIMARY_CARRIER_DOWN)) {
      // A known business situation with a modelled alternative: throw a BPMN error, the error boundary
      // routes the order to the backup carrier instead of raising an incident.
      LOG.warn("[{}] primary carrier booking API unavailable -> BPMN error CARRIER_UNAVAILABLE", orderId);
      throw CamundaError.bpmnError("CARRIER_UNAVAILABLE", "Primary carrier booking API unavailable (HTTP 503)",
          Map.of("primaryCarrierError", "Primary carrier booking API unavailable (HTTP 503)"));
    }

    inventory.commit(reservationId);
    boolean primary = "PRIMARY".equals(carrierRole);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("shipmentId", "SHP-" + orderId);
    out.put("trackingNumber", (primary ? "TRK" : "BKP")
        + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase());
    out.put("carrierUsed", carrierRole);
    out.put("carrier", primary ? "PRIMARY-CARRIER " + primaryServiceFor(deliveryPriority) : "BACKUP-CARRIER PRIORITY");
    out.put("dispatchWarehouse", warehouse);
    out.put("estimatedDelivery", OffsetDateTime.now().plusHours(slaHours == null ? 96 : slaHours)
        .truncatedTo(ChronoUnit.MINUTES).toString());
    out.put("reservationStatus", "COMMITTED");
    LOG.info("[{}] shipment booked with {} carrier: {}", orderId, carrierRole, out);
    return out;
  }
}

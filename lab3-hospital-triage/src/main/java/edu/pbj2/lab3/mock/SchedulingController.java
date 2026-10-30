package edu.pbj2.lab3.mock;

import edu.pbj2.lab3.faults.FaultInjection;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mock hospital scheduling API (synthetic data only).
 *
 * <pre>
 * POST /mock/scheduling/availability-requests              register a search  -> {availabilityRequestId, ...}
 * GET  /mock/scheduling/availability-requests/{id}/slots   free slots (max 5) -> called by the REST Connector
 * POST /mock/scheduling/bookings                           book a slot        -> {bookingReference, status, slot}
 * </pre>
 * Fault {@code scheduling-api-down} makes the slot query answer HTTP 503.
 */
@RestController
@RequestMapping("/mock/scheduling")
public class SchedulingController {

  private static final Logger LOG = LoggerFactory.getLogger(SchedulingController.class);
  private static final int MAX_SLOTS_RETURNED = 5;

  private final FaultInjection faults;
  private final SlotCalendar calendar;
  private final Map<String, Map<String, Object>> requests = new ConcurrentHashMap<>();

  public SchedulingController(FaultInjection faults, SlotCalendar calendar) {
    this.faults = faults;
    this.calendar = calendar;
  }

  @PostMapping("/availability-requests")
  public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, Object> body) {
    for (String required : List.of("patientReference", "department", "fromDate", "toDate")) {
      if (body.get(required) == null) {
        return error(HttpStatus.BAD_REQUEST, required + " is required");
      }
    }
    String id = "AR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    Map<String, Object> request = new LinkedHashMap<>(body);
    request.put("availabilityRequestId", id);
    request.put("createdAt", OffsetDateTime.now().toString());
    requests.put(id, request);
    LOG.info("Availability request registered: {}", request);
    return ResponseEntity.status(HttpStatus.CREATED).body(request);
  }

  @GetMapping("/availability-requests/{id}/slots")
  public ResponseEntity<Map<String, Object>> slots(@PathVariable String id) {
    if (faults.isActive(FaultInjection.SCHEDULING_API_DOWN)) {
      return error(HttpStatus.SERVICE_UNAVAILABLE, "Scheduling service temporarily unavailable");
    }
    Map<String, Object> request = requests.get(id);
    if (request == null) {
      return error(HttpStatus.NOT_FOUND, "Unknown availability request " + id);
    }
    List<Map<String, Object>> free = calendar.freeSlots(
            String.valueOf(request.get("department")),
            LocalDate.parse(String.valueOf(request.get("fromDate"))),
            LocalDate.parse(String.valueOf(request.get("toDate"))),
            (String) request.get("timeOfDay"),
            MAX_SLOTS_RETURNED)
        .stream().map(SlotCalendar.Slot::toMap).toList();
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("availabilityRequestId", id);
    response.put("department", request.get("department"));
    response.put("fromDate", request.get("fromDate"));
    response.put("toDate", request.get("toDate"));
    response.put("timeOfDay", request.get("timeOfDay"));
    response.put("slots", free);
    LOG.info("Slots for {}: {} found", id, free.size());
    return ResponseEntity.ok(response);
  }

  @PostMapping("/bookings")
  public ResponseEntity<Map<String, Object>> book(@RequestBody Map<String, Object> body) {
    String slotId = String.valueOf(body.get("slotId"));
    String patientReference = String.valueOf(body.get("patientReference"));
    var slot = calendar.find(slotId);
    if (slot.isEmpty()) {
      return error(HttpStatus.NOT_FOUND, "Unknown slot " + slotId);
    }
    if (!calendar.book(slotId, patientReference)) {
      return error(HttpStatus.CONFLICT, "Slot " + slotId + " is already booked");
    }
    Map<String, Object> booking = new LinkedHashMap<>();
    booking.put("bookingReference", "BK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    booking.put("status", "CONFIRMED");
    booking.put("patientReference", patientReference);
    booking.put("availabilityRequestId", body.get("availabilityRequestId"));
    booking.put("slot", slot.get().toMap());
    booking.put("bookedAt", OffsetDateTime.now().toString());
    LOG.info("Booking confirmed: {}", booking);
    return ResponseEntity.status(HttpStatus.CREATED).body(booking);
  }

  private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
    LOG.warn("Scheduling API responding {}: {}", status.value(), message);
    return ResponseEntity.status(status).body(Map.of("error", message, "status", status.value()));
  }
}

package edu.pbj2.lab3.mock;

import edu.pbj2.lab3.faults.FaultInjection;
import edu.pbj2.lab3.scheduling.SearchWindow;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mock hospital scheduling API (synthetic data only).
 *
 * <pre>
 * POST /mock/scheduling/availability-requests                         register a search -> {availabilityRequestId,
 *                                                                      fromDate, toDate, ...}
 * GET  /mock/scheduling/availability-requests/{id}/slots?site=SITE    free slots (max 5), SITE = MAIN_HOSPITAL (default)
 *                                                                      or PARTNER_CLINIC
 * POST /mock/scheduling/bookings                                      book the first free of the given slots
 *                                                                      -> {bookingReference, status, slot, conflicts}
 * POST /mock/scheduling/bookings/{bookingReference}/cancellation      release an unconfirmed booking
 * </pre>
 * Every endpoint is called by a REST Outbound Connector task in the BPMN; no Java worker calls this API.
 * Fault {@code scheduling-api-down} makes every slot query answer HTTP 503. Fault {@code booking-conflict-once}
 * lets "another patient" take each slot we try to book (HTTP 409) until the next slot query.
 */
@RestController
@RequestMapping("/mock/scheduling")
public class SchedulingController {

  private static final Logger LOG = LoggerFactory.getLogger(SchedulingController.class);
  private static final int MAX_SLOTS_RETURNED = 5;
  private static final String OTHER_PATIENT = "SYN-P-OTHER";

  private final FaultInjection faults;
  private final SlotCalendar calendar;
  private final Map<String, Map<String, Object>> requests = new ConcurrentHashMap<>();
  private final Map<String, String> bookings = new ConcurrentHashMap<>(); // bookingReference -> slotId
  private final AtomicBoolean conflictServed = new AtomicBoolean(false);

  public SchedulingController(FaultInjection faults, SlotCalendar calendar) {
    this.faults = faults;
    this.calendar = calendar;
  }

  @PostMapping("/availability-requests")
  public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, Object> body) {
    for (String required : List.of("patientReference", "department", "maxWaitDays")) {
      if (body.get(required) == null) {
        return error(HttpStatus.BAD_REQUEST, required + " is required");
      }
    }
    Object preferred = body.get("preferredDate");
    SearchWindow window = SearchWindow.of(LocalDate.now(), ((Number) body.get("maxWaitDays")).intValue(),
        preferred == null ? null : LocalDate.parse(preferred.toString()));
    String id = "AR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    Map<String, Object> request = new LinkedHashMap<>(body);
    request.putIfAbsent("timeOfDay", "ANY");
    request.put("fromDate", window.from().toString());
    request.put("toDate", window.to().toString());
    request.put("availabilityRequestId", id);
    request.put("createdAt", OffsetDateTime.now().toString());
    requests.put(id, request);
    LOG.info("Availability request registered: {}", request);
    return ResponseEntity.status(HttpStatus.CREATED).body(request);
  }

  @GetMapping("/availability-requests/{id}/slots")
  public ResponseEntity<Map<String, Object>> slots(@PathVariable String id,
                                                   @RequestParam(defaultValue = SlotCalendar.MAIN_HOSPITAL) String site) {
    if (faults.isActive(FaultInjection.SCHEDULING_API_DOWN)) {
      return error(HttpStatus.SERVICE_UNAVAILABLE, "Scheduling service temporarily unavailable");
    }
    if (conflictServed.getAndSet(false)) {
      // the competing booking round is over: the re-query sees the calendar as it is now
      faults.set(FaultInjection.BOOKING_CONFLICT_ONCE, false);
      LOG.warn("Booking-conflict simulation finished (fault switched itself off)");
    }
    if (!SlotCalendar.SITE_DEPARTMENTS.containsKey(site)) {
      return error(HttpStatus.BAD_REQUEST, "Unknown site " + site);
    }
    Map<String, Object> request = requests.get(id);
    if (request == null) {
      return error(HttpStatus.NOT_FOUND, "Unknown availability request " + id);
    }
    List<Map<String, Object>> free = calendar.freeSlots(
            site,
            String.valueOf(request.get("department")),
            LocalDate.parse(String.valueOf(request.get("fromDate"))),
            LocalDate.parse(String.valueOf(request.get("toDate"))),
            (String) request.get("timeOfDay"),
            MAX_SLOTS_RETURNED)
        .stream().map(SlotCalendar.Slot::toMap).toList();
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("availabilityRequestId", id);
    response.put("site", site);
    response.put("department", request.get("department"));
    response.put("fromDate", request.get("fromDate"));
    response.put("toDate", request.get("toDate"));
    response.put("timeOfDay", request.get("timeOfDay"));
    response.put("slots", free);
    LOG.info("Slots for {} at {}: {} found", id, site, free.size());
    return ResponseEntity.ok(response);
  }

  /**
   * Books the first free slot of {@code slotIds} (in the order given, i.e. earliest first). Slots taken by
   * someone else in the meantime are skipped and counted; when all of them are taken the answer is HTTP 409,
   * which the BPMN maps to the error SLOT_TAKEN.
   */
  @PostMapping("/bookings")
  public ResponseEntity<Map<String, Object>> book(@RequestBody Map<String, Object> body) {
    String patientReference = String.valueOf(body.get("patientReference"));
    if (!(body.get("slotIds") instanceof List<?> slotIds) || slotIds.isEmpty()) {
      return error(HttpStatus.BAD_REQUEST, "slotIds (non-empty list) is required");
    }
    int conflicts = 0;
    for (Object candidate : slotIds) {
      String slotId = String.valueOf(candidate);
      var slot = calendar.find(slotId);
      if (slot.isEmpty()) {
        return error(HttpStatus.NOT_FOUND, "Unknown slot " + slotId);
      }
      if (faults.isActive(FaultInjection.BOOKING_CONFLICT_ONCE)) {
        calendar.book(slotId, OTHER_PATIENT); // somebody else was faster
        conflictServed.set(true);
      }
      if (!calendar.book(slotId, patientReference)) {
        conflicts++;
        LOG.warn("Slot {} already booked by another patient, trying the next one", slotId);
        continue;
      }
      String reference = "BK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
      bookings.put(reference, slotId);
      Map<String, Object> booking = new LinkedHashMap<>();
      booking.put("bookingReference", reference);
      booking.put("status", "CONFIRMED");
      booking.put("patientReference", patientReference);
      booking.put("availabilityRequestId", body.get("availabilityRequestId"));
      booking.put("slot", slot.get().toMap());
      booking.put("conflicts", conflicts);
      booking.put("bookedAt", OffsetDateTime.now().toString());
      LOG.info("Booking confirmed: {}", booking);
      return ResponseEntity.status(HttpStatus.CREATED).body(booking);
    }
    LOG.warn("Scheduling API responding 409: all {} slots were taken", conflicts);
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
        "error", "All " + conflicts + " retrieved slots were taken by other patients before booking",
        "status", 409, "conflicts", conflicts));
  }

  @PostMapping("/bookings/{bookingReference}/cancellation")
  public ResponseEntity<Map<String, Object>> cancel(@PathVariable String bookingReference) {
    String slotId = bookings.remove(bookingReference);
    if (slotId == null) {
      return error(HttpStatus.NOT_FOUND, "Unknown booking " + bookingReference);
    }
    calendar.release(slotId);
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("bookingReference", bookingReference);
    response.put("status", "RELEASED");
    response.put("slotId", slotId);
    response.put("releasedAt", OffsetDateTime.now().toString());
    LOG.info("Booking released: {}", response);
    return ResponseEntity.ok(response);
  }

  private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
    LOG.warn("Scheduling API responding {}: {}", status.value(), message);
    return ResponseEntity.status(status).body(Map.of("error", message, "status", status.value()));
  }
}

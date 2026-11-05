package edu.pbj2.lab3.mock;

import static org.assertj.core.api.Assertions.assertThat;

import edu.pbj2.lab3.faults.FaultInjection;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** The mock API endpoints the REST Connector tasks call. */
class SchedulingControllerTest {

  private final FaultInjection faults = new FaultInjection();
  private final SlotCalendar calendar = new SlotCalendar();
  private final SchedulingController api = new SchedulingController(faults, calendar);
  private final LocalDate today = LocalDate.now();

  @Test
  void registrationReturnsTheSearchWindow() {
    var response = api.register(Map.of("patientReference", "SYN-P-0001", "department", "CARDIOLOGY",
        "maxWaitDays", 3, "preferredDate", today.plusDays(2).toString()));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody())
        .containsEntry("fromDate", today.plusDays(2).toString())
        .containsEntry("toDate", today.plusDays(3).toString())
        .containsEntry("timeOfDay", "ANY")
        .containsKey("availabilityRequestId");
  }

  @Test
  void bookingSkipsTakenSlotsAndCountsTheConflicts() {
    List<String> ids = slotIds(2);
    calendar.book(ids.getFirst(), "SYN-P-OTHER");

    var response = api.book(Map.of("patientReference", "SYN-P-0001", "slotIds", ids));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).containsEntry("status", "CONFIRMED").containsEntry("conflicts", 1);
    assertThat(((Map<?, ?>) response.getBody().get("slot")).get("slotId")).isEqualTo(ids.get(1));
  }

  @Test
  void allSlotsTakenAnswers409() {
    faults.set(FaultInjection.BOOKING_CONFLICT_ONCE, true);

    var response = api.book(Map.of("patientReference", "SYN-P-0001", "slotIds", slotIds(3)));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody()).containsEntry("conflicts", 3);
  }

  private List<String> slotIds(int n) {
    return calendar.freeSlots(SlotCalendar.MAIN_HOSPITAL, "CARDIOLOGY", today, today.plusDays(7), "ANY", n)
        .stream().map(SlotCalendar.Slot::slotId).toList();
  }
}

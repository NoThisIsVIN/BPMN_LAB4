package edu.pbj2.lab3.mock;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class SlotCalendarTest {

  private final SlotCalendar calendar = new SlotCalendar();
  private final LocalDate today = LocalDate.now();

  @Test
  void neurologyIsOnlyOfferedAtThePartnerClinic() {
    assertThat(calendar.freeSlots(SlotCalendar.MAIN_HOSPITAL, "NEUROLOGY", today, today.plusDays(14), "ANY", 5))
        .isEmpty();
    assertThat(calendar.freeSlots(SlotCalendar.PARTNER_CLINIC, "NEUROLOGY", today, today.plusDays(14), "ANY", 5))
        .hasSize(5)
        .allSatisfy(s -> assertThat(s.site()).isEqualTo(SlotCalendar.PARTNER_CLINIC));
  }

  @Test
  void dermatologyHasNoSlotsAnywhere() {
    for (String site : SlotCalendar.SITE_DEPARTMENTS.keySet()) {
      assertThat(calendar.freeSlots(site, "DERMATOLOGY", today, today.plusDays(21), "ANY", 5)).isEmpty();
    }
  }

  @Test
  void bookedSlotDisappearsAndComesBackWhenReleased() {
    var first = calendar.freeSlots(SlotCalendar.MAIN_HOSPITAL, "CARDIOLOGY", today, today, "MORNING", 1).getFirst();

    assertThat(calendar.book(first.slotId(), "SYN-P-0001")).isTrue();
    assertThat(calendar.book(first.slotId(), "SYN-P-0002")).isFalse();
    assertThat(calendar.freeSlots(SlotCalendar.MAIN_HOSPITAL, "CARDIOLOGY", today, today, "MORNING", 1).getFirst())
        .isNotEqualTo(first);

    calendar.release(first.slotId());
    assertThat(calendar.freeSlots(SlotCalendar.MAIN_HOSPITAL, "CARDIOLOGY", today, today, "MORNING", 1).getFirst())
        .isEqualTo(first);
  }
}

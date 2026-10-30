package edu.pbj2.lab3.mock;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * In-memory synthetic clinic calendar, generated relative to the day the application starts.
 * Every department except DERMATOLOGY has 8 slots a day for the next 21 days;
 * DERMATOLOGY has none, which exercises the "no suitable slot" path.
 */
@Component
public class SlotCalendar {

  public static final List<String> DEPARTMENTS_WITH_SLOTS =
      List.of("GENERAL_PRACTICE", "CARDIOLOGY", "ORTHOPAEDICS", "RESPIRATORY");
  static final List<LocalTime> TIMES = List.of(
      LocalTime.of(8, 0), LocalTime.of(9, 0), LocalTime.of(10, 0), LocalTime.of(11, 0),
      LocalTime.of(13, 0), LocalTime.of(14, 0), LocalTime.of(15, 0), LocalTime.of(16, 0));
  static final int DAYS_AHEAD = 21;

  /** A bookable slot in the synthetic calendar. */
  public record Slot(String slotId, String department, LocalDate date, LocalTime time, String clinician,
                     String location) {

    boolean matches(String timeOfDay) {
      return switch (timeOfDay == null ? "ANY" : timeOfDay) {
        case "MORNING" -> time.isBefore(LocalTime.NOON);
        case "AFTERNOON" -> !time.isBefore(LocalTime.NOON);
        default -> true;
      };
    }

    public Map<String, Object> toMap() {
      return Map.of("slotId", slotId, "department", department, "date", date.toString(),
          "time", time.toString(), "clinician", clinician, "location", location);
    }
  }

  private final Map<String, Slot> slots = new ConcurrentHashMap<>();
  private final Map<String, String> bookings = new ConcurrentHashMap<>(); // slotId -> patientReference

  public SlotCalendar() {
    LocalDate today = LocalDate.now();
    for (int d = 0; d <= DAYS_AHEAD; d++) {
      LocalDate date = today.plusDays(d);
      for (int deptIndex = 0; deptIndex < DEPARTMENTS_WITH_SLOTS.size(); deptIndex++) {
        String dept = DEPARTMENTS_WITH_SLOTS.get(deptIndex);
        for (int t = 0; t < TIMES.size(); t++) {
          LocalTime time = TIMES.get(t);
          String id = "%s-%s-%s".formatted(dept.substring(0, 3), date.toString().replace("-", ""),
              time.toString().replace(":", ""));
          slots.put(id, new Slot(id, dept, date, time,
              "Clinician " + (char) ('A' + (t % 4)) + " (synthetic)",
              "Outpatients block " + (char) ('A' + deptIndex) + ", room " + (1 + t % 3)));
        }
      }
    }
  }

  public List<Slot> freeSlots(String department, LocalDate from, LocalDate to, String timeOfDay, int limit) {
    List<Slot> result = new ArrayList<>();
    slots.values().stream()
        .filter(s -> s.department().equals(department))
        .filter(s -> !s.date().isBefore(from) && !s.date().isAfter(to))
        .filter(s -> s.matches(timeOfDay))
        .filter(s -> !bookings.containsKey(s.slotId()))
        .sorted(Comparator.comparing(Slot::date).thenComparing(Slot::time))
        .limit(limit)
        .forEach(result::add);
    return result;
  }

  public Optional<Slot> find(String slotId) {
    return Optional.ofNullable(slots.get(slotId));
  }

  /** @return false if the slot was already booked */
  public boolean book(String slotId, String patientReference) {
    return bookings.putIfAbsent(slotId, patientReference) == null;
  }
}

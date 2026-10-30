package edu.pbj2.lab3.scheduling;

import java.time.LocalDate;

/**
 * Date window in which the scheduling system looks for a slot.
 *
 * <p>Rule: the window always ends at {@code today + maxWaitDays} (the triage target). It starts today,
 * or at the patient's preferred date when that date lies inside the window, so the preference is
 * honoured without ever breaking the triage target. Urgent cases ({@code maxWaitDays = 0}) are
 * searched for today only.
 */
public record SearchWindow(LocalDate from, LocalDate to) {

  public static SearchWindow of(LocalDate today, int maxWaitDays, LocalDate preferredDate) {
    LocalDate to = today.plusDays(Math.max(0, maxWaitDays));
    LocalDate from = today;
    if (preferredDate != null && preferredDate.isAfter(today) && !preferredDate.isAfter(to)) {
      from = preferredDate;
    }
    return new SearchWindow(from, to);
  }
}

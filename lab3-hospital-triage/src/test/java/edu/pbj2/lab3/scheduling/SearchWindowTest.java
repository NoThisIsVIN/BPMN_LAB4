package edu.pbj2.lab3.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class SearchWindowTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 30);

  @Test
  void routineWindowRunsFromTodayToTriageTarget() {
    SearchWindow w = SearchWindow.of(TODAY, 14, null);

    assertThat(w.from()).isEqualTo(TODAY);
    assertThat(w.to()).isEqualTo(TODAY.plusDays(14));
  }

  @Test
  void preferredDateInsideWindowMovesTheStart() {
    SearchWindow w = SearchWindow.of(TODAY, 14, TODAY.plusDays(5));

    assertThat(w.from()).isEqualTo(TODAY.plusDays(5));
    assertThat(w.to()).isEqualTo(TODAY.plusDays(14));
  }

  @Test
  void preferredDateBeyondTriageTargetIsIgnored() {
    SearchWindow w = SearchWindow.of(TODAY, 3, TODAY.plusDays(10));

    assertThat(w.from()).isEqualTo(TODAY);
    assertThat(w.to()).isEqualTo(TODAY.plusDays(3));
  }

  @Test
  void urgentCasesSearchTodayOnly() {
    SearchWindow w = SearchWindow.of(TODAY, 0, TODAY.plusDays(2));

    assertThat(w.from()).isEqualTo(TODAY);
    assertThat(w.to()).isEqualTo(TODAY);
  }
}

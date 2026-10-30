package edu.pbj2.lab2.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PaymentRetryPolicyTest {

  @Test
  void defaultsAreAddedOnlyWhenTheOrderDidNotBringThem() {
    assertThat(PaymentRetryPolicy.defaults(Map.of())).containsExactlyInAnyOrderEntriesOf(Map.of(
        "paymentRetryTimeout", "PT30M", "maxPaymentAttempts", 2, "paymentAttempts", 1));

    // a test scenario shortens the wait to seconds; that value must win
    assertThat(PaymentRetryPolicy.defaults(Map.of("paymentRetryTimeout", "PT8S", "maxPaymentAttempts", 3)))
        .containsExactlyInAnyOrderEntriesOf(Map.of("paymentAttempts", 1));
  }

  @Test
  void customerGetsOneMoreTryWithTheDefaultLimit() {
    assertThat(PaymentRetryPolicy.retryAllowed(1, PaymentRetryPolicy.DEFAULT_MAX_ATTEMPTS)).isTrue();
    assertThat(PaymentRetryPolicy.retryAllowed(2, PaymentRetryPolicy.DEFAULT_MAX_ATTEMPTS)).isFalse();
  }
}

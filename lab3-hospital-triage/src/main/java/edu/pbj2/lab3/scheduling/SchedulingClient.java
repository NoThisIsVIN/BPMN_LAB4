package edu.pbj2.lab3.scheduling;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Thin HTTP client the job workers use to talk to the (mock) hospital scheduling API. */
@Component
public class SchedulingClient {

  private static final ParameterizedTypeReference<Map<String, Object>> JSON = new ParameterizedTypeReference<>() {};
  private final RestClient http;

  public SchedulingClient(@Value("${scheduling.base-url}") String baseUrl) {
    this.http = RestClient.builder().baseUrl(baseUrl).build();
  }

  public Map<String, Object> registerAvailabilityRequest(Map<String, Object> request) {
    return http.post().uri("/availability-requests").contentType(MediaType.APPLICATION_JSON)
        .body(request).retrieve().body(JSON);
  }

  public Map<String, Object> book(Map<String, Object> booking) {
    return http.post().uri("/bookings").contentType(MediaType.APPLICATION_JSON)
        .body(booking).retrieve().body(JSON);
  }

  public Map<String, Object> cancel(String bookingReference) {
    return http.post().uri("/bookings/{ref}/cancellation", bookingReference).retrieve().body(JSON);
  }
}

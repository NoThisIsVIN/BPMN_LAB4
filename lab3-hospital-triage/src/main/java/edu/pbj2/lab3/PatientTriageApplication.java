package edu.pbj2.lab3;

import io.camunda.client.annotation.Deployment;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Lab 3 – Hospital appointment and patient triage (synthetic, educational scenario).
 *
 * <p>One Spring Boot process hosts three things:
 * <ul>
 *   <li>the Camunda job workers for the Java service/send tasks ({@code edu.pbj2.lab3.worker}),</li>
 *   <li>the mock hospital scheduling REST API used by the workers and the REST Connector ({@code edu.pbj2.lab3.mock}),</li>
 *   <li>a fault-injection admin API used to provoke and repair incidents ({@code edu.pbj2.lab3.faults}).</li>
 * </ul>
 * BPMN, DMN and form files are deployed to Zeebe automatically on start-up.
 */
@SpringBootApplication
@Deployment(resources = {
    "classpath*:models/*.bpmn",
    "classpath*:models/*.dmn",
    "classpath*:models/*.form"})
public class PatientTriageApplication {

  public static void main(String[] args) {
    SpringApplication.run(PatientTriageApplication.class, args);
  }
}

package edu.pbj2.lab1;

import io.camunda.client.annotation.Deployment;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Lab 1 – Loan approval.
 *
 * <p>One Spring Boot process hosts three things:
 * <ul>
 *   <li>the Camunda job workers for the Java service tasks ({@code edu.pbj2.lab1.worker}),</li>
 *   <li>the mock credit-bureau REST API that the REST Connector calls ({@code edu.pbj2.lab1.mock}),</li>
 *   <li>a fault-injection admin API used to provoke and repair incidents ({@code edu.pbj2.lab1.faults}).</li>
 * </ul>
 * BPMN, DMN and form files are deployed to Zeebe automatically on start-up.
 */
@SpringBootApplication
@Deployment(resources = {
    "classpath*:models/*.bpmn",
    "classpath*:models/*.dmn",
    "classpath*:models/*.form"})
public class LoanApprovalApplication {

  public static void main(String[] args) {
    SpringApplication.run(LoanApprovalApplication.class, args);
  }
}

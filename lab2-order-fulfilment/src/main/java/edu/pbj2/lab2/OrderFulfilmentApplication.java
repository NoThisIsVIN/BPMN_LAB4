package edu.pbj2.lab2;

import edu.pbj2.lab2.domain.CustomerRegistry;
import edu.pbj2.lab2.domain.InventoryService;
import io.camunda.client.annotation.Deployment;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Lab 2 – E-commerce order processing and fulfilment.
 *
 * <p>One Spring Boot process hosts:
 * <ul>
 *   <li>the Camunda job workers for the Java service tasks ({@code edu.pbj2.lab2.worker}),</li>
 *   <li>a simulated inventory service and synthetic customer registry ({@code edu.pbj2.lab2.domain}),</li>
 *   <li>the mock payment gateway called by the REST Connector ({@code edu.pbj2.lab2.mock}),</li>
 *   <li>a fault-injection admin API used to provoke and repair incidents ({@code edu.pbj2.lab2.faults}).</li>
 * </ul>
 * BPMN, DMN and form files are deployed to Zeebe automatically on start-up.
 */
@SpringBootApplication
@Deployment(resources = {
    "classpath*:models/*.bpmn",
    "classpath*:models/*.dmn",
    "classpath*:models/*.form"})
public class OrderFulfilmentApplication {

  public static void main(String[] args) {
    SpringApplication.run(OrderFulfilmentApplication.class, args);
  }

  @Bean
  InventoryService inventoryService() {
    return InventoryService.withSyntheticCatalogue();
  }

  @Bean
  CustomerRegistry customerRegistry() {
    return CustomerRegistry.synthetic();
  }
}

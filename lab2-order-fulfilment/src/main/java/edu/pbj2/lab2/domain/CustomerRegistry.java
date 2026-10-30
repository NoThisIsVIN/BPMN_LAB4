package edu.pbj2.lab2.domain;

import java.util.Map;
import java.util.Optional;

/** Synthetic customer master data (no real people). */
public class CustomerRegistry {

  public record Customer(String reference, String name, String category) {}

  private final Map<String, Customer> customers;

  public CustomerRegistry(Map<String, Customer> customers) {
    this.customers = Map.copyOf(customers);
  }

  public static CustomerRegistry synthetic() {
    return new CustomerRegistry(Map.of(
        "CUST-0001", new Customer("CUST-0001", "Synthetic Customer Alpha", "REGULAR"),
        "CUST-0002", new Customer("CUST-0002", "Synthetic Customer Beta", "VIP"),
        "CUST-0003", new Customer("CUST-0003", "Synthetic Customer Gamma", "NEW"),
        "CUST-0004", new Customer("CUST-0004", "Synthetic Customer Delta", "REGULAR")));
  }

  public Optional<Customer> find(String reference) {
    return Optional.ofNullable(reference).map(customers::get);
  }
}

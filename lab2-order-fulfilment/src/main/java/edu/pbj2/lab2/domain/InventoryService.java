package edu.pbj2.lab2.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simulated inventory service: synthetic product catalogue, stock levels and reservations.
 * State lives in memory and resets when the application restarts.
 */
public class InventoryService {

  public record Product(String id, String name, double unitPrice, String warehouse) {}

  public record Reservation(String id, String productId, int quantity, String status) {}

  private final Map<String, Product> catalogue;
  private final Map<String, Integer> stock = new ConcurrentHashMap<>();
  private final Map<String, Reservation> reservations = new ConcurrentHashMap<>();

  public InventoryService(Map<String, Product> catalogue, Map<String, Integer> initialStock) {
    this.catalogue = Map.copyOf(catalogue);
    this.stock.putAll(initialStock);
  }

  public static InventoryService withSyntheticCatalogue() {
    Map<String, Product> products = new LinkedHashMap<>();
    products.put("PRD-1001", new Product("PRD-1001", "Wireless headphones", 89.90, "WH-NORTH"));
    products.put("PRD-2002", new Product("PRD-2002", "27-inch 4K monitor", 449.00, "WH-NORTH"));
    products.put("PRD-3003", new Product("PRD-3003", "Laptop Pro 16", 1899.00, "WH-SOUTH"));
    products.put("PRD-4004", new Product("PRD-4004", "Ergonomic office chair", 329.00, "WH-SOUTH"));
    products.put("PRD-LOW-01", new Product("PRD-LOW-01", "Limited edition keyboard", 159.00, "WH-NORTH"));
    return new InventoryService(products, Map.of(
        "PRD-1001", 500,
        "PRD-2002", 200,
        "PRD-3003", 100,
        "PRD-4004", 150,
        "PRD-LOW-01", 2));
  }

  public Optional<Product> product(String productId) {
    return Optional.ofNullable(productId).map(catalogue::get);
  }

  public int available(String productId) {
    return stock.getOrDefault(productId, 0);
  }

  /** Atomically reserves {@code quantity}; empty if not enough stock. */
  public synchronized Optional<Reservation> reserve(String productId, int quantity) {
    int available = available(productId);
    if (quantity > available) {
      return Optional.empty();
    }
    stock.put(productId, available - quantity);
    Reservation r = new Reservation("RES-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
        productId, quantity, "RESERVED");
    reservations.put(r.id(), r);
    return Optional.of(r);
  }

  /** Returns reserved stock to the shelf (compensation). Idempotent. */
  public synchronized Optional<Reservation> release(String reservationId) {
    Reservation r = reservations.get(reservationId);
    if (r == null || !"RESERVED".equals(r.status())) {
      return Optional.ofNullable(r);
    }
    stock.merge(r.productId(), r.quantity(), Integer::sum);
    Reservation released = new Reservation(r.id(), r.productId(), r.quantity(), "RELEASED");
    reservations.put(r.id(), released);
    return Optional.of(released);
  }

  /** Marks a reservation as shipped. Idempotent. */
  public synchronized Optional<Reservation> commit(String reservationId) {
    Reservation r = reservations.get(reservationId);
    if (r == null) {
      return Optional.empty();
    }
    Reservation committed = new Reservation(r.id(), r.productId(), r.quantity(), "COMMITTED");
    reservations.put(r.id(), committed);
    return Optional.of(committed);
  }
}

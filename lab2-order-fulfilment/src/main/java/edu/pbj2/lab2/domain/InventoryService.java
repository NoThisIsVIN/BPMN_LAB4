package edu.pbj2.lab2.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simulated inventory service: synthetic product catalogue, stock per warehouse and reservations.
 * Two warehouses: the primary distribution centre and a secondary (regional) warehouse that is used
 * as a fallback when the primary one cannot cover the order. State lives in memory and resets on restart.
 */
public class InventoryService {

  public static final String PRIMARY_WAREHOUSE = "WH-CENTRAL";
  public static final String SECONDARY_WAREHOUSE = "WH-REGIONAL";

  public record Product(String id, String name, double unitPrice) {}

  public record Reservation(String id, String warehouse, String productId, int quantity, String status) {}

  private final Map<String, Product> catalogue;
  /** key = warehouse + "/" + productId */
  private final Map<String, Integer> stock = new ConcurrentHashMap<>();
  private final Map<String, Reservation> reservations = new ConcurrentHashMap<>();

  public InventoryService(Map<String, Product> catalogue, Map<String, Integer> initialStock) {
    this.catalogue = Map.copyOf(catalogue);
    this.stock.putAll(initialStock);
  }

  public static InventoryService withSyntheticCatalogue() {
    Map<String, Product> products = new LinkedHashMap<>();
    products.put("PRD-1001", new Product("PRD-1001", "Wireless headphones", 89.90));
    products.put("PRD-2002", new Product("PRD-2002", "27-inch 4K monitor", 449.00));
    products.put("PRD-3003", new Product("PRD-3003", "Laptop Pro 16", 1899.00));
    products.put("PRD-4004", new Product("PRD-4004", "Ergonomic office chair", 329.00));
    products.put("PRD-5005", new Product("PRD-5005", "Standing desk", 549.00));
    products.put("PRD-LOW-01", new Product("PRD-LOW-01", "Limited edition keyboard", 159.00));
    Map<String, Integer> stock = new LinkedHashMap<>();
    stockAt(stock, "PRD-1001", 500, 100);
    stockAt(stock, "PRD-2002", 200, 50);
    stockAt(stock, "PRD-3003", 100, 20);
    stockAt(stock, "PRD-4004", 150, 30);
    stockAt(stock, "PRD-5005", 0, 40);   // sold out centrally, regional warehouse still has some
    stockAt(stock, "PRD-LOW-01", 2, 1);  // scarce everywhere
    return new InventoryService(products, stock);
  }

  private static void stockAt(Map<String, Integer> stock, String productId, int primary, int secondary) {
    stock.put(key(PRIMARY_WAREHOUSE, productId), primary);
    stock.put(key(SECONDARY_WAREHOUSE, productId), secondary);
  }

  private static String key(String warehouse, String productId) {
    return warehouse + "/" + productId;
  }

  /** Maps the role used in the BPMN task header (PRIMARY / SECONDARY) to a warehouse code. */
  public static String warehouseFor(String role) {
    return "SECONDARY".equalsIgnoreCase(role) ? SECONDARY_WAREHOUSE : PRIMARY_WAREHOUSE;
  }

  public Optional<Product> product(String productId) {
    return Optional.ofNullable(productId).map(catalogue::get);
  }

  public int available(String warehouse, String productId) {
    return stock.getOrDefault(key(warehouse, productId), 0);
  }

  /** Atomically reserves {@code quantity} in one warehouse; empty if that warehouse lacks stock. */
  public synchronized Optional<Reservation> reserve(String warehouse, String productId, int quantity) {
    int available = available(warehouse, productId);
    if (quantity > available) {
      return Optional.empty();
    }
    stock.put(key(warehouse, productId), available - quantity);
    Reservation r = new Reservation("RES-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
        warehouse, productId, quantity, "RESERVED");
    reservations.put(r.id(), r);
    return Optional.of(r);
  }

  /** Returns reserved stock to the shelf it came from (compensation). Idempotent. */
  public synchronized Optional<Reservation> release(String reservationId) {
    Reservation r = reservationId == null ? null : reservations.get(reservationId);
    if (r == null || !"RESERVED".equals(r.status())) {
      return Optional.ofNullable(r);
    }
    stock.merge(key(r.warehouse(), r.productId()), r.quantity(), Integer::sum);
    Reservation released = new Reservation(r.id(), r.warehouse(), r.productId(), r.quantity(), "RELEASED");
    reservations.put(r.id(), released);
    return Optional.of(released);
  }

  /** Marks a reservation as shipped. Idempotent. */
  public synchronized Optional<Reservation> commit(String reservationId) {
    Reservation r = reservationId == null ? null : reservations.get(reservationId);
    if (r == null) {
      return Optional.empty();
    }
    Reservation committed = new Reservation(r.id(), r.warehouse(), r.productId(), r.quantity(), "COMMITTED");
    reservations.put(r.id(), committed);
    return Optional.of(committed);
  }
}

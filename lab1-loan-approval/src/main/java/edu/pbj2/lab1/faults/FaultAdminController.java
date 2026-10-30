package edu.pbj2.lab1.faults;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET  /admin/faults                         -> current switches
 * POST /admin/faults/{name}?enabled=true     -> break the dependency
 * POST /admin/faults/{name}?enabled=false    -> repair it
 */
@RestController
@RequestMapping("/admin/faults")
public class FaultAdminController {

  private static final Logger LOG = LoggerFactory.getLogger(FaultAdminController.class);
  private final FaultInjection faults;

  public FaultAdminController(FaultInjection faults) {
    this.faults = faults;
  }

  @GetMapping
  public Map<String, Boolean> list() {
    return faults.all();
  }

  @PostMapping("/{name}")
  public Map<String, Boolean> toggle(@PathVariable String name, @RequestParam boolean enabled) {
    faults.set(name, enabled);
    LOG.warn("Fault '{}' is now {}", name, enabled ? "ACTIVE (dependency broken)" : "inactive (dependency repaired)");
    return faults.all();
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, String>> unknown(IllegalArgumentException e) {
    return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
  }
}

package iuh.fit.payment_service.controller;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/circuit-breaker")
@Tag(name = "Circuit Breaker Admin", description = "Circuit breaker management endpoints")
@Slf4j
public class CircuitBreakerController {

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public CircuitBreakerController(CircuitBreakerRegistry circuitBreakerRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    @GetMapping("/status/{name}")
    @Operation(summary = "Get circuit breaker status", description = "Returns the current state and metrics of a circuit breaker")
    public ResponseEntity<Map<String, Object>> getCircuitBreakerStatus(@PathVariable String name) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(name);

        return ResponseEntity.ok(Map.of(
                "name", circuitBreaker.getName(),
                "state", circuitBreaker.getState().name(),
                "failureRate", circuitBreaker.getMetrics().getFailureRate(),
                "numberOfBufferedCalls", circuitBreaker.getMetrics().getNumberOfBufferedCalls(),
                "numberOfFailedCalls", circuitBreaker.getMetrics().getNumberOfFailedCalls(),
                "numberOfSuccessfulCalls", circuitBreaker.getMetrics().getNumberOfSuccessfulCalls(),
                "numberOfNotPermittedCalls", circuitBreaker.getMetrics().getNumberOfNotPermittedCalls(),
                "numberOfSuccessfulCalls", circuitBreaker.getMetrics().getNumberOfSuccessfulCalls()
        ));
    }

    @GetMapping("/status")
    @Operation(summary = "Get all circuit breakers status", description = "Returns the current state of all registered circuit breakers")
    public ResponseEntity<Map<String, Object>> getAllCircuitBreakerStatus() {
        Map<String, String> statusMap = circuitBreakerRegistry.getAllCircuitBreakers().stream()
                .collect(java.util.stream.Collectors.toMap(
                        CircuitBreaker::getName,
                        cb -> cb.getState().name()
                ));

        return ResponseEntity.ok(Map.of(
                "circuitBreakers", statusMap
        ));
    }

    @PostMapping("/reset/{name}")
    @Operation(summary = "Reset circuit breaker", description = "Forces the circuit breaker to transition to CLOSED state")
    public ResponseEntity<Map<String, Object>> resetCircuitBreaker(@PathVariable String name) {
        try {
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(name);
            CircuitBreaker.State previousState = circuitBreaker.getState();

            circuitBreaker.reset();

            log.info("[CircuitBreakerAdmin] Reset circuit breaker '{}' from state {} to CLOSED", name, previousState);

            return ResponseEntity.ok(Map.of(
                    "name", name,
                    "previousState", previousState.name(),
                    "currentState", "CLOSED",
                    "message", "Circuit breaker reset successfully"
            ));
        } catch (Exception e) {
            log.error("[CircuitBreakerAdmin] Failed to reset circuit breaker '{}': {}", name, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "name", name,
                    "error", e.getMessage()
            ));
        }
    }

    @PostMapping("/transition/{name}")
    @Operation(summary = "Transition circuit breaker state", description = "Forces the circuit breaker to a specific state (CLOSED, OPEN, HALF_OPEN)")
    public ResponseEntity<Map<String, Object>> transitionCircuitBreaker(
            @PathVariable String name,
            @RequestParam String state
    ) {
        try {
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(name);
            CircuitBreaker.State previousState = circuitBreaker.getState();

            CircuitBreaker.State newState = CircuitBreaker.State.valueOf(state.toUpperCase());
            switch (newState) {
                case OPEN -> circuitBreaker.transitionToOpenState();
                case CLOSED -> circuitBreaker.transitionToClosedState();
                case HALF_OPEN -> circuitBreaker.transitionToHalfOpenState();
                default -> {
                    return ResponseEntity.badRequest().body(Map.of(
                            "name", name,
                            "error", "Invalid state for direct transition. Valid states: CLOSED, OPEN, HALF_OPEN"
                    ));
                }
            }

            log.info("[CircuitBreakerAdmin] Transition circuit breaker '{}' from {} to {}", name, previousState, newState);

            return ResponseEntity.ok(Map.of(
                    "name", name,
                    "previousState", previousState.name(),
                    "currentState", circuitBreaker.getState().name(),
                    "message", "Circuit breaker transitioned successfully"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "name", name,
                    "error", "Invalid state. Valid states: CLOSED, OPEN, HALF_OPEN"
            ));
        } catch (Exception e) {
            log.error("[CircuitBreakerAdmin] Failed to transition circuit breaker '{}': {}", name, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "name", name,
                    "error", e.getMessage()
            ));
        }
    }
}

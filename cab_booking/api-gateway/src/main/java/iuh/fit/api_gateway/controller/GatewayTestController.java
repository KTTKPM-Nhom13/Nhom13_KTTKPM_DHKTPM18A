package iuh.fit.api_gateway.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.Random;

@RestController
public class GatewayTestController {

    private final JwtDecoder jwtDecoder;

    public GatewayTestController(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    /**
     * Test xem Gateway đã khởi chạy thành công chưa (Public)
     */
    @GetMapping("/gateway/health")
    public Mono<ResponseEntity<String>> healthCheck() {
        return Mono.just(ResponseEntity.ok("Gateway is UP and Running!"));
    }

    /**
     * Test xem Filter đã giải mã JWT và đẩy vào Header chưa (Private)
     */
    @GetMapping("/gateway/debug")
    public Mono<ResponseEntity<Map<String, String>>> debugGateway(
            @RequestHeader(value = "X-User-Name", defaultValue = "Not Authenticated") String username,
            @RequestHeader(value = "Authorization", defaultValue = "No Token") String auth) {

        return Mono.just(ResponseEntity.ok(Map.of(
                "status", "Active",
                "extractedUser", username,
                "tokenPreview", auth.length() > 20 ? auth.substring(0, 20) + "..." : auth
        )));
    }

    @PostMapping("/gateway/verify")
    public Map<String, Object> testVerify(@RequestBody String token) {
        Jwt jwt = jwtDecoder.decode(token);
        return
                Map.of(
                        "status", "Xác thực thành công!",
                        "userId", jwt.getSubject(),
                        "issuer", jwt.getClaim("iss"),
                        "allClaims", jwt.getClaims()
                );
    }

    // ── Mock endpoints cho Demo UI ──────────────────────────────────────────

    /**
     * Mock: Tính giá dự kiến (ETA)
     * Route: POST /eta/calculate
     */
    @PostMapping("/eta/calculate")
    public Mono<ResponseEntity<Map<String, Object>>> calculateETA(@RequestBody Map<String, Object> body) {
        double pickupLat = ((Number) ((Map<?, ?>) body.get("pickupLocation")).get("lat")).doubleValue();
        double dropoffLat = ((Number) ((Map<?, ?>) body.get("dropoffLocation")).get("lat")).doubleValue();

        double distanceKm = Math.abs(dropoffLat - pickupLat) * 111;
        double basePrice = 15000;
        double pricePerKm = 8500;
        double estimatedPrice = basePrice + (distanceKm * pricePerKm);
        int estimatedMinutes = (int) (distanceKm * 2.5 + 5);

        return Mono.just(ResponseEntity.ok(Map.of(
                "estimatedPrice", Math.round(estimatedPrice),
                "estimatedMinutes", estimatedMinutes,
                "distanceKm", Math.round(distanceKm * 100.0) / 100.0,
                "timestamp", Instant.now().toString()
        )));
    }

    /**
     * Mock: Đăng nhập
     * Route: POST /auth/login
     */
    @PostMapping("/auth/login")
    public Mono<ResponseEntity<Map<String, Object>>> login(@RequestBody Map<String, Object> body) {
        String username = (String) body.getOrDefault("username", "");
        String password = (String) body.getOrDefault("password", "");

        if (username.isEmpty() || password.isEmpty()) {
            return Mono.just(ResponseEntity.badRequest().body(Map.of(
                    "error", "Username and password are required"
            )));
        }

        return Mono.just(ResponseEntity.ok(Map.of(
                "token", "mock-jwt-token-for-" + username + "-" + Instant.now().toEpochMilli(),
                "username", username,
                "expiresIn", 3600
        )));
    }
}
package iuh.fit.api_gateway.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
public class GatewayTestController {

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
}
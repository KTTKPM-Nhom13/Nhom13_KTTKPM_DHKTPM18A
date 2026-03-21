package iuh.fit.api_gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class AuthenticationGlobalFilter implements GlobalFilter, Ordered {

    private final ReactiveJwtDecoder jwtDecoder;

    public AuthenticationGlobalFilter(ReactiveJwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // 1. Bỏ qua các đường dẫn Public (Login/Register)
        if (path.startsWith("/auth/")) {
            return chain.filter(exchange);
        }

        // 2. Lấy Token từ Header
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);

        // 3. Giải mã và kiểm tra Token
        return jwtDecoder.decode(token)
                .flatMap(jwt -> {
                    // 4. Trích xuất thông tin và đẩy vào Header mới cho các service con
                    ServerWebExchange mutatedExchange = exchange.mutate()
                            .request(builder -> builder
                                    .header("User-Name", jwt.getSubject())
                                    // Bạn có thể lấy thêm roles nếu trong token có chứa
                                    .build())
                            .build();

                    return chain.filter(mutatedExchange);
                })
                .onErrorResume(e -> {
                    // Nếu token sai hoặc hết hạn
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                });
    }

    @Override
    public int getOrder() {
        // Đặt độ ưu tiên cao để chạy trước các filter khác
        return -1;
    }
}
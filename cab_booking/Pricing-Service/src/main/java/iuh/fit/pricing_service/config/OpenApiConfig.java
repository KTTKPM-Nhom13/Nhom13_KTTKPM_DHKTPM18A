package iuh.fit.pricing_service.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Pricing Service API")
                        .version("1.0.0")
                        .description("API for fare calculation and surge pricing management in CAB Booking System")
                        .contact(new Contact()
                                .name("CAB Booking Team")
                                .email("team@cabbooking.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(List.of(
                        new Server().url("http://localhost:8087").description("Development Server"),
                        new Server().url("http://api-gateway:8080").description("Production Server")
                ));
    }
}

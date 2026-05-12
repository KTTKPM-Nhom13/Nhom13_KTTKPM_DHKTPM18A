package iuh.fit.payment_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ComponentScan(
        basePackages = {"iuh.fit.payment_service", "iuh.fit.common"},
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {
                        iuh.fit.common.exception.GlobalExceptionHandler.class,
                        iuh.fit.common.config.SecurityConfig.class,
                        iuh.fit.common.config.JpaAuditingConfig.class
                }
        )
)
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}

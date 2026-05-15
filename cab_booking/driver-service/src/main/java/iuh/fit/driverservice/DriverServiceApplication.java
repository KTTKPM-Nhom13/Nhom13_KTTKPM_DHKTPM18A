package iuh.fit.driverservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication(scanBasePackages = "iuh.fit")
@EnableKafka
public class DriverServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DriverServiceApplication.class, args);
    }
}

package com.cab.ride;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class RideServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RideServiceApplication.class, args);
    }
}

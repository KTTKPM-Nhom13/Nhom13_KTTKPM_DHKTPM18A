package com.cab.matching;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients(basePackages = "com.cab.matching.client")
public class MatchingServiceApplication {

	public static void main(String[] args) {
		try {
			Class.forName("org.apache.kafka.clients.producer.ProducerConfig");
			Class.forName("org.apache.kafka.common.security.oauthbearer.DefaultJwtRetriever");
		} catch (ClassNotFoundException e) {
			// Ignore if not present in classpath
		}
		SpringApplication.run(MatchingServiceApplication.class, args);
	}

}

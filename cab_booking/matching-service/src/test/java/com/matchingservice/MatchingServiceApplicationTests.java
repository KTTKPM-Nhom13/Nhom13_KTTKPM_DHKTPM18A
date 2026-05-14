package com.matchingservice;

import com.cab.matching.MatchingServiceApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
		classes = MatchingServiceApplication.class,
		properties = "spring.autoconfigure.exclude="
				+ "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
				+ "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration"
)
class MatchingServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}

package fr.emse.ismin.reservation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
class ReservationApplicationTests {

	@Autowired
	private ApplicationContext context;

	@Test
	void contextLoads() {
		// GIVEN the application configured with the in-memory test database
		// WHEN Spring starts it (Flyway migrations and Hibernate schema validation included)
		// THEN the context is available
		assertNotNull(context);
	}

}

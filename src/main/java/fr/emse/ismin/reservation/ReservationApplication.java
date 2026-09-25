package fr.emse.ismin.reservation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

@SpringBootApplication
public class ReservationApplication {

	public static void main(String[] args) {
		SpringApplication.run(ReservationApplication.class, args);
	}

	/**
	 * Clock used to know the current time. Injecting it lets the tests fix the
	 * time instead of depending on the moment they run.
	 *
	 * @return the system clock in UTC
	 */
	@Bean
	public Clock clock() {
		return Clock.systemUTC();
	}

}

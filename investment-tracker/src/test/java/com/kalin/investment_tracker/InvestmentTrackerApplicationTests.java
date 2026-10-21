package com.kalin.investment_tracker;

import com.kalin.investment_tracker.catalogue.CsItemService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.DeserializationFeature;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.kalin.investment_tracker.auth.AuthenticationRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:context-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
		"spring.task.scheduling.enabled=false",
		"price.update.initial-delay-ms=3600000",
		"spring.mail.username=test@example.com",
		"spring.mail.password=test-password",
		"security.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
class InvestmentTrackerApplicationTests {

	@MockitoBean
	private CsItemService csItemService;

	@Autowired
	private ApplicationContext applicationContext;

	@Test
	void contextLoads() {
		assertTrue(applicationContext.getBeansOfType(Flyway.class).isEmpty(),
				"The H2 unit-test task disables MySQL migrations; mysqlTest verifies them separately");
		assertTrue(applicationContext.containsBean("applicationTaskExecutor"),
				"The separate verification mail executor must not replace normal application tasks");
		assertFalse(applicationContext.getBean(ObjectMapper.class).isEnabled(DeserializationFeature.ACCEPT_FLOAT_AS_INT),
				"Fractional quantities and IDs must not be silently truncated");
	}

	@Test
	void jacksonUpgradePreservesBrowserDateAndNumericContracts() {
		ObjectMapper mapper = applicationContext.getBean(ObjectMapper.class);
		var payload = new BrowserPayload(LocalDate.of(2026, 10, 21),
				LocalDateTime.of(2026, 10, 21, 9, 30), Instant.parse("2026-10-21T09:30:00Z"),
				new BigDecimal("123.45"));
		var json = mapper.readTree(mapper.writeValueAsString(payload));
		assertEquals("2026-10-21", json.get("date").asString());
		assertEquals("2026-10-21T09:30:00", json.get("dateTime").asString());
		assertEquals("2026-10-21T09:30:00Z", json.get("instant").asString());
		assertTrue(json.get("price").isNumber());
		assertEquals(0, new BigDecimal("123.45").compareTo(json.get("price").decimalValue()));
	}

	@Test
	void loginAcceptsExplicitOrOmittedRememberMeWithoutChangingItsDefault() {
		ObjectMapper mapper = applicationContext.getBean(ObjectMapper.class);
		AuthenticationRequest request = mapper.readValue(
				"{\"email\":\"test@example.invalid\",\"password\":\"test-only\"}", AuthenticationRequest.class);
		assertFalse(request.isRememberMe());
		AuthenticationRequest remembered = mapper.readValue(
				"{\"email\":\"test@example.invalid\",\"password\":\"test-only\",\"rememberMe\":true}", AuthenticationRequest.class);
		assertTrue(remembered.isRememberMe());
	}

	record BrowserPayload(LocalDate date, LocalDateTime dateTime, Instant instant, BigDecimal price) { }

}

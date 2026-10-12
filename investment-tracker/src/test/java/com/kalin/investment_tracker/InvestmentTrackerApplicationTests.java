package com.kalin.investment_tracker;

import com.kalin.investment_tracker.catalogue.CsItemService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

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
				"Stage 1 must not enable Flyway during ordinary application startup");
		assertTrue(applicationContext.containsBean("applicationTaskExecutor"),
				"The separate verification mail executor must not replace normal application tasks");
		assertFalse(applicationContext.getBean(ObjectMapper.class).isEnabled(DeserializationFeature.ACCEPT_FLOAT_AS_INT),
				"Fractional quantities and IDs must not be silently truncated");
	}

}

package br.com.fiap.delivery.order.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

/** Full application context with an isolated in-memory database, without Eureka and with a relaxed rate limit. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:test-${random.uuid};DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=60000",
        "delivery.rate-limit.orders-per-second=10000"
})
@AutoConfigureMockMvc
public @interface IntegrationTest {
}

package com.fantasykai.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The daily ingest runs with {@code --spring.main.web-application-type=none}
 * (scripts/ingest-once.sh), and nothing proved the application could start that
 * way until it could not.
 *
 * <p>Phase 5a added {@code SecurityConfig.filterChain(HttpSecurity, ...)}, and
 * {@code HttpSecurity} exists only in a servlet context. Every test in the suite
 * boots a web application, so all 130 stayed green while the one entrypoint that
 * runs headless died at startup with <em>"Parameter 0 of method filterChain
 * required a bean of type HttpSecurity that could not be found"</em>.
 *
 * <p>It went unseen for three days because two other failures were in front of
 * it: the stale-jar guard refused to run a jar older than {@code src}, and the
 * rebuild that would have satisfied it could not run either. The ingest was
 * already broken before the JDK was; fixing the JDK only exposed it.
 *
 * <p>So this test asserts the negative that the rest of the suite cannot: that
 * the context refreshes with <strong>no servlet environment at all</strong>.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "fantasykai.ingest.scheduled-enabled=false",
        // `once=true` would fire IngestOnceRunner, which ends in System.exit and
        // would take the test JVM with it. The context is what is under test.
        "fantasykai.ingest.once=false"
})
class OneShotContextTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ApplicationContext context;

    @Test
    void theContextStartsWithNoServletEnvironment() {
        // If this is a WebApplicationContext the test is passing for the wrong
        // reason -- it would be proving the thing every other test proves.
        assertThat(context).isNotInstanceOf(WebApplicationContext.class);
    }

    @Test
    void theIngestServiceIsUsableHeadless() {
        // The point of booting at all: the one-shot job needs this bean and its
        // whole dependency graph, not just a context that refreshed.
        assertThat(context.getBean(IngestService.class)).isNotNull();
    }
}

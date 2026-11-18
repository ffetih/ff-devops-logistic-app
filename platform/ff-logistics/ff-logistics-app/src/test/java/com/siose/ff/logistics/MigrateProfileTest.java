package com.siose.ff.logistics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.context.WebApplicationContext;

/** The hook Job's profile starts the app without a web server, so it can finish and exit. */
@SpringBootTest(properties = "spring.flyway.enabled=false")
@ActiveProfiles("migrate")
class MigrateProfileTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void startsWithoutAWebServer() {
        assertThat(context).isNotInstanceOf(WebApplicationContext.class);
    }
}

package com.siose.ff.logistics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * FF Logistics, one deployable. Business modules are added sprint by sprint.
 *
 * <p>The same image has two jobs. Normally it serves HTTP. With the {@code migrate} profile
 * (the Argo CD PreSync hook) it starts without a web server, lets Flyway migrate the database
 * during startup, and exits: 0 when the migration worked, an error when it did not.
 *
 * <p>Modules talk through events (Spring Modulith). A listener runs after the publishing
 * transaction has committed, on a thread of its own: {@code @EnableAsync}.
 */
@SpringBootApplication
@EnableAsync
public class LogisticsApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(LogisticsApplication.class, args);
        if (context.getEnvironment().matchesProfiles("migrate")) {
            System.exit(SpringApplication.exit(context));
        }
    }
}

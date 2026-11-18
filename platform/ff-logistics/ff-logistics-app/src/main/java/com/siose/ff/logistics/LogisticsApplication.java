package com.siose.ff.logistics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * FF Logistics, one deployable. For now it only answers Actuator, which is enough to prove the
 * path from a commit to the cluster. Business modules are added sprint by sprint.
 */
@SpringBootApplication
public class LogisticsApplication {

    public static void main(String[] args) {
        SpringApplication.run(LogisticsApplication.class, args);
    }
}

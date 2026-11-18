package com.siose.ff.logistics;

import java.net.URISyntaxException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * The module walls. No Spring context starts: Modulith reads the compiled classes.
 *
 * <p>Every top-level package under {@code com.siose.ff.logistics} is a module. Its base package
 * is what other modules may use; its sub-packages ({@code api}, {@code domain}, {@code service})
 * are internal. {@code verify()} fails the build on a cycle between modules, on a use of another
 * module's internal package, and on a dependency its {@code package-info.java} does not allow.
 */
class ModularityTest {

    private final ApplicationModules modules = ApplicationModules.of(LogisticsApplication.class);

    @Test
    void modulesKeepTheirBoundaries() {
        modules.verify();
    }

    /**
     * PlantUML component diagrams and a canvas per module, generated from the code into
     * {@code target/spring-modulith-docs}: the current picture, next to the planned one in the
     * Logistics App Guide.
     */
    @Test
    void writeModuleDiagrams() throws URISyntaxException {
        // target/ from the compiled test classes, so the output does not depend on the working
        // directory (tests run in the Maven process, forkCount 0).
        Path testClasses = Path.of(getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
        String outputFolder = testClasses.getParent().resolve("spring-modulith-docs").toString();

        new Documenter(modules, Documenter.Options.defaults().withOutputFolder(outputFolder))
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml()
                .writeModuleCanvases();
    }
}

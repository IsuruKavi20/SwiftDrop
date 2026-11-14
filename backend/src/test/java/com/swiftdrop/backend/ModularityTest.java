package com.swiftdrop.backend;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * The module guard (Spring Modulith). Each top-level package is a module; another module may only use its front door
 * ({@code @NamedInterface} in package-info.java), and modules may not depend on each other in a circle.
 * A broken rule fails this test, so CI blocks the PR. See docs/MODULES.md.
 */
class ModularityTest {

    private static final ApplicationModules MODULES = ApplicationModules.of(BackendApplication.class);

    @Test
    void modulesOnlyUseEachOthersFrontDoorAndHaveNoCycles() {
        MODULES.verify();
    }

    /** Writes the module diagrams (PlantUML) to target/spring-modulith-docs, drawn from the real code. */
    @Test
    void writeModuleDiagrams() {
        new Documenter(MODULES).writeModulesAsPlantUml().writeIndividualModulesAsPlantUml();
    }
}

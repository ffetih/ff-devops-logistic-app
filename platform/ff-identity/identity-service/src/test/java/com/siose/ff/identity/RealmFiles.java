package com.siose.ff.identity;

import java.nio.file.Files;
import java.nio.file.Path;

/** The local Keycloak files in {@code deploy/platform/keycloak}, found from the working directory upwards. */
final class RealmFiles {

    private RealmFiles() {
    }

    static Path directory() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve("deploy/platform/keycloak");
            if (Files.exists(candidate.resolve("ff-logistics-realm.json"))) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("deploy/platform/keycloak not found above " + Path.of("").toAbsolutePath());
    }
}

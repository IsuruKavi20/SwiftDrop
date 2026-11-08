package com.swiftdrop.backend.identity;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Other modules may only use identity's front door (see docs/USERS.md): the person ({@code entity}), the
 * {@code service.UserDirectory}, the messages ({@code event}) and the exceptions. Everything else (repository, client,
 * security, controller, config, other services) is identity's machinery and may change any time.
 */
class IdentityBoundaryTest {

    private static final Path SOURCES = Path.of("src/main/java/com/swiftdrop/backend");
    private static final Pattern IDENTITY_IMPORT =
            Pattern.compile("^import (?:static )?com\\.swiftdrop\\.backend\\.identity\\.([\\w.]+);", Pattern.MULTILINE);
    private static final List<String> ALLOWED = List.of(
            "entity.AppUser", "entity.AppUserStatus", "service.UserDirectory",
            "event.", "exception.");

    @Test
    void otherModulesUseOnlyIdentitysFrontDoor() throws IOException {
        List<String> violations;
        try (Stream<Path> files = Files.walk(SOURCES)) {
            violations = files
                    .filter(file -> file.toString().endsWith(".java"))
                    .filter(file -> !file.startsWith(SOURCES.resolve("identity")))
                    .flatMap(IdentityBoundaryTest::forbiddenImports)
                    .toList();
        }

        assertThat(violations).as("Use only AppUser, UserDirectory, the identity events and exceptions (docs/USERS.md)")
                .isEmpty();
    }

    @Test
    void theCheckReallyLooksAtFiles() throws IOException {
        try (Stream<Path> files = Files.walk(SOURCES)) {
            assertThat(files.filter(file -> file.toString().endsWith(".java")).count()).isGreaterThan(10);
        }
    }

    private static Stream<String> forbiddenImports(Path file) {
        try {
            Matcher imports = IDENTITY_IMPORT.matcher(Files.readString(file));
            Stream.Builder<String> bad = Stream.builder();
            while (imports.find()) {
                String used = imports.group(1);
                if (ALLOWED.stream().noneMatch(used::startsWith)) {
                    bad.add(SOURCES.relativize(file) + " uses identity." + used);
                }
            }
            return bad.build();
        } catch (IOException unreadable) {
            throw new IllegalStateException(unreadable);
        }
    }
}

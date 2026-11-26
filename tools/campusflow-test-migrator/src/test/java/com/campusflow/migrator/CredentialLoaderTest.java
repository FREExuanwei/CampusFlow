package com.campusflow.migrator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CredentialLoaderTest {
    @TempDir Path root;

    @Test void missingCredentialStopsWithoutCreatingAnything() {
        assertEquals(MigrationFailure.Category.CREDENTIAL,
                assertThrows(MigrationFailure.class, () -> new CredentialLoader(root).load()).category());
        assertFalse(Files.exists(root.resolve("config/campusflow-test-migrator.password")));
    }

    @Test void emptyCredentialStops() throws Exception {
        credential("");
        assertThrows(MigrationFailure.class, () -> new CredentialLoader(root).load());
    }

    @Test void onlyTerminalNewlineIsRemoved() throws Exception {
        String fake = "  demo#:\"'\\${literal} $!汉字  ";
        credential(fake + "\r\n");
        assertArrayEquals(fake.toCharArray(), new CredentialLoader(root).load());
    }

    @Test void preservesSpecialCharactersAndTrailingBackslash() throws Exception {
        String fake = " #:[ ]'\"\\\t${demo:value} \\";
        credential(fake);
        assertArrayEquals(fake.toCharArray(), new CredentialLoader(root).load());
    }

    @Test void newlineOnlyIsEmpty() throws Exception {
        credential("\n");
        assertThrows(MigrationFailure.class, () -> new CredentialLoader(root).load());
    }

    @Test void embeddedNewlineIsRejectedWithoutLeakingContent() throws Exception {
        credential("fake-secret\nsecond-line");
        var failure = assertThrows(MigrationFailure.class, () -> new CredentialLoader(root).load());
        assertFalse(failure.toString().contains("fake-secret"));
    }

    @Test void utf8BomIsRemoved() throws Exception {
        credential("\uFEFFdemo-utf8汉字\n");
        assertArrayEquals("demo-utf8汉字".toCharArray(), new CredentialLoader(root).load());
    }

    @Test void malformedUtf8IsRejected() throws Exception {
        Path path = credential("");
        Files.write(path, new byte[]{(byte) 0xC3, (byte) 0x28});
        assertThrows(MigrationFailure.class, () -> new CredentialLoader(root).load());
    }

    @Test void oversizeCredentialIsRejected() throws Exception {
        credential("X".repeat(4097));
        assertThrows(MigrationFailure.class, () -> new CredentialLoader(root).load());
    }

    @Test void alternateFileIsNeverUsed() throws Exception {
        Files.writeString(root.resolve("application-local.yml"), "do-not-read: synthetic");
        assertThrows(MigrationFailure.class, () -> new CredentialLoader(root).load());
    }

    private Path credential(String content) throws Exception {
        Path config = Files.createDirectories(root.resolve("config"));
        return Files.writeString(config.resolve("campusflow-test-migrator.password"),
                content, StandardCharsets.UTF_8);
    }
}

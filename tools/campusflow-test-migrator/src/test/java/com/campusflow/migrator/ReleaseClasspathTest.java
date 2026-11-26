package com.campusflow.migrator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ReleaseClasspathTest {
    @TempDir Path temporary;

    @Test void dependencyCannotIntroduceAnotherSqlResource() throws Exception {
        try (JarFile jar = archive(Map.of("db/migration/afterMigrate.sql", new byte[]{1}))) {
            assertEquals(MigrationFailure.Category.RESOURCE_INTEGRITY,
                    assertThrows(MigrationFailure.class, () -> ReleaseClasspath.verifyResources(jar, false)).category());
        }
    }

    @Test void springClassesAreRejected() throws Exception {
        try (JarFile jar = archive(Map.of("org/springframework/boot/SpringApplication.class", new byte[]{1}))) {
            assertEquals(MigrationFailure.Category.CLASSPATH,
                    assertThrows(MigrationFailure.class, () -> ReleaseClasspath.verifyResources(jar, false)).category());
        }
    }

    @Test void applicationCannotPackageAnImplicitFlywayCallbackOrService() throws Exception {
        for (String name : new String[]{"db/callback/Unexpected.class", "META-INF/services/java.sql.Driver"}) {
            try (JarFile jar = archive(Map.of(name, new byte[]{1}))) {
                assertEquals(MigrationFailure.Category.CLASSPATH,
                        assertThrows(MigrationFailure.class, () -> ReleaseClasspath.verifyResources(jar, true)).category());
            }
        }
    }

    @Test void changedBytesAreRejectedEvenWithAnAllowedFilename() throws Exception {
        try (JarFile jar = archive(Map.of("db/migration/V2__seed_base_roles.sql", new byte[]{1}))) {
            assertEquals(MigrationFailure.Category.RESOURCE_INTEGRITY,
                    assertThrows(MigrationFailure.class, () -> ReleaseClasspath.verifyResources(jar, true)).category());
        }
    }

    private JarFile archive(Map<String, byte[]> resources) throws Exception {
        Path path = temporary.resolve("synthetic.jar");
        try (var output = new JarOutputStream(Files.newOutputStream(path))) {
            for (var resource : resources.entrySet()) {
                output.putNextEntry(new JarEntry(resource.getKey()));
                output.write(resource.getValue());
                output.closeEntry();
            }
        }
        return new JarFile(path.toFile());
    }
}

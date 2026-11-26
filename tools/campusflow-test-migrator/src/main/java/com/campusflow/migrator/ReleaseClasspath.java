package com.campusflow.migrator;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

final class ReleaseClasspath {
    private static final String JAR_NAME = "campusflow-migrator-v6-0.2.0-mysql-final.jar";
    private static final String RELEASE_DIRECTORY = "campusflow-migrator-v6-mysql-final-20261122";
    private static final String MAIN_CLASS = "com.campusflow.migrator.TestMigratorApplication";
    private static final String TOOL_PACKAGE = "com/campusflow/migrator/";
    private static final Set<String> TOOL_METADATA = Set.of("META-INF/MANIFEST.MF", "runtime-lock.properties",
            "META-INF/maven/com.campusflow/campusflow-migrator-v6/pom.xml",
            "META-INF/maven/com.campusflow/campusflow-migrator-v6/pom.properties");
    private static final int RUNTIME_JAR_COUNT = 6;

    static void verifyInvocation() {
        if (!ManagementFactory.getRuntimeMXBean().getInputArguments().isEmpty()) {
            throw failure();
        }
        try {
            Path jar = launcherJar();
            Path classpath = Path.of(System.getProperty("java.class.path")).toAbsolutePath().normalize();
            Path release = jar.getParent();
            if (!jar.toRealPath().equals(jar) || !classpath.equals(jar) || !JAR_NAME.equals(jar.getFileName().toString())
                    || !RELEASE_DIRECTORY.equals(release.getFileName().toString())
                    || !"artifacts".equals(release.getParent().getFileName().toString())) {
                throw failure();
            }
            verifyRelease(jar);
        } catch (IOException | URISyntaxException | IllegalArgumentException e) {
            throw failure();
        }
    }

    static Path projectRoot() {
        try {
            return launcherJar().getParent().getParent().getParent().toRealPath();
        } catch (IOException | URISyntaxException e) {
            throw failure();
        }
    }

    private static Path launcherJar() throws URISyntaxException {
        return Path.of(ReleaseClasspath.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                .toAbsolutePath().normalize();
    }

    static void verifyRelease(Path jar) throws IOException {
        Map<String, String> locked;
        Set<String> manifestPaths;
        if (!Files.isRegularFile(jar, LinkOption.NOFOLLOW_LINKS)) {
            throw failure();
        }
        try (JarFile app = new JarFile(jar.toFile())) {
            var manifest = app.getManifest();
            if (manifest == null || !MAIN_CLASS.equals(
                    manifest.getMainAttributes().getValue(Attributes.Name.MAIN_CLASS))) {
                throw failure();
            }
            String paths = manifest.getMainAttributes().getValue(Attributes.Name.CLASS_PATH);
            if (paths == null) {
                throw failure();
            }
            List<String> entries = Arrays.asList(paths.trim().split("\\s+"));
            manifestPaths = new HashSet<>(entries);
            if (manifestPaths.size() != entries.size()) {
                throw failure();
            }
            verifyResources(app, true);
            var entry = app.getJarEntry("runtime-lock.properties");
            if (entry == null) {
                throw failure();
            }
            try (var input = app.getInputStream(entry)) {
                locked = parseLock(new String(input.readNBytes(8193), StandardCharsets.US_ASCII));
            }
        }
        Set<String> expectedPaths = new HashSet<>();
        locked.keySet().forEach(name -> expectedPaths.add("lib/" + name));
        if (!manifestPaths.equals(expectedPaths)) {
            throw failure();
        }

        Path lib = jar.resolveSibling("lib");
        if (!Files.isDirectory(lib, LinkOption.NOFOLLOW_LINKS) || !lib.toRealPath().equals(lib)) {
            throw failure();
        }
        try (var files = Files.list(lib)) {
            Set<String> names = new HashSet<>();
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                        || !locked.containsKey(name) || !locked.get(name).equals(hash(file))) {
                    throw failure();
                }
                names.add(name);
                try (JarFile dependency = new JarFile(file.toFile())) {
                    var manifest = dependency.getManifest();
                    if (manifest != null && manifest.getMainAttributes().containsKey(Attributes.Name.CLASS_PATH)) {
                        throw failure();
                    }
                    verifyResources(dependency, false);
                }
            }
            if (!names.equals(locked.keySet())) {
                throw failure();
            }
        }
    }

    private static Map<String, String> parseLock(String content) {
        Map<String, String> entries = new HashMap<>();
        for (String line : content.lines().toList()) {
            String[] fields = line.split("=", -1);
            if (fields.length != 2 || !fields[0].matches("[a-z0-9.-]+\\.jar")
                    || !fields[1].matches("[A-F0-9]{64}") || entries.putIfAbsent(fields[0], fields[1]) != null) {
                throw failure();
            }
        }
        if (entries.size() != RUNTIME_JAR_COUNT) {
            throw failure();
        }
        return entries;
    }

    static void verifyResources(JarFile jar, boolean application) throws IOException {
        Set<String> sql = new HashSet<>();
        Set<String> all = new HashSet<>();
        var entries = jar.entries();
        while (entries.hasMoreElements()) {
            var entry = entries.nextElement();
            if (entry.isDirectory()) {
                continue;
            }
            String name = entry.getName();
            String lower = name.toLowerCase(Locale.ROOT);
            if (!all.add(name) || lower.endsWith(".jar") || lower.startsWith("boot-inf/")
                    || lower.startsWith("org/springframework/") || lower.startsWith("org/hibernate/")
                    || lower.startsWith("jakarta/persistence/") || lower.startsWith("com/campusflow/")
                    && !lower.startsWith(TOOL_PACKAGE)) {
                throw failure();
            }
            if (lower.endsWith(".sql") || lower.contains("db/migration/")) {
                sql.add(name);
                if (!application || !name.startsWith(ApprovedMigrations.DIRECTORY)) {
                    throw new MigrationFailure(MigrationFailure.Category.RESOURCE_INTEGRITY);
                }
                String filename = name.substring(ApprovedMigrations.DIRECTORY.length());
                try (var input = jar.getInputStream(entry)) {
                    if (!ApprovedMigrations.sha256(input.readNBytes(65537))
                            .equals(ApprovedMigrations.HASHES.get(filename))) {
                        throw new MigrationFailure(MigrationFailure.Category.RESOURCE_INTEGRITY);
                    }
                }
            } else if (application && !TOOL_METADATA.contains(name)
                    && !(name.startsWith(TOOL_PACKAGE) && name.endsWith(".class"))) {
                throw failure();
            }
        }
        if (application && sql.size() != ApprovedMigrations.HASHES.size()) {
            throw new MigrationFailure(MigrationFailure.Category.RESOURCE_INTEGRITY);
        }
    }

    private static String hash(Path file) throws IOException {
        try (var input = Files.newInputStream(file)) {
            var digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            for (int count; (count = input.read(buffer)) != -1; ) {
                digest.update(buffer, 0, count);
            }
            return HexFormat.of().withUpperCase().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw failure();
        }
    }

    private static MigrationFailure failure() {
        return new MigrationFailure(MigrationFailure.Category.CLASSPATH);
    }

    private ReleaseClasspath() {}
}

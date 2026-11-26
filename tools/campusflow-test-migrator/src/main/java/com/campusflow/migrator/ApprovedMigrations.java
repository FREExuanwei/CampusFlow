package com.campusflow.migrator;

import org.flywaydb.core.api.ResourceProvider;
import org.flywaydb.core.api.resource.LoadableResource;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ApprovedMigrations implements ResourceProvider {
    static final String DIRECTORY = "db/migration/";
    static final Map<String, String> HASHES = Map.of(
            "V1__create_user_tables.sql", "BF905E11A5D6370B968166D361EF2DB31A3C1416CC4FF4A7DC38785CA404B24E",
            "V2__seed_base_roles.sql", "F7FAB40B4F19192A8EDC176A2B277BA2CC35DBCE4079002AC895D0BF7AB423B6",
            "V3__create_activities.sql", "A87D1E6678A2A5E4781C388445DC03E1DB90547F1F0F3933E7FE8304CCEDFD20",
            "V4__activity_review_and_publication.sql", "F42A7BA5FDD3E8E852DD61277590B1041D7109DED0834D66E69E77A525868052",
            "V5__create_enrollments.sql", "4C6DA46AE01DF5A51A0516ADCE0E0B89CE9A50453A9BDFA0B3BEA40EC12C13D4",
            "V6__notifications_and_operation_logs.sql", "C3D8FE348F003A6AB71951FE1A8F59B0753094CABB2E6BFD1A3C5F60C6B3373F");
    private final Map<String, LoadableResource> resources;

    private ApprovedMigrations(Map<String, LoadableResource> resources) {
        this.resources = Map.copyOf(resources);
    }

    static ApprovedMigrations load(ClassLoader loader) {
        Map<String, byte[]> contents = new LinkedHashMap<>();
        try {
            for (String name : HASHES.keySet()) {
                var matches = java.util.Collections.list(loader.getResources(DIRECTORY + name));
                if (matches.size() != 1) {
                    throw new MigrationFailure(MigrationFailure.Category.RESOURCE_INTEGRITY);
                }
                try (var input = matches.getFirst().openStream()) {
                    contents.put(name, input.readNBytes(65537));
                }
            }
        } catch (IOException e) {
            throw new MigrationFailure(MigrationFailure.Category.RESOURCE_INTEGRITY);
        }
        return verify(contents);
    }

    static ApprovedMigrations verify(Map<String, byte[]> contents) {
        if (!contents.keySet().equals(HASHES.keySet())) {
            throw new MigrationFailure(MigrationFailure.Category.RESOURCE_INTEGRITY);
        }
        Map<String, LoadableResource> resources = new LinkedHashMap<>();
        contents.forEach((name, bytes) -> {
            if (!sha256(bytes).equals(HASHES.get(name))) {
                throw new MigrationFailure(MigrationFailure.Category.RESOURCE_INTEGRITY);
            }
            resources.put(name, new ApprovedResource(name, new String(bytes, StandardCharsets.UTF_8)));
        });
        return new ApprovedMigrations(resources);
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256");
        }
    }

    @Override public LoadableResource getResource(String name) {
        String filename = name.startsWith(DIRECTORY) ? name.substring(DIRECTORY.length()) : name;
        return resources.get(filename);
    }

    @Override public Collection<LoadableResource> getResources(String prefix, String[] suffixes) {
        return resources.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(prefix))
                .filter(entry -> Arrays.stream(suffixes).anyMatch(entry.getKey()::endsWith))
                .sorted(Map.Entry.comparingByKey()).map(Map.Entry::getValue).toList();
    }

    List<LoadableResource> all() {
        return resources.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue).toList();
    }

    private static final class ApprovedResource extends LoadableResource {
        private final String filename;
        private final String sql;
        ApprovedResource(String filename, String sql) {
            this.filename = filename;
            this.sql = sql;
        }
        @Override public Reader read() { return new StringReader(sql); }
        @Override public String getAbsolutePath() { return DIRECTORY + filename; }
        @Override public String getAbsolutePathOnDisk() { return null; }
        @Override public String getFilename() { return filename; }
        @Override public String getRelativePath() { return filename; }
    }
}

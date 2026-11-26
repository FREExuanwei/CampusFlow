package com.campusflow.migrator;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;

final class CredentialLoader {
    private final Path root;

    CredentialLoader(Path root) {
        this.root = root;
    }

    char[] load() {
        Path directory = root.resolve("config");
        Path file = directory.resolve("campusflow-test-migrator.password");
        if (Files.isSymbolicLink(directory) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new MigrationFailure(MigrationFailure.Category.CREDENTIAL);
        }
        byte[] bytes = null;
        CharBuffer decoded = null;
        try {
            Path resolved = root.toRealPath().resolve("config");
            if (!directory.toRealPath().equals(resolved) || !file.toRealPath().equals(resolved.resolve(file.getFileName()))) {
                throw new MigrationFailure(MigrationFailure.Category.CREDENTIAL);
            }
            try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
                bytes = input.readNBytes(4097);
            }
            if (bytes.length > 4096) {
                throw new MigrationFailure(MigrationFailure.Category.CREDENTIAL);
            }
            decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            int start = decoded.length() > 0 && decoded.charAt(0) == '\uFEFF' ? 1 : 0;
            int end = decoded.length();
            if (end > start && decoded.charAt(end - 1) == '\n') {
                end--;
                if (end > start && decoded.charAt(end - 1) == '\r') {
                    end--;
                }
            }
            if (start == end) {
                throw new MigrationFailure(MigrationFailure.Category.CREDENTIAL);
            }
            char[] password = new char[end - start];
            for (int i = start; i < end; i++) {
                char value = decoded.charAt(i);
                if (value == '\r' || value == '\n' || value == 0) {
                    Arrays.fill(password, '\0');
                    throw new MigrationFailure(MigrationFailure.Category.CREDENTIAL);
                }
                password[i - start] = value;
            }
            return password;
        } catch (IOException e) {
            throw new MigrationFailure(MigrationFailure.Category.CREDENTIAL);
        } finally {
            if (decoded != null && decoded.hasArray()) {
                Arrays.fill(decoded.array(), '\0');
            }
            if (bytes != null) {
                Arrays.fill(bytes, (byte) 0);
            }
        }
    }
}

package com.campusflow.common.config;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import ch.qos.logback.core.FileAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LogbackFileOutputTests {

    @Test
    void writesFormattedEventsAndExceptionsToFile(@TempDir Path outputDirectory) throws Exception {
        Path logFile = outputDirectory.resolve("file-output.log");
        var context = new LoggerContext();
        try {
            context.setMDCAdapter(new LogbackMDCAdapter());
            context.start();

            var encoder = new PatternLayoutEncoder();
            encoder.setContext(context);
            encoder.setCharset(StandardCharsets.UTF_8);
            encoder.setPattern("%level %msg%n%ex");
            encoder.start();

            var appender = new FileAppender<ILoggingEvent>();
            appender.setContext(context);
            appender.setName("FILE");
            appender.setFile(logFile.toString());
            appender.setEncoder(encoder);
            appender.start();
            assertThat(appender.isStarted()).isTrue();

            var logger = context.getLogger(LogbackFileOutputTests.class);
            logger.addAppender(appender);
            logger.info("CampusFlow logging fixture");
            logger.error("CampusFlow exception fixture", new IllegalStateException("synthetic failure"));
        } finally {
            context.stop();
        }

        assertThat(Files.readString(logFile, StandardCharsets.UTF_8))
                .contains("INFO CampusFlow logging fixture",
                        "ERROR CampusFlow exception fixture",
                        "java.lang.IllegalStateException: synthetic failure");
    }
}

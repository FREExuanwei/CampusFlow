package com.campusflow.migrator;

import org.flywaydb.core.api.logging.Log;
import org.flywaydb.core.api.logging.LogCreator;

/** Keeps dependency diagnostics out of the tool's sanitized console output. */
public final class SilentFlywayLogger implements LogCreator {
    private static final Log SILENT = new Log() {
        @Override public void debug(String message) {}
        @Override public void info(String message) {}
        @Override public void warn(String message) {}
        @Override public void error(String message) {}
        @Override public void error(String message, Exception error) {}
        @Override public void notice(String message) {}
    };

    @Override
    public Log createLogger(Class<?> type) {
        return SILENT;
    }
}

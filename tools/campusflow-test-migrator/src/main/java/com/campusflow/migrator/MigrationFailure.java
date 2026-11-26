package com.campusflow.migrator;

final class MigrationFailure extends RuntimeException {
    enum Category {
        ARGUMENTS, CREDENTIAL, RESOURCE_INTEGRITY, CLASSPATH, CONFIGURATION,
        CONFIRMATION, CONNECTION, TLS, PERMISSION, LOCK, HISTORY, SQL_EXECUTION, VERIFICATION, UNKNOWN
    }

    private final Category category;

    MigrationFailure(Category category) {
        super(category.name());
        this.category = category;
    }

    Category category() {
        return category;
    }
}

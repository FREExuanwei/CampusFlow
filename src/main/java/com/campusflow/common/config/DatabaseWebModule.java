package com.campusflow.common.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Profile;

/** Enables business web modules only with a single application data source. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Profile(DatabaseWebModule.PROFILE)
public @interface DatabaseWebModule {
    String MYSQL_PROFILE = "((mysql-dev & !mysql-test) | (mysql-test & !mysql-dev)) & !mysql-migrate";
    String PROFILE = "(" + MYSQL_PROFILE + ") | (persistence-test & !mysql-dev & !mysql-test & !mysql-migrate) | demo";
}

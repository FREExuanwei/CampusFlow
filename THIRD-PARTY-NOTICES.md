# Third-party notices

CampusFlow v0.2.0 references the dependencies and includes the third-party materials listed below. The holder of each component retains its rights under the component's upstream terms. The [MIT License](LICENSE) applies to original CampusFlow code.

## Included materials

### Apache Maven Wrapper

The source checkout includes the Apache Maven Wrapper 3.3.4 scripts `mvnw` and `mvnw.cmd`, licensed under Apache License 2.0. The scripts download the bootstrap JAR into the local checkout on first use. The checkout retains the Apache [LICENSE](.mvn/wrapper/LICENSE) and [NOTICE](.mvn/wrapper/NOTICE). The JAR's notice credits the Apache Software Foundation, copyright 2013–2025. The upstream source notice also credits the Apache Software Foundation, copyright 2013–2022, and the original Gradle Wrapper implementation by Hans Dockter and Adam Murdoch, copyright 2007 the original author or authors. [Apache Maven Wrapper releases](https://maven.apache.org/tools/wrapper/download.cgi) · [Upstream source NOTICE](https://github.com/apache/maven-wrapper/blob/maven-wrapper-3.3.4/NOTICE) · [Apache License 2.0 and redistribution terms](https://www.apache.org/licenses/LICENSE-2.0)

The scripts verify the downloaded Wrapper JAR using the configured SHA-256 pin `4e2fbf6554bc8a4702cdfdd3bef464f423393d784ddbb037216320ce55d5e4e1`. The reviewed bootstrap JAR also matched the Maven Central SHA-1 checksum. The scripts match the upstream templates with the release version substituted and line endings normalized. The wrapper fetches Apache Maven 3.9.16, an Apache-2.0 distribution with its own accompanying license and third-party notices. [Maven Wrapper JAR and checksum](https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.4/) · [Maven 3.9.16 distribution and checksums](https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.16/)

### Frontend visuals

- The four campus and activity photographs under `frontend/public/media/` (`academic.jpg`, `campus.jpg`, `culture.jpg` and `sports.jpg`) were generated with AI for CampusFlow. The project's asset provenance records approved their inclusion.
- The stylesheet uses the user's installed Inter, Segoe UI, Microsoft YaHei UI and Noto Sans SC fonts when available, followed by system fallbacks. The checkout supplies CSS font-family references.
- The interface uses `lucide-react` 1.79.0. The upstream package declares ISC and specifies MIT for the listed Feather-derived icons. The original notice and license terms are included below for Lucide and Feather graphics visible in screenshots. [Lucide npm package](https://www.npmjs.com/package/lucide-react/v/1.79.0) · [Lucide upstream license](https://github.com/lucide-icons/lucide/blob/main/LICENSE)

<details>
<summary>Lucide and Feather license notices from lucide-react 1.79.0</summary>

```text
ISC License

Copyright (c) 2026 Lucide Icons and Contributors

Permission to use, copy, modify, and/or distribute this software for any
purpose with or without fee is hereby granted, provided that the above
copyright notice and this permission notice appear in all copies.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.

---

The following Lucide icons are derived from the Feather project:

airplay, alert-circle, alert-octagon, alert-triangle, aperture, arrow-down-circle, arrow-down-left, arrow-down-right, arrow-down, arrow-left-circle, arrow-left, arrow-right-circle, arrow-right, arrow-up-circle, arrow-up-left, arrow-up-right, arrow-up, at-sign, calendar, cast, check, chevron-down, chevron-left, chevron-right, chevron-up, chevrons-down, chevrons-left, chevrons-right, chevrons-up, circle, clipboard, clock, code, columns, command, compass, corner-down-left, corner-down-right, corner-left-down, corner-left-up, corner-right-down, corner-right-up, corner-up-left, corner-up-right, crosshair, database, divide-circle, divide-square, dollar-sign, download, external-link, feather, frown, hash, headphones, help-circle, info, italic, key, layout, life-buoy, link-2, link, loader, lock, log-in, log-out, maximize, meh, minimize, minimize-2, minus-circle, minus-square, minus, monitor, moon, more-horizontal, more-vertical, move, music, navigation-2, navigation, octagon, pause-circle, percent, plus-circle, plus-square, plus, power, radio, rss, search, server, share, shopping-bag, sidebar, smartphone, smile, square, table-2, tablet, target, terminal, trash-2, trash, triangle, tv, type, upload, x-circle, x-octagon, x-square, x, zoom-in, zoom-out

The MIT License (MIT) (for the icons listed above)

Copyright (c) 2013-present Cole Bemis

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

</details>

## Dependencies retrieved by Maven

The application uses Spring Boot 4.1.2, Spring Framework 7.0.10, Spring Data JPA 4.1.2 and Spring Security 7.1.2, all Apache-2.0. JWT support comes from Spring Security OAuth2 Resource Server and Nimbus JOSE + JWT 10.9.1 (Apache-2.0). BCrypt is provided by Spring Security Crypto 7.1.2 (Apache-2.0). Spring Boot's notices credit VMware, Inc., copyright 2012–2026. The Spring Data JPA notices credit Pivotal Software, Inc., copyright 2015–2026. [Spring Boot 4.1.2 artifact](https://central.sonatype.com/artifact/org.springframework.boot/spring-boot/4.1.2) · [Spring Security Crypto 7.1.2 artifact](https://central.sonatype.com/artifact/org.springframework.security/spring-security-crypto/7.1.2) · [Nimbus JOSE + JWT 10.9.1 artifact](https://central.sonatype.com/artifact/com.nimbusds/nimbus-jose-jwt/10.9.1)

| Component | Version | License and use |
| --- | --- | --- |
| [H2](https://central.sonatype.com/artifact/com.h2database/h2/2.4.240) | 2.4.240 | MPL-2.0 or EPL-1.0. Application tests and runtime under the `demo` profile. |
| [Flyway Core](https://central.sonatype.com/artifact/org.flywaydb/flyway-core/12.4.0) and [Flyway MySQL](https://central.sonatype.com/artifact/org.flywaydb/flyway-mysql/12.4.0) | 12.4.0 | Apache-2.0. Application and standalone migrator. Copyright 2010–2026 Red Gate Software Ltd. |
| [MySQL Connector/J Community](https://central.sonatype.com/artifact/com.mysql/mysql-connector-j/26.10.0) | 26.10.0 | GPL-2.0-only with Universal FOSS Exception 1.0 and Oracle's additional linking permission described below. Application runtime and standalone migrator. |
| [Jackson Core](https://central.sonatype.com/artifact/tools.jackson.core/jackson-core/3.1.8) and [Jackson Databind](https://central.sonatype.com/artifact/tools.jackson.core/jackson-databind/3.1.8) | 3.1.8 | Apache-2.0. Application and standalone migrator. |
| [Jackson Annotations](https://central.sonatype.com/artifact/com.fasterxml.jackson.core/jackson-annotations/2.21) | 2.21 | Apache-2.0. Application and standalone migrator. |
| [JUnit Jupiter and Platform](https://central.sonatype.com/artifact/org.junit.jupiter/junit-jupiter/6.0.3) | 6.0.3 | EPL-2.0. Application and standalone migrator tests. |

The standalone migrator's runtime graph contains both Flyway components, Connector/J and the three Jackson artifacts above. The Spring Boot dependency-management POM excludes Protobuf Java from Connector/J in the application. The migrator POM makes the same exclusion explicitly. The Spring Boot Maven plugin is 4.1.2 (Apache-2.0). The standalone migrator declares Apache-2.0 Maven plugins for resources 3.5.0, compilation 3.15.0, tests (Surefire) 3.5.6, JAR creation 3.5.1 and dependency copying 3.10.0. [Spring Boot dependency management](https://central.sonatype.com/artifact/org.springframework.boot/spring-boot-dependencies/4.1.2) · [Apache Maven plugins](https://maven.apache.org/plugins/)

<details>
<summary>Application Maven dependency inventory</summary>

Versions and scopes are from the resolved application dependency tree. License entries come from corresponding publisher POMs, including inherited metadata where applicable. The H2 scope in this table is the default. Each linked Maven Central entry identifies a versioned artifact in the corresponding group.

| Maven group | Artifacts | Version | Default scope | Declared license |
| --- | --- | --- | --- | --- |
| [`ch.qos.logback`](https://central.sonatype.com/artifact/ch.qos.logback/logback-classic/1.6.5) | `logback-classic`, `logback-core` | 1.6.5 | compile | EPL-2.0 or LGPL-2.1-only |
| [`com.fasterxml`](https://central.sonatype.com/artifact/com.fasterxml/classmate/1.7.3) | `classmate` | 1.7.3 | compile | Apache-2.0 |
| [`com.fasterxml.jackson.core`](https://central.sonatype.com/artifact/com.fasterxml.jackson.core/jackson-annotations/2.21) | `jackson-annotations` | 2.21 | compile | Apache-2.0 |
| [`com.h2database`](https://central.sonatype.com/artifact/com.h2database/h2/2.4.240) | `h2` | 2.4.240 | test | MPL-2.0 or EPL-1.0 |
| [`com.jayway.jsonpath`](https://central.sonatype.com/artifact/com.jayway.jsonpath/json-path/2.10.0) | `json-path` | 2.10.0 | test | Apache-2.0 |
| [`com.mysql`](https://central.sonatype.com/artifact/com.mysql/mysql-connector-j/26.10.0) | `mysql-connector-j` | 26.10.0 | runtime | GPL-2.0-only with Universal FOSS Exception 1.0 and additional linking permission |
| [`com.nimbusds`](https://central.sonatype.com/artifact/com.nimbusds/nimbus-jose-jwt/10.9.1) | `nimbus-jose-jwt` | 10.9.1 | compile | Apache-2.0 |
| [`com.sun.istack`](https://central.sonatype.com/artifact/com.sun.istack/istack-commons-runtime/4.1.2) | `istack-commons-runtime` | 4.1.2 | runtime | EDL-1.0 |
| [`com.vaadin.external.google`](https://central.sonatype.com/artifact/com.vaadin.external.google/android-json/0.0.20131108.vaadin1) | `android-json` | 0.0.20131108.vaadin1 | test | Apache-2.0 |
| [`com.zaxxer`](https://central.sonatype.com/artifact/com.zaxxer/HikariCP/7.0.2) | `HikariCP` | 7.0.2 | compile | Apache-2.0 |
| [`commons-logging`](https://central.sonatype.com/artifact/commons-logging/commons-logging/1.3.6) | `commons-logging` | 1.3.6 | compile | Apache-2.0 |
| [`io.micrometer`](https://central.sonatype.com/artifact/io.micrometer/micrometer-commons/1.17.2) | `micrometer-commons`, `micrometer-observation` | 1.17.2 | compile | Apache-2.0 |
| [`jakarta.activation`](https://central.sonatype.com/artifact/jakarta.activation/jakarta.activation-api/2.1.4) | `jakarta.activation-api` | 2.1.4 | runtime | EDL-1.0 |
| [`jakarta.annotation`](https://central.sonatype.com/artifact/jakarta.annotation/jakarta.annotation-api/3.0.0) | `jakarta.annotation-api` | 3.0.0 | compile | EPL-2.0 or GPL-2.0 with Classpath Exception |
| [`jakarta.inject`](https://central.sonatype.com/artifact/jakarta.inject/jakarta.inject-api/2.0.1) | `jakarta.inject-api` | 2.0.1 | runtime | Apache-2.0 |
| [`jakarta.persistence`](https://central.sonatype.com/artifact/jakarta.persistence/jakarta.persistence-api/3.2.0) | `jakarta.persistence-api` | 3.2.0 | compile | EPL-2.0 or EDL-1.0 |
| [`jakarta.transaction`](https://central.sonatype.com/artifact/jakarta.transaction/jakarta.transaction-api/2.0.1) | `jakarta.transaction-api` | 2.0.1 | compile | EPL-2.0 or GPL-2.0 with Classpath Exception |
| [`jakarta.validation`](https://central.sonatype.com/artifact/jakarta.validation/jakarta.validation-api/3.1.1) | `jakarta.validation-api` | 3.1.1 | compile | Apache-2.0 |
| [`jakarta.xml.bind`](https://central.sonatype.com/artifact/jakarta.xml.bind/jakarta.xml.bind-api/4.0.5) | `jakarta.xml.bind-api` | 4.0.5 | runtime | EDL-1.0 |
| [`net.bytebuddy`](https://central.sonatype.com/artifact/net.bytebuddy/byte-buddy/1.18.15) | `byte-buddy` | 1.18.15 | runtime | Apache-2.0 |
| [`net.minidev`](https://central.sonatype.com/artifact/net.minidev/accessors-smart/2.6.0) | `accessors-smart`, `json-smart` | 2.6.0 | test | Apache-2.0 |
| [`org.antlr`](https://central.sonatype.com/artifact/org.antlr/antlr4-runtime/4.13.2) | `antlr4-runtime` | 4.13.2 | compile | BSD-3-Clause |
| [`org.apache.logging.log4j`](https://central.sonatype.com/artifact/org.apache.logging.log4j/log4j-api/2.25.5) | `log4j-api`, `log4j-to-slf4j` | 2.25.5 | compile | Apache-2.0 |
| [`org.apache.tomcat.embed`](https://central.sonatype.com/artifact/org.apache.tomcat.embed/tomcat-embed-core/11.0.27) | `tomcat-embed-core`, `tomcat-embed-el`, `tomcat-embed-websocket` | 11.0.27 | compile | Apache-2.0 |
| [`org.apiguardian`](https://central.sonatype.com/artifact/org.apiguardian/apiguardian-api/1.1.2) | `apiguardian-api` | 1.1.2 | test | Apache-2.0 |
| [`org.aspectj`](https://central.sonatype.com/artifact/org.aspectj/aspectjweaver/1.9.25.1) | `aspectjweaver` | 1.9.25.1 | compile | EPL-2.0 |
| [`org.assertj`](https://central.sonatype.com/artifact/org.assertj/assertj-core/3.27.7) | `assertj-core` | 3.27.7 | test | Apache-2.0 |
| [`org.awaitility`](https://central.sonatype.com/artifact/org.awaitility/awaitility/4.3.0) | `awaitility` | 4.3.0 | test | Apache-2.0 |
| [`org.eclipse.angus`](https://central.sonatype.com/artifact/org.eclipse.angus/angus-activation/2.0.3) | `angus-activation` | 2.0.3 | runtime | EDL-1.0 |
| [`org.flywaydb`](https://central.sonatype.com/artifact/org.flywaydb/flyway-core/12.4.0) | `flyway-core` | 12.4.0 | compile | Apache-2.0 |
| [`org.flywaydb`](https://central.sonatype.com/artifact/org.flywaydb/flyway-mysql/12.4.0) | `flyway-mysql` | 12.4.0 | runtime | Apache-2.0 |
| [`org.glassfish.jaxb`](https://central.sonatype.com/artifact/org.glassfish.jaxb/jaxb-core/4.0.9) | `jaxb-core`, `jaxb-runtime`, `txw2` | 4.0.9 | runtime | EDL-1.0 |
| [`org.hamcrest`](https://central.sonatype.com/artifact/org.hamcrest/hamcrest/3.0) | `hamcrest` | 3.0 | test | BSD-3-Clause |
| [`org.hibernate.models`](https://central.sonatype.com/artifact/org.hibernate.models/hibernate-models/1.1.2) | `hibernate-models` | 1.1.2 | runtime | Apache-2.0 |
| [`org.hibernate.orm`](https://central.sonatype.com/artifact/org.hibernate.orm/hibernate-core/7.4.13.Final) | `hibernate-core` | 7.4.13.Final | compile | Apache-2.0 |
| [`org.hibernate.validator`](https://central.sonatype.com/artifact/org.hibernate.validator/hibernate-validator/9.1.5.Final) | `hibernate-validator` | 9.1.5.Final | compile | Apache-2.0 |
| [`org.jboss.logging`](https://central.sonatype.com/artifact/org.jboss.logging/jboss-logging/3.6.3.Final) | `jboss-logging` | 3.6.3.Final | compile | Apache-2.0 |
| [`org.jspecify`](https://central.sonatype.com/artifact/org.jspecify/jspecify/1.0.1) | `jspecify` | 1.0.1 | compile | Apache-2.0 |
| [`org.junit.jupiter`](https://central.sonatype.com/artifact/org.junit.jupiter/junit-jupiter-api/6.0.3) | `junit-jupiter-api`, `junit-jupiter-engine`, `junit-jupiter-params`, `junit-jupiter` | 6.0.3 | test | EPL-2.0 |
| [`org.junit.platform`](https://central.sonatype.com/artifact/org.junit.platform/junit-platform-commons/6.0.3) | `junit-platform-commons`, `junit-platform-engine` | 6.0.3 | test | EPL-2.0 |
| [`org.opentest4j`](https://central.sonatype.com/artifact/org.opentest4j/opentest4j/1.3.0) | `opentest4j` | 1.3.0 | test | Apache-2.0 |
| [`org.ow2.asm`](https://central.sonatype.com/artifact/org.ow2.asm/asm/9.7.1) | `asm` | 9.7.1 | test | BSD-3-Clause |
| [`org.skyscreamer`](https://central.sonatype.com/artifact/org.skyscreamer/jsonassert/1.5.3) | `jsonassert` | 1.5.3 | test | Apache-2.0 |
| [`org.slf4j`](https://central.sonatype.com/artifact/org.slf4j/jul-to-slf4j/2.0.20) | `jul-to-slf4j`, `slf4j-api` | 2.0.20 | compile | MIT |
| [`org.springframework`](https://central.sonatype.com/artifact/org.springframework/spring-aop/7.0.10) | `spring-aop`, `spring-aspects`, `spring-beans`, `spring-context`, `spring-core`, `spring-expression`, `spring-jdbc`, `spring-orm`, `spring-tx`, `spring-web`, `spring-webmvc` | 7.0.10 | compile | Apache-2.0 |
| [`org.springframework`](https://central.sonatype.com/artifact/org.springframework/spring-test/7.0.10) | `spring-test` | 7.0.10 | test | Apache-2.0 |
| [`org.springframework.boot`](https://central.sonatype.com/artifact/org.springframework.boot/spring-boot-autoconfigure/4.1.2) | `spring-boot-autoconfigure`, `spring-boot-data-commons`, `spring-boot-data-jpa`, `spring-boot-flyway`, `spring-boot-hibernate`, `spring-boot-http-converter`, `spring-boot-jackson`, `spring-boot-jdbc`, `spring-boot-jpa`, `spring-boot-persistence`, `spring-boot-security-oauth2-resource-server`, `spring-boot-security`, `spring-boot-servlet`, `spring-boot-sql`, `spring-boot-starter-data-jpa`, `spring-boot-starter-flyway`, `spring-boot-starter-jackson`, `spring-boot-starter-jdbc`, `spring-boot-starter-logging`, `spring-boot-starter-security-oauth2-resource-server`, `spring-boot-starter-security`, `spring-boot-starter-tomcat-runtime`, `spring-boot-starter-tomcat`, `spring-boot-starter-validation`, `spring-boot-starter-webmvc`, `spring-boot-starter`, `spring-boot-tomcat`, `spring-boot-transaction`, `spring-boot-validation`, `spring-boot-web-server`, `spring-boot-webmvc`, `spring-boot` | 4.1.2 | compile | Apache-2.0 |
| [`org.springframework.boot`](https://central.sonatype.com/artifact/org.springframework.boot/spring-boot-resttestclient/4.1.2) | `spring-boot-resttestclient`, `spring-boot-starter-jackson-test`, `spring-boot-starter-test`, `spring-boot-starter-webmvc-test`, `spring-boot-test-autoconfigure`, `spring-boot-test`, `spring-boot-webmvc-test` | 4.1.2 | test | Apache-2.0 |
| [`org.springframework.data`](https://central.sonatype.com/artifact/org.springframework.data/spring-data-commons/4.1.2) | `spring-data-commons`, `spring-data-jpa` | 4.1.2 | compile | Apache-2.0 |
| [`org.springframework.security`](https://central.sonatype.com/artifact/org.springframework.security/spring-security-config/7.1.2) | `spring-security-config`, `spring-security-core`, `spring-security-crypto`, `spring-security-oauth2-core`, `spring-security-oauth2-jose`, `spring-security-oauth2-resource-server`, `spring-security-web` | 7.1.2 | compile | Apache-2.0 |
| [`org.xmlunit`](https://central.sonatype.com/artifact/org.xmlunit/xmlunit-core/2.11.0) | `xmlunit-core` | 2.11.0 | test | Apache-2.0 |
| [`org.yaml`](https://central.sonatype.com/artifact/org.yaml/snakeyaml/2.6) | `snakeyaml` | 2.6 | compile | Apache-2.0 |
| [`tools.jackson.core`](https://central.sonatype.com/artifact/tools.jackson.core/jackson-core/3.1.8) | `jackson-core`, `jackson-databind` | 3.1.8 | compile | Apache-2.0 |

</details>

## Dependencies retrieved by npm

The versions below come from `frontend/package-lock.json` and all locked package archives are sourced from the npm registry. The checkout includes dependency manifests for restoring these packages. The license files shipped in individual npm packages supply the authoritative attributions and any bundled third-party terms.

| Direct dependency | Locked version | Declared license | Use |
| --- | --- | --- | --- |
| [`@types/node`](https://www.npmjs.com/package/@types/node/v/24.19.2) | 24.19.2 | MIT | type checking |
| [`@types/react`](https://www.npmjs.com/package/@types/react/v/19.3.0) | 19.3.0 | MIT | type checking |
| [`@types/react-dom`](https://www.npmjs.com/package/@types/react-dom/v/19.3.0) | 19.3.0 | MIT | type checking |
| [`lucide-react`](https://www.npmjs.com/package/lucide-react/v/1.79.0) | 1.79.0 | ISC and MIT for Feather-derived icons | runtime |
| [`playwright-core`](https://www.npmjs.com/package/playwright-core/v/1.65.1) | 1.65.1 | Apache-2.0 | browser tests |
| [`react`](https://www.npmjs.com/package/react/v/19.3.1) | 19.3.1 | MIT | runtime |
| [`react-dom`](https://www.npmjs.com/package/react-dom/v/19.3.1) | 19.3.1 | MIT | runtime |
| [`react-router-dom`](https://www.npmjs.com/package/react-router-dom/v/7.18.6) | 7.18.6 | MIT | runtime |
| [`typescript`](https://www.npmjs.com/package/typescript/v/5.9.3) | 5.9.3 | Apache-2.0 | type checking |
| [`vite`](https://www.npmjs.com/package/vite/v/8.3.8) | 8.3.8 | MIT | build and development |

React, React DOM and Scheduler credit Meta Platforms, Inc. and affiliates. React Router and React Router DOM credit React Training LLC (2015–2019), Remix Software Inc. (2020–2021) and Shopify Inc. (2022–2023). Vite credits VoidZero Inc. and Vite contributors (2019–present). Playwright's distribution includes Microsoft's copyright notice and third-party notices. TypeScript's distribution includes its own third-party notices. [React 19.3.1](https://www.npmjs.com/package/react/v/19.3.1) · [React Router 7.18.6](https://www.npmjs.com/package/react-router/v/7.18.6) · [Vite 8.3.8](https://www.npmjs.com/package/vite/v/8.3.8) · [Playwright Core 1.65.1](https://www.npmjs.com/package/playwright-core/v/1.65.1) · [TypeScript 5.9.3](https://www.npmjs.com/package/typescript/v/5.9.3)

<details>
<summary>Transitive npm lockfile entries</summary>

| Package | Locked version | Declared license | Lockfile scope |
| --- | --- | --- | --- |
| [`@emnapi/core`](https://www.npmjs.com/package/@emnapi/core/v/2.0.0-beta.2) | 2.0.0-beta.2 | MIT | dev (optional) |
| [`@emnapi/runtime`](https://www.npmjs.com/package/@emnapi/runtime/v/2.0.0-beta.2) | 2.0.0-beta.2 | MIT | dev (optional) |
| [`@emnapi/wasi-threads`](https://www.npmjs.com/package/@emnapi/wasi-threads/v/2.2.0) | 2.2.0 | MIT | dev (optional) |
| [`@napi-rs/wasm-runtime`](https://www.npmjs.com/package/@napi-rs/wasm-runtime/v/1.2.6) | 1.2.6 | MIT | dev (optional) |
| [`@oxc-project/types`](https://www.npmjs.com/package/@oxc-project/types/v/0.156.0) | 0.156.0 | MIT | dev |
| [`@rolldown/binding-android-arm-eabi`](https://www.npmjs.com/package/@rolldown/binding-android-arm-eabi/v/1.2.19) | 1.2.19 | MIT | dev (optional) |
| [`@rolldown/binding-android-arm64`](https://www.npmjs.com/package/@rolldown/binding-android-arm64/v/1.2.19) | 1.2.19 | MIT | dev (optional) |
| [`@rolldown/binding-darwin-arm64`](https://www.npmjs.com/package/@rolldown/binding-darwin-arm64/v/1.2.19) | 1.2.19 | MIT | dev (optional) |
| [`@rolldown/binding-darwin-x64`](https://www.npmjs.com/package/@rolldown/binding-darwin-x64/v/1.2.19) | 1.2.19 | MIT | dev (optional) |
| [`@rolldown/binding-freebsd-x64`](https://www.npmjs.com/package/@rolldown/binding-freebsd-x64/v/1.2.19) | 1.2.19 | MIT | dev (optional) |
| [`@rolldown/binding-linux-arm-gnueabihf`](https://www.npmjs.com/package/@rolldown/binding-linux-arm-gnueabihf/v/1.2.19) | 1.2.19 | MIT | dev (optional) |
| [`@rolldown/binding-linux-arm64-gnu`](https://www.npmjs.com/package/@rolldown/binding-linux-arm64-gnu/v/1.2.19) | 1.2.19 | MIT | dev (optional) |
| [`@rolldown/binding-linux-arm64-musl`](https://www.npmjs.com/package/@rolldown/binding-linux-arm64-musl/v/1.2.19) | 1.2.19 | MIT | dev (optional) |
| [`@rolldown/binding-linux-ppc64-gnu`](https://www.npmjs.com/package/@rolldown/binding-linux-ppc64-gnu/v/1.2.19) | 1.2.19 | MIT | dev (optional) |
| [`@rolldown/binding-linux-s390x-gnu`](https://www.npmjs.com/package/@rolldown/binding-linux-s390x-gnu/v/1.2.19) | 1.2.19 | MIT | dev (optional) |
| [`@rolldown/binding-linux-x64-gnu`](https://www.npmjs.com/package/@rolldown/binding-linux-x64-gnu/v/1.2.19) | 1.2.19 | MIT | dev (optional) |
| [`@rolldown/binding-linux-x64-musl`](https://www.npmjs.com/package/@rolldown/binding-linux-x64-musl/v/1.2.19) | 1.2.19 | MIT | dev (optional) |
| [`@rolldown/binding-openharmony-arm64`](https://www.npmjs.com/package/@rolldown/binding-openharmony-arm64/v/1.2.19) | 1.2.19 | MIT | dev (optional) |
| [`@rolldown/binding-wasm32-wasi`](https://www.npmjs.com/package/@rolldown/binding-wasm32-wasi/v/1.2.19) | 1.2.19 | MIT | dev (optional) |
| [`@rolldown/binding-win32-arm64-msvc`](https://www.npmjs.com/package/@rolldown/binding-win32-arm64-msvc/v/1.2.19) | 1.2.19 | MIT | dev (optional) |
| [`@rolldown/binding-win32-x64-msvc`](https://www.npmjs.com/package/@rolldown/binding-win32-x64-msvc/v/1.2.19) | 1.2.19 | MIT | dev (optional) |
| [`@rolldown/pluginutils`](https://www.npmjs.com/package/@rolldown/pluginutils/v/1.1.1) | 1.1.1 | MIT | dev |
| [`@tybys/wasm-util`](https://www.npmjs.com/package/@tybys/wasm-util/v/0.10.5) | 0.10.5 | MIT | dev (optional) |
| [`cookie`](https://www.npmjs.com/package/cookie/v/1.1.1) | 1.1.1 | MIT | runtime |
| [`csstype`](https://www.npmjs.com/package/csstype/v/3.2.4) | 3.2.4 | MIT | runtime |
| [`detect-libc`](https://www.npmjs.com/package/detect-libc/v/2.1.2) | 2.1.2 | Apache-2.0 | dev |
| [`fdir`](https://www.npmjs.com/package/fdir/v/6.5.0) | 6.5.0 | MIT | dev |
| [`fsevents`](https://www.npmjs.com/package/fsevents/v/2.3.3) | 2.3.3 | MIT | dev (optional) |
| [`lightningcss`](https://www.npmjs.com/package/lightningcss/v/1.33.0) | 1.33.0 | MPL-2.0 | dev |
| [`lightningcss-android-arm64`](https://www.npmjs.com/package/lightningcss-android-arm64/v/1.33.0) | 1.33.0 | MPL-2.0 | dev (optional) |
| [`lightningcss-darwin-arm64`](https://www.npmjs.com/package/lightningcss-darwin-arm64/v/1.33.0) | 1.33.0 | MPL-2.0 | dev (optional) |
| [`lightningcss-darwin-x64`](https://www.npmjs.com/package/lightningcss-darwin-x64/v/1.33.0) | 1.33.0 | MPL-2.0 | dev (optional) |
| [`lightningcss-freebsd-x64`](https://www.npmjs.com/package/lightningcss-freebsd-x64/v/1.33.0) | 1.33.0 | MPL-2.0 | dev (optional) |
| [`lightningcss-linux-arm-gnueabihf`](https://www.npmjs.com/package/lightningcss-linux-arm-gnueabihf/v/1.33.0) | 1.33.0 | MPL-2.0 | dev (optional) |
| [`lightningcss-linux-arm64-gnu`](https://www.npmjs.com/package/lightningcss-linux-arm64-gnu/v/1.33.0) | 1.33.0 | MPL-2.0 | dev (optional) |
| [`lightningcss-linux-arm64-musl`](https://www.npmjs.com/package/lightningcss-linux-arm64-musl/v/1.33.0) | 1.33.0 | MPL-2.0 | dev (optional) |
| [`lightningcss-linux-x64-gnu`](https://www.npmjs.com/package/lightningcss-linux-x64-gnu/v/1.33.0) | 1.33.0 | MPL-2.0 | dev (optional) |
| [`lightningcss-linux-x64-musl`](https://www.npmjs.com/package/lightningcss-linux-x64-musl/v/1.33.0) | 1.33.0 | MPL-2.0 | dev (optional) |
| [`lightningcss-win32-arm64-msvc`](https://www.npmjs.com/package/lightningcss-win32-arm64-msvc/v/1.33.0) | 1.33.0 | MPL-2.0 | dev (optional) |
| [`lightningcss-win32-x64-msvc`](https://www.npmjs.com/package/lightningcss-win32-x64-msvc/v/1.33.0) | 1.33.0 | MPL-2.0 | dev (optional) |
| [`nanoid`](https://www.npmjs.com/package/nanoid/v/3.3.20) | 3.3.20 | MIT | dev |
| [`picocolors`](https://www.npmjs.com/package/picocolors/v/1.1.1) | 1.1.1 | ISC | dev |
| [`picomatch`](https://www.npmjs.com/package/picomatch/v/4.0.7) | 4.0.7 | MIT | dev |
| [`postcss`](https://www.npmjs.com/package/postcss/v/8.5.33) | 8.5.33 | MIT | dev |
| [`react-router`](https://www.npmjs.com/package/react-router/v/7.18.6) | 7.18.6 | MIT | runtime |
| [`rolldown`](https://www.npmjs.com/package/rolldown/v/1.2.19) | 1.2.19 | MIT | dev |
| [`scheduler`](https://www.npmjs.com/package/scheduler/v/0.28.0) | 0.28.0 | MIT | runtime |
| [`set-cookie-parser`](https://www.npmjs.com/package/set-cookie-parser/v/2.7.3) | 2.7.3 | MIT | runtime |
| [`source-map-js`](https://www.npmjs.com/package/source-map-js/v/1.2.4) | 1.2.4 | BSD-3-Clause | dev |
| [`tinyglobby`](https://www.npmjs.com/package/tinyglobby/v/0.2.17) | 0.2.17 | MIT | dev |
| [`tslib`](https://www.npmjs.com/package/tslib/v/2.9.1) | 2.9.1 | 0BSD | dev (optional) |
| [`undici-types`](https://www.npmjs.com/package/undici-types/v/7.24.6) | 7.24.6 | MIT | dev |

</details>

## Redistribution

- The intended release is a source checkout. Maven and npm restore packages from their upstream registries. The Maven Wrapper bootstrap is restored locally and excluded from source control. Normal application archives, migration-tool binaries with `lib/`, Maven distributions and caches, `node_modules/` and browser build output stay outside this source release.
- A later binary or hosted frontend distribution needs a review against the exact files delivered. Preserve the applicable license, copyright, disclaimer and `NOTICE` texts for embedded components, including MIT, ISC, BSD and Apache-2.0 components as applicable. Check covered-source obligations for any MPL, EPL, LGPL or GPL components actually redistributed and retain package-specific exceptions and third-party notices. Vite and its build-tool dependencies appear in the source dependency inventory. Browser bundles require their own inventory of embedded code and graphics. [Apache License 2.0 §4](https://www.apache.org/licenses/LICENSE-2.0) · [MIT License](https://opensource.org/license/mit) · [Mozilla Public License 2.0 §3](https://www.mozilla.org/en-US/MPL/2.0/)
- MySQL Connector/J 26.10.0 Community carries Oracle's copyright notice and GPLv2 terms. Oracle gives specific additional permission for linking to separately licensed software designated in its distribution or documentation. The Universal FOSS Exception 1.0 also provides conditional interoperability permissions for qualifying software distributed with complete corresponding source under a recognized free or open-source license. Any redistribution of Connector/J itself still requires its license and notices and compliance with the applicable GPLv2 source-distribution terms. A later application or migrator package containing Connector/J would need its own review based on the final project license and bundle contents. The H2 demo packaging profile excludes Connector/J and Flyway MySQL from the demo JAR. [Connector/J 26.10.0 Community license](https://github.com/mysql/mysql-connector-j/blob/26.10.0/LICENSE) · [Oracle Universal FOSS Exception 1.0](https://oss.oracle.com/licenses/universal-foss-exception/)
- These third-party notices convey the upstream terms for the components they cover.

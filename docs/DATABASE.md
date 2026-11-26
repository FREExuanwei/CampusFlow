# Database profiles and migrations · 数据库配置与迁移

[English](#english) · [简体中文](#简体中文)

## English

| Profile or tool | Database and purpose |
| --- | --- |
| `demo` | Loopback application on port 18087. Starts a fresh in-memory H2 database, applies V1–V8, and seeds fictional users and activities. |
| `mysql-dev` | Optional application profile for a MySQL development schema you manage. Validates the schema at startup. The current app requires V8. |
| `mysql-test` | Restricted application profile for the dedicated local test schema `campusflow_test` on `127.0.0.1:3306`. It uses the example application account `campusflow_example_app` and runs on port 18086. Use this profile only with an isolated test instance. |
| Standalone V6 migrator | Independent Flyway application that applies only V1–V6 to the dedicated local test schema, using the separate example migration account `campusflow_example_migrator`. |

The complete migration chain lives in [src/main/resources/db/migration](../src/main/resources/db/migration). Previous acceptance on isolated MySQL 9.7.2 covered V1–V6 and their recorded checksums, schema and role verification, 16 groups of HTTP checks, an application restart, and SQL verification. V7 adds activity categories and V8 adds a user token version. Both have been verified on H2 only. The full v0.2.0 MySQL deployment remains unverified.

### Configure a separate MySQL development database

1. Use a dedicated development instance and a least-privilege application account. Provision the schema separately and confirm that it has been migrated through V8 in that environment. Keep the schema-management identity separate from the application identity.
2. Copy [application-local.example.yml](../application-local.example.yml) to `application-local.yml` at the repository root. For `mysql-dev`, fill in `campusflow.local.database.dev-jdbc-url`, `dev-app-user`, and `dev-app-password` under `campusflow.local.database`. Keep the credentials out of the JDBC URL and review the TLS settings for your server.
3. Set `campusflow.local.jwt.signing-key-base64` to an independently generated Base64 key representing at least 32 cryptographically random bytes. For the isolated `mysql-test` profile, use its separate `test-app-password` and `test-signing-key-base64` settings. Keep local config and passwords out of version control.
4. After preparing the schema and verifying the account, build the standard application package with the [build commands](BUILD.md). From the repository root, `java -jar target/campusflow-0.2.0.jar --spring.profiles.active=mysql-dev` starts the optional loopback application on port 8080.

MySQL application profiles use Hibernate schema validation. Keep application-side Flyway disabled and perform reviewed migrations separately. The Windows H2 launcher is the entry point for the self-contained demo.

### Verify the standalone migrator offline

Use the [migrator guide](../tools/campusflow-test-migrator/README.md) for portable build and test commands. The packaged launcher offers `-CheckOnly`, which validates the bundle and approved SQL resources before any credential read or database connection. The migrator enforces a fixed local destination and a limited migration set. Use the offline check with ordinary development copies and reserve execution for a separately approved isolated test instance.

## 简体中文

| 配置或工具 | 数据库与用途 |
| --- | --- |
| `demo` | 应用在本机回环地址的 18087 端口运行。启动时新建 H2 内存数据库，执行 V1–V8 并生成虚构用户和活动。 |
| `mysql-dev` | 可选的 MySQL 开发配置，使用自行管理的开发库。应用启动时校验数据库结构，当前应用要求 V8。 |
| `mysql-test` | 受限的应用测试配置，只用于 `127.0.0.1:3306` 上的专用测试库 `campusflow_test`。示例应用账号为 `campusflow_example_app`，应用监听 18086 端口。仅在隔离的测试实例中使用该配置。 |
| 独立 V6 迁移器 | 独立 Flyway 应用，仅对专用本地测试库运行 V1–V6，使用独立的示例迁移账号 `campusflow_example_migrator`。 |

完整迁移链位于 [src/main/resources/db/migration](../src/main/resources/db/migration)。此前在隔离 MySQL 9.7.2 中验收了 V1–V6 和历史校验和、数据库结构与角色、16 组 HTTP 检查、应用重启和 SQL 复核。V7 增加活动分类，V8 增加用户令牌版本，两者仅在 H2 上完成验证。完整 v0.2.0 的 MySQL 部署仍待验证。

### 配置独立的 MySQL 开发库

1. 使用专用开发实例和最小权限应用账号。单独准备数据库结构，并确认已在该环境中迁移到 V8。将数据库结构管理身份与应用账号分开。
2. 将 [application-local.example.yml](../application-local.example.yml) 复制为项目根目录下的 `application-local.yml`。为 `mysql-dev` 填写 `campusflow.local.database` 下的 `dev-jdbc-url`、`dev-app-user` 和 `dev-app-password`。凭据应单独填写，避免放进 JDBC URL，同时复核服务器的 TLS 设置。
3. 为 `campusflow.local.jwt.signing-key-base64` 生成独立的 Base64 密钥，解码后至少包含 32 字节的密码学安全随机数据。隔离的 `mysql-test` 配置使用单独的 `test-app-password` 和 `test-signing-key-base64`。请勿将本机配置和密码提交到版本控制。
4. 完成结构准备和账号检查后，按[构建指南](BUILD.md)构建标准应用包。在项目根目录运行 `java -jar target/campusflow-0.2.0.jar --spring.profiles.active=mysql-dev`，应用将监听本地回环地址的 8080 端口。

MySQL 应用配置使用 Hibernate 校验数据库结构。保持应用内 Flyway 禁用，通过独立流程执行经过审核的迁移。自包含的 H2 演示使用 Windows 演示启动器。

### 离线验证独立迁移器

可移植的构建与测试命令见[迁移器指南](../tools/campusflow-test-migrator/README.md)。打包后的启动器支持 `-CheckOnly`，可在读取凭据或连接数据库前校验软件包和已审核的 SQL 资源。迁移器限定本地目标和迁移版本范围。普通开发副本可使用离线检查，实际运行迁移需要单独批准的隔离测试实例。

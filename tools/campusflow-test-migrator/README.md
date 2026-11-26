# CampusFlow standalone V6 test migrator · 独立 V6 测试迁移器

## 1. Scope · 适用范围

This guarded Flyway tool packages CampusFlow v0.2.0 migrations V1–V6 for a dedicated, isolated MySQL test instance. The current application requires V8. V7 and V8 have only been verified on H2.

该 Flyway 工具带有安全保护，打包 CampusFlow v0.2.0 的 V1–V6 迁移，供专用隔离 MySQL 测试实例使用。当前应用需要 V8，V7、V8 仅在 H2 上完成验证。

## 2. Toolchain · 工具链

Use JDK 25. The packaging and launcher scripts require PowerShell 7.4 or later. Set `JAVA_HOME` to the JDK 25 installation when running the packaging scripts. Use the Maven Wrapper from the repository root.

需要 JDK 25。打包和启动脚本要求 PowerShell 7.4 或更高版本。执行打包脚本时，请将 `JAVA_HOME` 指向 JDK 25 安装目录，并从项目根目录使用 Maven Wrapper。

## 3. Test · 运行测试

Run these tests without a MySQL service. The first command uses the Windows wrapper. The second uses the macOS or Linux wrapper.

运行测试无需 MySQL 服务。第一条命令使用 Windows Wrapper，第二条适用于 macOS 或 Linux。

```text
.\mvnw.cmd -f tools/campusflow-test-migrator/pom.xml test
./mvnw -f tools/campusflow-test-migrator/pom.xml test
```

## 4. Package and preflight · 打包与离线预检

From the repository root on Windows, use the following commands. Packaging verifies the SQL and runtime dependency hashes and runs the offline preflight. You can repeat the check against the generated bundle.

在 Windows 上从项目根目录执行以下命令。打包会核对 SQL 和运行依赖的哈希并执行离线预检。可对生成的软件包再次运行离线检查。

```powershell
pwsh -NoProfile -File .\tools\campusflow-test-migrator\Build.ps1
pwsh -NoProfile -File .\artifacts\campusflow-migrator-v6-mysql-final-20261122\Start-TestMigrator.ps1 -CheckOnly
```

## 5. Isolated target · 隔离目标

The target is fixed to `campusflow_test` on `127.0.0.1:3306` and requires TLS. The migration identity is `campusflow_example_migrator`, separate from the application identity `campusflow_example_app`. A disposable instance and confirmation that the target schema is empty are required before an actual migration. Use `-CheckOnly` for local bundle verification.

目标固定为 `127.0.0.1:3306` 上的 `campusflow_test`，并要求 TLS。示例迁移账号 `campusflow_example_migrator` 与示例应用账号 `campusflow_example_app` 分开。实际迁移前需要准备可丢弃的测试实例，并确认目标库为空。验证本地软件包请使用 `-CheckOnly`。

## 6. Credentials · 凭据

Offline preflight reads the approved migrations and bundle files. An approved online run reads its password from the repository-relative `config/campusflow-test-migrator.password`, a private, single-line UTF-8 file. Keep the password file outside the bundle and out of version control.

离线预检读取审核通过的迁移资源和软件包文件。经批准的在线运行从项目根目录下的 `config/campusflow-test-migrator.password` 读取密码，该文件应为私密的单行 UTF-8 文件。请勿将密码文件放入软件包或提交到版本控制。

## 7. Read-only follow-up · 只读复核

If verification reports `ADMIN_REQUIRED`, `MISMATCH`, or `UNAVAILABLE`, preserve the test instance state. Have the database administrator verify the target and TLS session, schema objects, migration history and checksums, seed roles, and effective grants with read-only inspection. Resolve any discrepancy before further changes.

如果验核返回 `ADMIN_REQUIRED`、`MISMATCH` 或 `UNAVAILABLE`，请保留测试实例现状。由数据库管理员以只读方式复核目标与 TLS 会话、数据库对象、迁移历史与校验和、基础角色及有效授权。处理差异后再作进一步变更。

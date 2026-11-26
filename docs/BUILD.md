# Build and test · 构建与测试

[English](#english) · [简体中文](#简体中文)

## English

Use JDK 25, Node.js 20.19 or later in the 20.x series or Node.js 22.12 or later, and npm. The Maven Wrapper pins Maven 3.9.16. On a fresh checkout the wrapper and dependency commands may download files from the configured Maven and npm repositories.

### Backend and migrator

Run from the repository root in Windows PowerShell. On macOS and Linux, replace `.\mvnw.cmd` with `./mvnw`.

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
.\mvnw.cmd -Pdemo package
.\mvnw.cmd -f tools/campusflow-test-migrator/pom.xml test
```

The standard application package is `target/campusflow-0.2.0.jar` and includes the MySQL connector and Flyway MySQL plugin. The Maven `demo` profile produces `target/demo-build/campusflow-demo.jar` with H2 at runtime. For a running backend, use the Windows [demo launcher](LOCAL-DEMO.md). The standalone migrator test command exercises its checks and workflow in the test suite. To build and preflight its runnable bundle, see the [migrator guide](../tools/campusflow-test-migrator/README.md).

### Frontend

```powershell
npm --prefix frontend ci
npm --prefix frontend run typecheck
npm --prefix frontend run build
```

`npm ci` restores dependencies from `frontend/package-lock.json`. The build runs TypeScript checking before bundling to `frontend/dist/`. For interactive development on Windows, start the H2 backend first and run the frontend in another terminal.

```powershell
.\scripts\Start-CampusFlow-Demo.cmd -BackendOnly
npm --prefix frontend run dev
```

Vite serves the UI at [127.0.0.1:5173](http://127.0.0.1:5173) and forwards `/api/v1` to the demo API on port 18087. Stop the manually started frontend with `Ctrl+C` and run `.\scripts\Stop-CampusFlow-Demo.cmd` to stop the backend.

### Browser acceptance

Start the complete H2 demo with `.\scripts\Start-CampusFlow-Demo.cmd`. The acceptance script uses an installed Microsoft Edge in headless mode and verifies that both loopback endpoints identify the backend as `H2_DEMO` before testing. Start with a fresh demo because the suite adds test records. Run from the repository root.

```powershell
npm --prefix frontend run test:e2e
```

The suite covers registration and login, role and resource isolation, activity management, enrollment and cancellation, notifications, user status changes, audit records, and a mobile viewport. Screenshots are saved under `.campusflow-demo/acceptance/`. Stop the demo when finished to clear the in-memory data. Keep runtime files and raw test output local.

## 简体中文

需要 JDK 25、Node.js 20.19 及以上的 20.x 版本或 Node.js 22.12 及以上版本，以及 npm。Maven Wrapper 固定使用 Maven 3.9.16。首次获取源码后，Wrapper 和依赖恢复命令可能从配置的 Maven 和 npm 仓库下载文件。

### 后端和迁移器

在项目根目录的 Windows PowerShell 中执行。使用 macOS 或 Linux 时，将 `.\mvnw.cmd` 换为 `./mvnw`。

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
.\mvnw.cmd -Pdemo package
.\mvnw.cmd -f tools/campusflow-test-migrator/pom.xml test
```

标准应用构建产物为 `target/campusflow-0.2.0.jar`。Maven 的 `demo` 配置生成 `target/demo-build/campusflow-demo.jar`，包含运行时 H2，排除 MySQL 驱动和 Flyway MySQL 插件。使用 Windows [演示启动器](LOCAL-DEMO.md)运行后端。独立迁移器的测试命令用于检查其保护机制和工作流。构建并离线预检可运行的软件包，见[迁移器指南](../tools/campusflow-test-migrator/README.md)。

### 前端

```powershell
npm --prefix frontend ci
npm --prefix frontend run typecheck
npm --prefix frontend run build
```

`npm ci` 根据 `frontend/package-lock.json` 恢复依赖。生产构建先执行 TypeScript 检查，再将前端打包至 `frontend/dist/`。在 Windows 上交互开发时，先启动 H2 后端，再在另一个终端启动前端。

```powershell
.\scripts\Start-CampusFlow-Demo.cmd -BackendOnly
npm --prefix frontend run dev
```

Vite 在 [127.0.0.1:5173](http://127.0.0.1:5173) 提供应用界面，将 `/api/v1` 转发到 18087 端口的演示 API。按 `Ctrl+C` 停止手动启动的前端，再运行 `.\scripts\Stop-CampusFlow-Demo.cmd` 停止后端。

### 浏览器验收

通过 `.\scripts\Start-CampusFlow-Demo.cmd` 启动完整 H2 演示。验收脚本使用已安装的 Microsoft Edge，以无头模式运行。开始测试前，脚本会确认两个本地回环地址都返回 `H2_DEMO` 后端标记。测试会添加记录，请从新启动的演示开始，并在项目根目录运行。

```powershell
npm --prefix frontend run test:e2e
```

验收涵盖注册与登录、角色和资源隔离、活动管理、报名与取消、通知、账号状态、审计和移动端视口。截图保存于 `.campusflow-demo/acceptance/`。结束后停止演示即可清空内存数据。运行文件和原始测试输出仅保留在本地。

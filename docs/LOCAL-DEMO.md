# Local demo · 本地演示

[English](#english) · [简体中文](#简体中文)

## English

The Windows demo uses loopback ports 5173 and 18087 and a fresh H2 in-memory database on each backend start. Use a terminal without inherited Spring, CampusFlow, or JVM application overrides. See the [build requirements](BUILD.md) for the toolchain.

1. Run `.\scripts\Start-CampusFlow-Demo.cmd` from the repository root. The launcher builds the H2 backend and starts both servers. Open [127.0.0.1:5173](http://127.0.0.1:5173).
2. Run `.\scripts\Open-CampusFlow-Demo-Accounts.cmd` to read the generated local passwords. The fictional accounts are `demo_student`, `demo_student2`, and `demo_admin`.
3. Sign in as `demo_student`. Open **活动广场** to find a published activity with open enrollment. Enroll, check **我的活动**, then cancel and inspect **通知中心**.
4. Sign out and sign in as `demo_admin`. Open **活动管理** to create and submit a draft. Review it under **活动审核**, then publish the approved activity. Inspect the dashboard, enrollment roster, and **操作审计**.
5. Run `.\scripts\Stop-CampusFlow-Demo.cmd` after exploring. It stops the processes associated with this checkout. The H2 business data is cleared when the backend stops, and the next start recreates the fictional data.

If a port is busy, the launcher reports the conflict and exits. If startup fails, inspect the local logs under `.campusflow-demo/`. `.\scripts\Reset-CampusFlow-Demo.cmd` can clear stopped launcher state. The generated account files can be reused across demo starts. Keep passwords and runtime files local.

## 简体中文

Windows 演示使用本机回环端口 5173 和 18087，每次启动后端时都会新建 H2 内存数据库。请在没有继承 Spring、CampusFlow 或 JVM 应用配置覆盖的终端中启动。工具链要求见[构建指南](BUILD.md)。

1. 在项目根目录运行 `.\scripts\Start-CampusFlow-Demo.cmd`。启动器会构建 H2 后端并启动前后端服务。打开 [127.0.0.1:5173](http://127.0.0.1:5173)。
2. 运行 `.\scripts\Open-CampusFlow-Demo-Accounts.cmd` 查看本地生成的密码。虚构账号为 `demo_student`、`demo_student2` 和 `demo_admin`。
3. 使用 `demo_student` 登录。在 **活动广场** 找到一项开放报名的已发布活动并报名。在 **我的活动** 查看报名，再取消报名并查看 **通知中心**。
4. 退出后使用 `demo_admin` 登录。在 **活动管理** 创建并提交草稿，在 **活动审核** 完成审核，再发布已通过审核的活动。查看工作台、报名名册和 **操作审计**。
5. 体验结束后运行 `.\scripts\Stop-CampusFlow-Demo.cmd`，停止当前项目目录关联的演示进程。后端停止后 H2 业务数据清空，下次启动会重新创建虚构数据。

如果端口被占用，启动器会退出并保留原有进程。启动失败时可查看 `.campusflow-demo/` 中的本地日志。`.\scripts\Reset-CampusFlow-Demo.cmd` 可以清理已停止的启动器状态。生成的账号文件可在下次演示中继续使用。请将密码和运行文件保留在本地。

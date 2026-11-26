# Security policy · 安全政策

[English](#english) · [简体中文](#简体中文)

## English

### Scope

This policy applies to CampusFlow v0.2.0, including the local demo, application API and frontend, and standalone migrator. v0.2.0 is a local demonstration and educational release. Production deployment has yet to be validated.

### Reporting a vulnerability

If the GitHub repository is public and private vulnerability reporting has been enabled, use **Security → Report a vulnerability**. If that option is unavailable, you may open an issue asking for a private reporting channel. Keep vulnerability details out of public issues, discussions, and pull requests.

Include the affected version or commit, the component and active profile, and the expected and observed behavior. Give the shortest steps needed to reproduce the problem with fictional data and a local H2 demo or a separate environment you control. Describe the impact and include a small excerpt of relevant logs with sensitive values removed.

Replace database credentials, JWT signing keys and tokens, cookies, session IDs, authorization headers, local paths, and nonpublic hostnames with placeholders. Remove real student and administrator records, including names, IDs, contact details, audit entries, and screenshots that contain them. Check attachments and their metadata before sharing.

## 简体中文

### 适用范围

本政策适用于 CampusFlow v0.2.0，包括本地演示、应用 API 和前端，以及独立迁移器。v0.2.0 面向本地演示和教学用途，生产部署尚待验证。

### 反馈安全漏洞

如果 GitHub 仓库已经公开并启用了私密漏洞报告功能，请通过 **Security → Report a vulnerability** 提交。若此入口不可用，可以通过 Issue 请求私密反馈渠道。请勿在公开 Issue、讨论或拉取请求中披露漏洞细节。

请提供受影响的版本或提交、相关模块和启用的配置，以及预期行为和实际行为。使用虚构数据在本地 H2 演示或由你控制的独立环境中给出最简复现步骤。说明影响，并附上一小段已脱敏的相关日志。

请用占位符替换数据库凭据、JWT 签名密钥和令牌、Cookie、会话 ID、认证请求头、本机路径及非公开主机名。移除真实学生和管理员记录，包括姓名、标识符、联系方式、审计记录及含有这些信息的截图。分享前请检查附件和元数据。

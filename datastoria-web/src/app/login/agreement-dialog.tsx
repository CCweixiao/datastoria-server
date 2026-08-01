"use client";

import { useUiPreferences } from "@/components/shared/ui-preferences-provider";
import { Dialog, DialogContent, DialogDescription, DialogTitle } from "@/components/ui/dialog";
import { ScrollArea } from "@/components/ui/scroll-area";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";

export const TERMS_OF_SERVICE = `
# Terms of Service

Last Updated: July 26, 2026

## 1. Acceptance of Terms
By accessing or using DataStoria, you agree to be bound by these Terms of Service.

If you **DO NOT** agree, please **DO NOT** use the service.

## 2. Description of Service
DataStoria is an AI-native ClickHouse console. It provides database introspection, query execution, data visualization, cluster diagnostics, and AI-assisted workflows.

## 3. User Responsibilities
- You are responsible for maintaining the security of your account and the credentials configured for database and model-provider access.
- You agree **NOT** to use the service for any illegal or unauthorized purpose.
- You are responsible for the content and data processed through the service.

## 4. Privacy and Data Security
Connection credentials and model-provider API keys configured through DataStoria are stored by the Java server in encrypted form. Secrets are not returned to the browser after they are saved. See the Privacy Policy for additional details.

## 5. Limitation of Liability
DataStoria is provided "as is" without warranties. To the extent permitted by applicable law, the project and its contributors are not liable for damages arising from use of the service.

## 6. Changes to Terms
These terms may be updated as the service evolves. Continued use after an update constitutes acceptance of the revised terms.
`;

export const PRIVACY_POLICY = `
# Privacy Policy

Last Updated: July 26, 2026

## 1. Information We Process
- **Account Information:** Your deployment operator stores the username, display name, email address, role, and encrypted password required to manage your account.
- **Service Data:** Sessions, preferences, feedback, connection definitions, and model-provider configuration may be stored to provide the requested functionality.
- **Secrets:** Database credentials and model-provider API keys are submitted directly to the Java server and stored in encrypted form. Saved secret values are never returned to the browser.
- **Database and AI Context:** Schema metadata, query text, query results, and selected context may be sent to the configured model provider when you invoke AI functionality.

## 2. How Information Is Used
- To authenticate users and provide DataStoria features.
- To execute database operations and AI-assisted workflows requested by the user.
- To persist user configuration, sessions, and preferences.
- To diagnose failures and improve reliability.

## 3. Storage and Retention
The deployment operator controls the database, encryption keys, logs, backups, and retention policy. Removing a connection or provider configuration removes its active server-side record according to the deployment's storage policy.

## 4. Third-Party Services
ClickHouse deployments and configured model providers process information under their own terms and privacy policies.

## 5. Security
DataStoria uses server-side secret handling and encrypted credential storage, but no transmission or storage mechanism can guarantee absolute security. Deployment operators should protect encryption keys, restrict database access, and use TLS in production.

## 6. Contact
Contact the operator of your DataStoria deployment for privacy, retention, or deletion requests.
`;

export const TERMS_OF_SERVICE_ZH_CN = `
# 服务条款

最后更新：2026 年 7 月 26 日

## 1. 接受条款
访问或使用 DataStoria 即表示您同意受本服务条款约束。如不同意，请勿使用本服务。

## 2. 服务说明
DataStoria 是 AI 原生 ClickHouse 控制台，提供数据库浏览、查询执行、数据可视化、集群诊断和 AI 辅助工作流。

## 3. 用户责任
- 您负责保护账户以及数据库和模型服务商的访问凭据。
- 您不得将本服务用于任何违法或未经授权的用途。
- 您对通过本服务处理的内容和数据负责。

## 4. 隐私与数据安全
DataStoria 通过 Java 服务端加密存储连接凭据和模型服务商 API 密钥，保存后不会将密钥返回浏览器。详情请参阅隐私政策。

## 5. 责任限制
DataStoria 按“现状”提供且不作保证。在法律允许范围内，项目及贡献者不对使用本服务产生的损失承担责任。

## 6. 条款变更
服务演进过程中可能更新本条款。更新后继续使用即表示接受修订后的条款。
`;

export const PRIVACY_POLICY_ZH_CN = `
# 隐私政策

最后更新：2026 年 7 月 26 日

## 1. 我们处理的信息
- **账户信息：** 部署运营方会存储管理账户所需的用户名、显示名称、邮箱、角色和加密密码。
- **服务数据：** 为提供功能，可能存储会话、偏好、反馈、连接定义和模型服务商配置。
- **密钥：** 数据库凭据和模型 API 密钥直接提交至 Java 服务端并加密存储，已保存的密钥不会返回浏览器。
- **数据库与 AI 上下文：** 使用 AI 功能时，架构元数据、查询文本、查询结果及所选上下文可能发送给配置的模型服务商。

## 2. 信息用途
用于身份认证、提供功能、执行用户请求的数据库及 AI 操作、保存配置与会话，以及诊断故障和提升可靠性。

## 3. 存储与保留
部署运营方控制数据库、加密密钥、日志、备份和保留策略。删除连接或服务商配置时，将依据部署的存储策略删除其有效服务端记录。

## 4. 第三方服务
ClickHouse 部署和配置的模型服务商依据其各自条款及隐私政策处理信息。

## 5. 安全
DataStoria 使用服务端密钥处理和加密凭据存储，但任何传输或存储机制均无法保证绝对安全。生产部署应保护加密密钥、限制数据库访问并使用 TLS。

## 6. 联系方式
隐私、保留或删除请求请联系 DataStoria 部署运营方。
`;

export function AgreementDialog({
  isOpen,
  onOpenChange,
  content,
}: {
  isOpen: boolean;
  onOpenChange: (open: boolean) => void;
  content: string;
}) {
  const { t } = useUiPreferences();
  return (
    <Dialog open={isOpen} onOpenChange={onOpenChange}>
      <DialogContent className="flex h-[80vh] max-w-2xl flex-col overflow-hidden p-0">
        <DialogTitle className="sr-only">{t("login.agreement")}</DialogTitle>
        <DialogDescription className="sr-only">{t("login.agreementDescription")}</DialogDescription>
        <ScrollArea className="flex-1 p-6 pt-4">
          <div className="space-y-2 text-sm text-muted-foreground">
            <ReactMarkdown
              remarkPlugins={[remarkGfm]}
              components={{
                h1: (props) => <h1 className="pt-4 text-xl font-bold" {...props} />,
                h2: (props) => <h2 className="pt-4 text-lg font-semibold" {...props} />,
                ul: (props) => <ul className="mb-2 list-disc space-y-2 pl-6" {...props} />,
              }}
            >
              {content}
            </ReactMarkdown>
          </div>
        </ScrollArea>
      </DialogContent>
    </Dialog>
  );
}

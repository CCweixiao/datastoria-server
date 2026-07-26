"use client";

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
- **Account Information:** OAuth providers may supply basic profile information such as name, email address, and profile image.
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
OAuth identity providers, ClickHouse deployments, and configured model providers process information under their own terms and privacy policies.

## 5. Security
DataStoria uses server-side secret handling and encrypted credential storage, but no transmission or storage mechanism can guarantee absolute security. Deployment operators should protect encryption keys, restrict database access, and use TLS in production.

## 6. Contact
Contact the operator of your DataStoria deployment for privacy, retention, or deletion requests.
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
  return (
    <Dialog open={isOpen} onOpenChange={onOpenChange}>
      <DialogContent className="flex h-[80vh] max-w-2xl flex-col overflow-hidden p-0">
        <DialogTitle className="sr-only">Agreement</DialogTitle>
        <DialogDescription className="sr-only">
          DataStoria terms of service or privacy policy.
        </DialogDescription>
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

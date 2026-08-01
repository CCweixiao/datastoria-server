import { useUiPreferences } from "@/components/shared/ui-preferences-provider";
import { Dialog } from "@/components/shared/use-dialog";
import { Button } from "@/components/ui/button";
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field";
import { FieldDescription } from "@/components/ui/field-description";
import { Input } from "@/components/ui/input";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { useIsMobile } from "@/hooks/use-mobile";
import {
  backendApiFetch,
  backendApiHeaders,
  backendApiUrl,
  readBackendError,
} from "@/lib/backend-api";
import type { ConnectionConfig } from "@/lib/connection/connection-config";
import { ConnectionManager } from "@/lib/connection/connection-manager";
import { normalizeLocale, translate } from "@/lib/i18n/i18n";
import { cn } from "@/lib/utils";
import * as PopoverPrimitive from "@radix-ui/react-popover";
import { AlertCircle, CheckCircle2, Eye, EyeOff, Loader2 } from "lucide-react";
import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type ComponentPropsWithoutRef,
  type ReactNode,
} from "react";

// Type for test status
type TestStatus = { type: "success" | "error"; message: string } | null;

const PLAYGROUND_CONNECTION: ConnectionConfig = {
  name: "ClickHouse Playground",
  url: "https://play.clickhouse.com",
  user: "play",
  password: "",
  cluster: "",
  editable: true,
};

// Exported component for inline use in connection creation flows.
export function StatusPopover({
  children,
  className,
  icon,
  title,
  trigger,
  open,
  onOpenChange,
  ...props
}: ComponentPropsWithoutRef<typeof PopoverContent> & {
  icon: ReactNode;
  title: string;
  trigger: ReactNode;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  return (
    <Popover open={open} onOpenChange={onOpenChange}>
      <PopoverTrigger asChild>{trigger}</PopoverTrigger>
      <PopoverContent className={cn("p-0 overflow-hidden z-[10002]", className)} {...props}>
        <PopoverPrimitive.Arrow className={cn("fill-[var(--border)]")} width={12} height={8} />
        <div className="flex items-start gap-2 px-3 py-3">
          {icon}
          <div className="flex-1 min-w-0">
            <div className="font-semibold text-sm mb-1">{title}</div>
            {children}
          </div>
        </div>
      </PopoverContent>
    </Popover>
  );
}

export function ConnectionEditComponent({
  connection,
  onSave,
  onDelete,
  onCancel,
  isAddMode,
}: {
  connection: ConnectionConfig | null;
  onSave?: (connection: ConnectionConfig) => void;
  onDelete?: () => void;
  onCancel?: () => void;
  isAddMode: boolean;
}) {
  const { t } = useUiPreferences();
  const [isTesting, setIsTesting] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const isMobile = useIsMobile();

  // View Model
  const [name, setName] = useState(connection ? connection.name : "");
  const [cluster, setCluster] = useState(connection ? connection.cluster : "");
  const [url, setUrl] = useState(connection ? connection.url : "");
  const [user, setUser] = useState(connection ? connection.user : "");
  const [password, setPassword] = useState(connection ? connection.password : "");
  const [editable, setEditable] = useState(connection ? connection.editable : true);
  const [currentSelectedConnection, setCurrentSelectedConnection] =
    useState<ConnectionConfig | null>(connection);

  // Initialize isNameManuallyEdited: true if editing existing connection, false for new connection
  useEffect(() => {
    if (connection) {
      setIsNameManuallyEdited(true); // Existing connection name is pre-set
    } else {
      setIsNameManuallyEdited(false); // New connection, allow auto-fill
    }
  }, [connection]);

  const [apiCanceller, setAbort] = useState<AbortController>();
  const apiCancellerRef = useRef<AbortController | undefined>(undefined);

  // UI state
  const [isShowPassword, setShowPassword] = useState(false);
  const [testStatus, setTestStatus] = useState<TestStatus>(null);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);

  // Error and message state
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [isNameManuallyEdited, setIsNameManuallyEdited] = useState(false);

  const clearFieldErrors = useCallback(() => {
    setFieldErrors({});
  }, []);

  const setFieldError = useCallback((field: string, error: string) => {
    setFieldErrors((prev) => ({ ...prev, [field]: error }));
  }, []);

  const getEditingConnection = useCallback((): ConnectionConfig | undefined => {
    clearFieldErrors();

    let hasError = false;

    if (name.trim().length === 0) {
      setFieldError("name", t("connection.nameRequired"));
      hasError = true;
    }

    let cURL;
    try {
      cURL = new URL(url.trim());
    } catch {
      setFieldError("url", t("connection.invalidUrl"));
      hasError = true;
    }
    if (cURL && cURL.protocol !== "http:" && cURL.protocol !== "https:") {
      setFieldError("url", t("connection.urlProtocol"));
      hasError = true;
    }
    if (cURL && cURL.pathname === "") {
      cURL.pathname = "/";
    }

    const userText = user.trim();
    if (userText.length === 0) {
      setFieldError("user", t("connection.userRequired"));
      hasError = true;
    }

    if (hasError) {
      return;
    }

    const newConnection: ConnectionConfig = {
      name: name,
      url: cURL!.href,
      user: userText,
      password: password,
      cluster: cluster.trim(),
      editable: editable,
    };

    return newConnection;
  }, [name, cluster, url, user, password, editable, clearFieldErrors, setFieldError, t]);

  // Save handler
  const stableHandleSave = useCallback(async (): Promise<boolean> => {
    const editingConnection = getEditingConnection();
    if (editingConnection == null) {
      return false; // Keep dialog open
    }

    clearFieldErrors();

    const manager = ConnectionManager.getInstance();

    if (isAddMode) {
      // For a new connection, the name must not be in the saved connection
      if (manager.contains(editingConnection.name)) {
        setFieldError(
          "name",
          `There's already a connection with the name [${editingConnection.name}]. Please change the connection name to continue.`
        );
        return false; // Keep dialog open
      }

      await manager.add(editingConnection);
    } else {
      // edit mode
      // If name changed, the name must not be in the saved connection
      if (editingConnection.name !== currentSelectedConnection?.name) {
        if (manager.contains(editingConnection.name)) {
          setFieldError(
            "name",
            `There's already a connection with the name [${editingConnection.name}]. Please change the connection name to continue.`
          );
          return false; // Keep dialog open
        }
      }

      await manager.replace(currentSelectedConnection!.name, editingConnection);
    }

    // Get the saved connection from manager to ensure consistency
    const savedConnection = manager
      .getConnections()
      .find((conn) => conn.name === editingConnection.name);
    if (!savedConnection) {
      // This shouldn't happen, but handle gracefully
      console.error("Failed to retrieve saved connection from ConnectionManager.");
      return false; // Keep dialog open
    }

    // Call onSave with the saved connection
    // The caller (e.g., wizard) will handle setting it as the selected connection
    if (onSave) {
      onSave(savedConnection);
    }
    return true; // Close dialog
  }, [
    getEditingConnection,
    currentSelectedConnection,
    isAddMode,
    onSave,
    clearFieldErrors,
    setFieldError,
  ]);

  useEffect(() => {
    if (apiCanceller) {
      apiCancellerRef.current = apiCanceller;
    }
  }, [apiCanceller]);

  useEffect(() => {
    return () => {
      // cancel any inflight request on unmount
      apiCancellerRef.current?.abort();
    };
  }, []);

  // Helper function to get auto-generated name from URL
  const getAutoGeneratedName = useCallback((urlValue: string): string => {
    try {
      const urlObj = new URL(urlValue.trim());
      return urlObj.hostname;
    } catch {
      return "";
    }
  }, []);

  // Memoize input onChange handlers to prevent unnecessary re-renders
  const handleUrlChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const newUrl = e.target.value;
      setUrl(newUrl);
      if (fieldErrors.url) {
        setFieldError("url", "");
      }

      // Auto-fill name from URL hostname if:
      // 1. Name hasn't been manually edited, OR
      // 2. Name is empty, OR
      // 3. Name matches the previous auto-generated value
      const previousAutoName = getAutoGeneratedName(url);
      if (!isNameManuallyEdited || name.trim() === "" || name === previousAutoName) {
        try {
          const urlObj = new URL(newUrl.trim());
          const hostname = urlObj.hostname;
          // Use hostname as connection name
          const autoName = hostname;
          setName(autoName);
          setIsNameManuallyEdited(false); // Reset flag since we're auto-filling
        } catch {
          // Invalid URL, don't update name
        }
      }
    },
    [fieldErrors.url, setFieldError, isNameManuallyEdited, name, url, getAutoGeneratedName]
  );

  const handleUserChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      setUser(e.target.value);
      if (fieldErrors.user) {
        setFieldError("user", "");
      }
    },
    [fieldErrors.user, setFieldError]
  );

  const handlePasswordChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => setPassword(e.target.value),
    []
  );

  const handleClusterChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => setCluster(e.target.value),
    []
  );

  const handleNameChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      setName(e.target.value);
      setIsNameManuallyEdited(true); // Mark as manually edited
      if (fieldErrors.name) {
        setFieldError("name", "");
      }
    },
    [fieldErrors.name, setFieldError]
  );

  const handleUsePlayground = useCallback(() => {
    setCurrentSelectedConnection(PLAYGROUND_CONNECTION);
    setCluster(PLAYGROUND_CONNECTION.cluster);
    setEditable(PLAYGROUND_CONNECTION.editable);
    setName(PLAYGROUND_CONNECTION.name);
    setUrl(PLAYGROUND_CONNECTION.url);
    setUser(PLAYGROUND_CONNECTION.user);
    setPassword(PLAYGROUND_CONNECTION.password);
    setIsNameManuallyEdited(true);
    clearFieldErrors();
  }, [clearFieldErrors]);

  const renderUrlField = (
    <Field className="space-y-1">
      <div className="flex items-center gap-2">
        <FieldLabel htmlFor="url">{t("connection.url")}</FieldLabel>
        <FieldDescription className="text-xs text-muted-foreground"></FieldDescription>
      </div>
      <Input
        id="url"
        autoFocus
        placeholder="http(s)://"
        value={url}
        onChange={handleUrlChange}
        className={cn("h-10 w-full", fieldErrors.url && "border-destructive")}
      />
      {fieldErrors.url && (
        <FieldDescription className="text-destructive text-xs">{fieldErrors.url}</FieldDescription>
      )}
    </Field>
  );

  const renderPlaygroundHelper = isAddMode ? (
    <div className="mb-4 text-xs text-muted-foreground">
      {t("connection.playgroundPrefix")}{" "}
      <button
        type="button"
        className="text-primary disabled:pointer-events-none disabled:opacity-50"
        onClick={handleUsePlayground}
        disabled={isTesting || isSaving || showDeleteConfirm}
      >
        {t("connection.playgroundUse")}{" "}
        <span className="underline decoration-dotted underline-offset-4">play.clickhouse.com</span>{" "}
        {t("connection.playgroundSuffix")}
      </button>
    </div>
  ) : null;

  const renderClusterField = (
    <Field className="space-y-1">
      <div className="flex items-center gap-2">
        <FieldLabel htmlFor="cluster">{t("connection.cluster")}</FieldLabel>
        <FieldDescription className="text-xs text-muted-foreground"></FieldDescription>
      </div>
      <Input
        id="cluster"
        value={cluster}
        disabled={!editable}
        onChange={handleClusterChange}
        placeholder={t("connection.clusterPlaceholder")}
        className="h-10 w-full"
      />
    </Field>
  );

  // Test handler that manages testing state
  const handleTestConnection = useCallback(async () => {
    const testConnectionConfig = getEditingConnection();
    if (testConnectionConfig == null) {
      return;
    }

    setTestStatus(null);
    setIsTesting(true);
    const setTestResultWithDelay = (result: { type: "success" | "error"; message: string }) => {
      setTimeout(() => {
        setIsTesting(false);
        setTestStatus(result);
      }, 300);
    };

    const controller = new AbortController();
    setAbort(controller);
    try {
      const response = await backendApiFetch(backendApiUrl("/api/connections/test"), {
        method: "POST",
        headers: backendApiHeaders({ "Content-Type": "application/json" }),
        body: JSON.stringify({
          name: testConnectionConfig.name,
          url: testConnectionConfig.url,
          username: testConnectionConfig.user,
          password: testConnectionConfig.password,
          cluster: testConnectionConfig.cluster || null,
          enabled: true,
        }),
        signal: controller.signal,
      });
      if (!response.ok) {
        const { message } = await readBackendError(
          response,
          `Connection test failed: ${response.status}`
        );
        throw new Error(message);
      }
      setTestResultWithDelay({ type: "success", message: t("connection.success") });
    } catch (e) {
      if (e instanceof Error && e.name === "AbortError") {
        setIsTesting(false);
        return;
      }
      const errorMessage = e instanceof Error ? e.message : t("connection.unknownError");
      setTestResultWithDelay({
        type: "error",
        message: errorMessage,
      });
    } finally {
      setAbort(undefined);
    }
  }, [getEditingConnection, setAbort, t]);

  // Save handler
  const handleSave = useCallback(async () => {
    setIsSaving(true);
    try {
      await stableHandleSave();
      // stableHandleSave already handles calling onSave and closing
    } finally {
      setIsSaving(false);
    }
  }, [stableHandleSave]);

  const handleCancel = useCallback(() => {
    if (onCancel) {
      onCancel();
    }
  }, [onCancel]);

  const handleClose = useCallback(() => {
    handleCancel();
  }, [handleCancel]);

  const handleDeleteClick = useCallback(() => {
    setShowDeleteConfirm(true);
  }, []);

  const handleDeleteConfirm = useCallback(async () => {
    if (connection) {
      await ConnectionManager.getInstance().remove(connection.name.trim());
      setShowDeleteConfirm(false);
      if (onDelete) {
        onDelete();
      }
    }
  }, [connection, onDelete]);

  const handleDeleteCancel = useCallback(() => {
    setShowDeleteConfirm(false);
  }, []);

  // Handle ESC key to close
  useEffect(() => {
    const handleEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        handleClose();
      }
    };

    document.addEventListener("keydown", handleEscape);
    return () => {
      document.removeEventListener("keydown", handleEscape);
    };
  }, [handleClose]);

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        handleSave();
      }}
    >
      <FieldGroup className="space-y-5 sm:space-y-4 mb-5 sm:mb-4 pt-2">
        {renderPlaygroundHelper}
        {renderUrlField}
      </FieldGroup>

      <FieldGroup className="space-y-5 sm:space-y-4">
        <Field className="space-y-1">
          <div className="flex items-center gap-2">
            <FieldLabel htmlFor="user">{t("connection.user")}</FieldLabel>
            <FieldDescription className="text-xs text-muted-foreground"></FieldDescription>
          </div>
          <Input
            id="user"
            value={user}
            onChange={handleUserChange}
            placeholder={t("connection.userPlaceholder")}
            className={cn("h-10 w-full", fieldErrors.user && "border-destructive")}
          />
          {fieldErrors.user && (
            <FieldDescription className="text-destructive text-xs">
              {fieldErrors.user}
            </FieldDescription>
          )}
        </Field>

        <Field className="space-y-1">
          <div className="flex items-center gap-2">
            <FieldLabel htmlFor="password">{t("connection.password")}</FieldLabel>
            <FieldDescription className="text-xs text-muted-foreground"></FieldDescription>
          </div>
          <div className="relative w-full min-w-0">
            <Input
              id="password"
              type={isShowPassword ? "text" : "password"}
              value={password}
              placeholder={t("connection.passwordPlaceholder")}
              onChange={handlePasswordChange}
              className="h-10 w-full pr-10"
              autoComplete="current-password"
            />
            <Button
              type="button"
              variant="ghost"
              size="icon"
              className="absolute right-0 top-0 h-full px-3 py-2 hover:bg-transparent"
              onClick={() => setShowPassword((prev) => !prev)}
              disabled={showDeleteConfirm}
            >
              {isShowPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
            </Button>
          </div>
        </Field>

        {renderClusterField}

        <Field className="space-y-1">
          <div className="flex items-center gap-2">
            <FieldLabel htmlFor="name">{t("connection.displayName")}</FieldLabel>
            <FieldDescription className="text-xs text-muted-foreground"></FieldDescription>
          </div>
          <Input
            id="name"
            value={name}
            onChange={handleNameChange}
            placeholder={t("connection.displayNamePlaceholder")}
            className={cn("h-10 w-full", fieldErrors.name && "border-destructive")}
          />
          {fieldErrors.name && (
            <FieldDescription className="text-destructive text-xs">
              {fieldErrors.name}
            </FieldDescription>
          )}
        </Field>

        <FieldGroup className="pt-1">
          <Field>
            <div className="flex flex-col gap-2 pt-4 border-t sm:flex-row sm:justify-end sm:pt-6">
              <StatusPopover
                open={testStatus !== null}
                onOpenChange={(open) => !open && setTestStatus(null)}
                trigger={
                  <Button
                    type="button"
                    variant="outline"
                    className="w-full sm:w-auto sm:shrink-0"
                    onClick={handleTestConnection}
                    disabled={isTesting || isSaving || showDeleteConfirm}
                  >
                    {isTesting && <Loader2 className="h-4 w-4 mr-2 animate-spin" />}
                    {t("connection.testAction")}
                  </Button>
                }
                side={isMobile ? "top" : "left"}
                align={isMobile ? "center" : "end"}
                sideOffset={isMobile ? 4 : 0}
                className="max-w-md"
                icon={
                  testStatus?.type === "success" ? (
                    <CheckCircle2 className="h-4 w-4 mt-0.5 shrink-0 text-green-600 dark:text-green-400" />
                  ) : (
                    <AlertCircle className="h-4 w-4 mt-0.5 shrink-0 text-destructive" />
                  )
                }
                title={testStatus?.type === "success" ? t("connection.test") : t("common.error")}
              >
                <div className="text-xs whitespace-pre-wrap break-words max-h-[200px] overflow-y-auto">
                  {testStatus?.message}
                </div>
              </StatusPopover>
              {!isAddMode && onDelete && (
                <StatusPopover
                  open={showDeleteConfirm}
                  onOpenChange={setShowDeleteConfirm}
                  trigger={
                    <Button
                      type="button"
                      variant="outline"
                      className="w-full sm:w-auto sm:shrink-0"
                      onClick={handleDeleteClick}
                      disabled={isTesting || isSaving}
                    >
                      {t("connection.delete")}
                    </Button>
                  }
                  side="top"
                  align={isMobile ? "center" : "end"}
                  sideOffset={isMobile ? 4 : undefined}
                  icon={<AlertCircle className="h-4 w-4 mt-0.5 shrink-0 text-destructive" />}
                  title={t("connection.confirmDeletion")}
                >
                  <div className="text-xs mb-3">{t("connection.deleteDescription")}</div>
                  <div className="flex justify-end gap-2">
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={handleDeleteCancel}
                      disabled={isTesting || isSaving}
                    >
                      {t("common.cancel")}
                    </Button>
                    <Button
                      type="button"
                      variant="destructive"
                      size="sm"
                      onClick={handleDeleteConfirm}
                      disabled={isTesting || isSaving}
                    >
                      {t("connection.delete")}
                    </Button>
                  </div>
                </StatusPopover>
              )}
              <Button
                type="button"
                variant="outline"
                className="w-full sm:w-auto sm:shrink-0"
                onClick={handleCancel}
                disabled={isTesting || isSaving || showDeleteConfirm}
              >
                {t("common.cancel")}
              </Button>
              <Button
                type="submit"
                className="w-full sm:w-auto sm:shrink-0"
                disabled={isTesting || isSaving || showDeleteConfirm}
              >
                {t("common.save")}
              </Button>
            </div>
          </Field>
        </FieldGroup>
      </FieldGroup>
    </form>
  );
}

export interface ShowConnectionEditDialogOptions {
  connection: ConnectionConfig | null;
  onSave?: (connection: ConnectionConfig) => void;
  onDelete?: () => void;
  onCancel?: () => void;
}

export function showConnectionEditDialog(options: ShowConnectionEditDialogOptions) {
  const { connection, onSave, onDelete, onCancel } = options;
  const isAddMode = connection == null;
  const locale = normalizeLocale(
    typeof document === "undefined" ? "en" : document.documentElement.lang
  );

  const handleClose = () => {
    Dialog.close();
    if (onCancel) {
      onCancel();
    }
  };

  Dialog.showDialog({
    title: translate(locale, isAddMode ? "connection.createTitle" : "connection.modifyTitle"),
    description: translate(locale, "connection.editDescription"),
    className: "max-w-2xl",
    overlayClassName: "bg-black/85",
    mainContent: (
      <ConnectionEditComponent
        connection={connection}
        onSave={(savedConnection: ConnectionConfig) => {
          Dialog.close();
          if (onSave) {
            onSave(savedConnection);
          }
        }}
        onDelete={() => {
          Dialog.close();
          if (onDelete) {
            onDelete();
          }
        }}
        onCancel={handleClose}
        isAddMode={isAddMode}
      />
    ),
    onCancel: handleClose,
  });
}

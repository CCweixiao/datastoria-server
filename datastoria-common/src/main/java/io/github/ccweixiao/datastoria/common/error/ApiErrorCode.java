package io.github.ccweixiao.datastoria.common.error;

import java.util.Locale;

/** Stable API error codes with centrally managed English and Simplified Chinese messages. */
public enum ApiErrorCode {
  NOT_FOUND(
      404, "Resource not found", "The requested resource does not exist.", "资源不存在", "请求的资源不存在。"),
  REVISION_CONFLICT(
      409,
      "Revision conflict",
      "The resource was modified by another writer. Fetch the latest revision and retry.",
      "版本冲突",
      "资源已被其他操作修改，请获取最新版本后重试。"),
  ACTION_ALREADY_RESOLVED(
      409,
      "Action already resolved",
      "The action was already resolved with a different decision or response.",
      "操作已处理",
      "该操作已使用其他决定或响应完成处理。"),
  ACTION_EXPIRED(
      409,
      "Action expired",
      "The action expired before this response was received.",
      "操作已过期",
      "收到响应前该操作已过期。"),
  RESOURCE_IN_USE(
      409, "Resource is still in use", "The resource is still in use.", "资源正在使用", "该资源仍在使用中。"),
  PROVIDER_OPERATION_FAILED(
      503,
      "Provider operation failed",
      "The model provider operation failed.",
      "模型提供商操作失败",
      "模型提供商操作失败。"),
  CLIENT_SECRET_NOT_ALLOWED(
      400,
      "Client secret not allowed",
      "API keys must be stored server-side. Remove the secret field from the request body.",
      "不允许客户端密钥",
      "API 密钥必须存储在服务端，请从请求正文中移除密钥字段。"),
  SHARE_PERMISSION_DENIED(
      403,
      "Share visitor may not mutate this session",
      "Share codes are read-only by default.",
      "共享访客不能修改会话",
      "共享码默认仅提供只读权限。"),
  SHARE_NOT_FOUND(
      404,
      "No active share for this session",
      "The session has no active share to revoke.",
      "会话没有有效共享",
      "该会话没有可撤销的有效共享。"),
  FEEDBACK_TARGET_NOT_FOUND(
      404,
      "Referenced message does not exist",
      "Feedback references a message that is not present in this session.",
      "引用的消息不存在",
      "反馈引用的消息不在当前会话中。"),
  INVALID_REQUEST(
      400, "Invalid request", "One or more request fields are invalid.", "请求无效", "一个或多个请求字段无效。"),
  INVALID_REQUEST_FORMAT(
      400, "Invalid request format", "Invalid request format", "请求格式无效", "请求格式无效"),
  INVALID_JSON(400, "Invalid JSON", "Invalid JSON in request body", "JSON 无效", "请求正文中的 JSON 无效"),
  INVALID_LIMIT(400, "Invalid limit", "Invalid limit", "数量限制无效", "数量限制无效"),
  INVALID_CONNECTION_ID(
      400, "Invalid connection id", "Invalid connectionId", "连接 ID 无效", "连接 ID 无效"),
  INVALID_SESSION_ID(400, "Invalid session id", "Invalid sessionId", "会话 ID 无效", "会话 ID 无效"),
  INVALID_MESSAGES(400, "Invalid messages", "Invalid messages", "消息无效", "消息无效"),
  SESSION_ID_REQUIRED(400, "Session id required", "sessionId is required", "需要会话 ID", "必须提供会话 ID"),
  CONNECTION_ID_REQUIRED(
      400, "Connection id required", "connectionId is required", "需要连接 ID", "必须提供连接 ID"),
  MODEL_SELECTION_REQUIRED(
      400, "Model selection required", "Select an enabled model first", "需要选择模型", "请先选择已启用的模型"),
  MODEL_DISABLED(400, "Model disabled", "Selected model is disabled", "模型已禁用", "所选模型已禁用"),
  REPOSITORY_FILE_TOO_LARGE(
      400,
      "Repository file too large",
      "Repository file is too large to browse",
      "仓库文件过大",
      "仓库文件过大，无法浏览"),
  INVALID_FILE_RANGE(
      400, "Invalid file range", "Requested file range is invalid", "文件范围无效", "请求的文件范围无效"),
  REPOSITORY_PATH_REQUIRED(
      400, "Repository path required", "A repo-relative path is required", "需要仓库路径", "必须提供仓库相对路径"),
  REPOSITORY_PATH_OUTSIDE_ROOT(
      400,
      "Repository path outside root",
      "Repository path escapes the configured root",
      "仓库路径超出根目录",
      "仓库路径超出了配置的根目录"),
  METHOD_NOT_ALLOWED(
      405,
      "Method not allowed",
      "The requested HTTP method is not supported for this resource.",
      "请求方法不允许",
      "该资源不支持请求使用的 HTTP 方法。"),
  INTERNAL_ERROR(
      500,
      "Internal server error",
      "An unexpected error occurred. Contact support with the request id.",
      "服务器内部错误",
      "发生意外错误，请携带请求 ID 联系支持人员。"),
  AUTHENTICATION_REQUIRED(
      401, "Authentication required", "Authentication is required.", "需要身份认证", "需要完成身份认证。"),
  AUTHENTICATION_FAILED(
      401, "Authentication failed", "Invalid username or password.", "身份验证失败", "用户名或密码错误。"),
  ADMIN_ACCESS_REQUIRED(
      403,
      "Administrator access required",
      "Administrator access is required.",
      "需要管理员权限",
      "需要管理员权限才能执行此操作。"),
  QUERY_WRITE_PERMISSION_DENIED(
      403,
      "Query write permission denied",
      "Only administrators may execute DDL or data-changing SQL in Query.",
      "没有 Query 写入权限",
      "只有管理员可以在 Query 中执行 DDL 或数据变更 SQL。"),
  QUERY_UNSAFE_SQL(
      400,
      "Unsafe query rejected",
      "The query violates the server read-only safety policy.",
      "查询被安全策略拒绝",
      "该查询违反了服务端只读安全策略。"),
  ADMIN_ACCOUNT_PROTECTED(
      403,
      "Administrator account is protected",
      "This API can manage ordinary users only.",
      "管理员账户受保护",
      "此接口仅允许管理普通用户。"),
  USERNAME_ALREADY_EXISTS(
      409, "Username already exists", "The username is already in use.", "用户名已存在", "该用户名已被使用。"),
  INVALID_SHARE_CODE(403, "Invalid share code", "Invalid session share code.", "共享码无效", "会话共享码无效。"),
  CONNECTION_ID_MISMATCH(
      409,
      "Connection mismatch",
      "Session connectionId mismatch.",
      "连接不匹配",
      "会话的 connectionId 不匹配。");

  private final int status;
  private final String titleEn;
  private final String messageEn;
  private final String titleZh;
  private final String messageZh;

  ApiErrorCode(int status, String titleEn, String messageEn, String titleZh, String messageZh) {
    this.status = status;
    this.titleEn = titleEn;
    this.messageEn = messageEn;
    this.titleZh = titleZh;
    this.messageZh = messageZh;
  }

  public int status() {
    return status;
  }

  public String title(Locale locale) {
    return isChinese(locale) ? titleZh : titleEn;
  }

  public String message(Locale locale) {
    return isChinese(locale) ? messageZh : messageEn;
  }

  public static boolean isChinese(Locale locale) {
    return locale != null && "zh".equalsIgnoreCase(locale.getLanguage());
  }
}

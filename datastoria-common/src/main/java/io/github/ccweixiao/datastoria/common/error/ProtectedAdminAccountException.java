package io.github.ccweixiao.datastoria.common.error;

/** Raised when the ordinary-user management API is used on an administrator account. */
public class ProtectedAdminAccountException extends RuntimeException {}

"use client";

import { useUiPreferences } from "@/components/shared/ui-preferences-provider";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  createManagedUser,
  deleteManagedUser,
  listManagedUsers,
  resetManagedUserPassword,
  updateManagedUser,
  type ManagedUser,
} from "@/lib/user-admin-client";
import { Loader2, Pencil, Plus, Trash2 } from "lucide-react";
import { useCallback, useEffect, useState, type FormEvent } from "react";

type EditorTarget = "new" | ManagedUser | null;

export function UserManagementDialog({
  open,
  onOpenChange,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const { t, locale } = useUiPreferences();
  const [users, setUsers] = useState<ManagedUser[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [editorTarget, setEditorTarget] = useState<EditorTarget>(null);
  const [deleteTarget, setDeleteTarget] = useState<ManagedUser | null>(null);
  const [deleting, setDeleting] = useState(false);

  const loadUsers = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      setUsers(await listManagedUsers());
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : t("users.loadFailed"));
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => {
    if (open) void loadUsers();
  }, [loadUsers, open]);

  const handleSaved = useCallback((saved: ManagedUser) => {
    setUsers((current) => {
      const exists = current.some((user) => user.userId === saved.userId);
      const next = exists
        ? current.map((user) => (user.userId === saved.userId ? saved : user))
        : [...current, saved];
      return next.sort((a, b) => a.username.localeCompare(b.username));
    });
    setEditorTarget(null);
  }, []);

  const handleDelete = useCallback(async () => {
    if (!deleteTarget) return;
    setDeleting(true);
    setError("");
    try {
      await deleteManagedUser(deleteTarget.userId);
      setUsers((current) => current.filter((user) => user.userId !== deleteTarget.userId));
      setDeleteTarget(null);
    } catch (deleteError) {
      setError(deleteError instanceof Error ? deleteError.message : t("users.deleteFailed"));
    } finally {
      setDeleting(false);
    }
  }, [deleteTarget, t]);

  return (
    <>
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogContent className="flex max-h-[85vh] max-w-5xl flex-col">
          <DialogHeader>
            <DialogTitle>{t("users.title")}</DialogTitle>
            <DialogDescription>{t("users.description")}</DialogDescription>
          </DialogHeader>

          <div className="flex items-center justify-between gap-4">
            <div className="text-sm text-destructive" role="alert">
              {error}
            </div>
            <Button size="sm" onClick={() => setEditorTarget("new")}>
              <Plus className="mr-2 h-4 w-4" />
              {t("users.add")}
            </Button>
          </div>

          <div className="min-h-0 flex-1 overflow-auto rounded-md border">
            {loading ? (
              <div className="flex h-40 items-center justify-center text-muted-foreground">
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                {t("users.loading")}
              </div>
            ) : users.length === 0 ? (
              <div className="flex h-40 items-center justify-center text-sm text-muted-foreground">
                {t("users.empty")}
              </div>
            ) : (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t("users.username")}</TableHead>
                    <TableHead>{t("users.email")}</TableHead>
                    <TableHead>{t("users.status")}</TableHead>
                    <TableHead>{t("users.createdAt")}</TableHead>
                    <TableHead className="text-right">{t("common.action")}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {users.map((user) => (
                    <TableRow key={user.userId}>
                      <TableCell className="font-medium">{user.username}</TableCell>
                      <TableCell>{user.email || t("common.notAvailable")}</TableCell>
                      <TableCell>
                        <Badge variant={user.status === 1 ? "default" : "secondary"}>
                          {user.status === 1 ? t("users.active") : t("users.disabled")}
                        </Badge>
                      </TableCell>
                      <TableCell>
                        {new Intl.DateTimeFormat(locale).format(new Date(user.createdAt))}
                      </TableCell>
                      <TableCell>
                        <div className="flex justify-end gap-1">
                          <Button
                            variant="ghost"
                            size="icon"
                            aria-label={t("users.edit")}
                            onClick={() => setEditorTarget(user)}
                          >
                            <Pencil className="h-4 w-4" />
                          </Button>
                          <Button
                            variant="ghost"
                            size="icon"
                            className="text-destructive hover:text-destructive"
                            aria-label={t("users.delete")}
                            onClick={() => setDeleteTarget(user)}
                          >
                            <Trash2 className="h-4 w-4" />
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </div>
        </DialogContent>
      </Dialog>

      <UserEditorDialog
        target={editorTarget}
        onClose={() => setEditorTarget(null)}
        onSaved={handleSaved}
      />

      <Dialog open={deleteTarget !== null} onOpenChange={(next) => !next && setDeleteTarget(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("users.deleteTitle")}</DialogTitle>
            <DialogDescription>
              {t("users.deleteDescription", { username: deleteTarget?.username ?? "" })}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleteTarget(null)} disabled={deleting}>
              {t("common.cancel")}
            </Button>
            <Button variant="destructive" onClick={() => void handleDelete()} disabled={deleting}>
              {deleting ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
              {t("users.delete")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}

function UserEditorDialog({
  target,
  onClose,
  onSaved,
}: {
  target: EditorTarget;
  onClose: () => void;
  onSaved: (user: ManagedUser) => void;
}) {
  const { t } = useUiPreferences();
  const existing = target !== null && target !== "new" ? target : null;
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [active, setActive] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    setUsername(existing?.username ?? "");
    setEmail(existing?.email ?? "");
    setPassword("");
    setActive(existing ? existing.status === 1 : true);
    setError("");
  }, [existing]);

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setSaving(true);
    setError("");
    try {
      let saved: ManagedUser;
      if (existing) {
        saved = await updateManagedUser(existing.userId, {
          email: email.trim(),
          status: active ? 1 : 0,
        });
        if (password) await resetManagedUserPassword(existing.userId, password);
      } else {
        saved = await createManagedUser({
          username: username.trim(),
          email: email.trim() || undefined,
          password,
        });
      }
      onSaved(saved);
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : t("users.saveFailed"));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog open={target !== null} onOpenChange={(next) => !next && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{existing ? t("users.editTitle") : t("users.createTitle")}</DialogTitle>
          <DialogDescription>{t("users.editorDescription")}</DialogDescription>
        </DialogHeader>
        <form className="space-y-4" onSubmit={(event) => void handleSubmit(event)}>
          <div className="space-y-2">
            <Label htmlFor="managed-username">{t("users.username")}</Label>
            <Input
              id="managed-username"
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              disabled={existing !== null || saving}
              maxLength={64}
              required
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="managed-email">{t("users.email")}</Label>
            <Input
              id="managed-email"
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              disabled={saving}
              maxLength={255}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="managed-password">
              {existing ? t("users.newPassword") : t("login.password")}
            </Label>
            <Input
              id="managed-password"
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              disabled={saving}
              minLength={8}
              maxLength={256}
              required={!existing}
              autoComplete="new-password"
              placeholder={existing ? t("users.passwordUnchanged") : undefined}
            />
          </div>
          {existing ? (
            <label className="flex items-center gap-2 text-sm" htmlFor="managed-active">
              <input
                id="managed-active"
                type="checkbox"
                checked={active}
                onChange={(event) => setActive(event.target.checked)}
                disabled={saving}
                className="h-4 w-4"
              />
              {t("users.activeAccount")}
            </label>
          ) : null}
          {error ? (
            <div className="text-sm text-destructive" role="alert">
              {error}
            </div>
          ) : null}
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose} disabled={saving}>
              {t("common.cancel")}
            </Button>
            <Button type="submit" disabled={saving}>
              {saving ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
              {t("common.save")}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

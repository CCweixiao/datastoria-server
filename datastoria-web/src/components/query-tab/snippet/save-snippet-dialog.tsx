import { useUiPreferences } from "@/components/shared/ui-preferences-provider";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { normalizeLocale, translate } from "@/lib/i18n/i18n";
import { useState } from "react";
import { Dialog } from "../../shared/use-dialog";
import { QuerySnippetManager } from "../snippet/query-snippet-manager";

interface OpenSaveSnippetDialogOptions {
  initialSql?: string;
  initialName?: string;
  onSaved?: () => void;
}

function SaveSnippetForm({
  initialName,
  initialSql,
  onSaved,
}: {
  initialName: string;
  initialSql: string;
  onSaved?: () => void;
}) {
  const { t } = useUiPreferences();
  const [name, setName] = useState(initialName);
  const [sql, setSql] = useState(initialSql);
  const [error, setError] = useState<string | null>(null);

  const handleSave = () => {
    const normalizedName = name.trim();
    const normalizedSql = sql.trim();

    if (!normalizedName) {
      setError(t("snippet.nameRequired"));
      return;
    }

    if (!normalizedSql) {
      setError(t("snippet.sqlRequired"));
      return;
    }

    const manager = QuerySnippetManager.getInstance();
    if (manager.hasSnippet(normalizedName)) {
      setError(t("snippet.nameExists"));
      return;
    }

    try {
      manager.addSnippet(normalizedName, normalizedSql);
      onSaved?.();
      Dialog.close();
    } catch (saveError) {
      console.error(saveError);
      setError(t("snippet.saveFailed"));
    }
  };

  return (
    <div className="flex flex-col gap-4 py-4">
      <div className="grid gap-2">
        <Label htmlFor="name">{t("snippet.nameLabel")}</Label>
        <Input
          id="name"
          placeholder={t("snippet.nameExample")}
          value={name}
          onChange={(e) => {
            setName(e.target.value);
            setError(null);
          }}
        />
      </div>
      <div className="grid gap-2">
        <Label htmlFor="sql">SQL</Label>
        <Textarea
          id="sql"
          placeholder="SELECT * FROM ..."
          className="font-mono text-xs min-h-[150px]"
          value={sql}
          onChange={(e) => {
            setSql(e.target.value);
            setError(null);
          }}
        />
      </div>
      {error && <p className="text-sm text-destructive">{error}</p>}
      <div className="flex justify-end gap-2">
        <Button type="button" variant="outline" onClick={() => Dialog.close()}>
          {t("common.cancel")}
        </Button>
        <Button type="button" onClick={handleSave}>
          {t("common.save")}
        </Button>
      </div>
    </div>
  );
}

export function openSaveSnippetDialog({
  initialSql = "",
  initialName = "",
  onSaved,
}: OpenSaveSnippetDialogOptions = {}) {
  const locale = normalizeLocale(
    typeof document === "undefined" ? "en" : document.documentElement.lang
  );
  Dialog.showDialog({
    title: translate(locale, "snippet.saveTitle"),
    description: translate(locale, "snippet.saveHelp"),
    className: "sm:max-w-[800px]",
    mainContent: (
      <SaveSnippetForm initialName={initialName} initialSql={initialSql} onSaved={onSaved} />
    ),
  });
}

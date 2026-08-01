"use client";

import useIsDarkTheme from "@/components/shared/dashboard/use-is-dark-theme";
import { useUiPreferences } from "@/components/shared/ui-preferences-provider";
import { Button } from "@/components/ui/button";
import { CopyButton } from "@/components/ui/copy-button";
import { toastManager } from "@/lib/toast";
import { cn } from "@/lib/utils";
import * as VizlayerReact from "@vizlayer/react";
import { toChartSpec, VizlayerDiagram, type ParsedVizlayerSpec } from "@vizlayer/react";
import { AlertCircle, Download, Maximize2 } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";

interface MessageMarkdownVizlayerProps {
  spec: string;
}

type VizlayerSpec = Parameters<typeof toChartSpec>[0];

type BuiltVizlayerChart =
  | {
      ok: true;
      chart: string;
    }
  | {
      ok: false;
      error: string;
    };

type FullscreenTarget = HTMLDivElement & {
  webkitRequestFullscreen?: () => Promise<void> | void;
  mozRequestFullScreen?: () => Promise<void> | void;
  msRequestFullscreen?: () => Promise<void> | void;
};

type FullscreenDocument = Document & {
  webkitFullscreenElement?: Element | null;
  mozFullScreenElement?: Element | null;
  msFullscreenElement?: Element | null;
  webkitExitFullscreen?: () => Promise<void> | void;
  mozCancelFullScreen?: () => Promise<void> | void;
  msExitFullscreen?: () => Promise<void> | void;
};

export function MessageMarkdownVizlayer({ spec }: MessageMarkdownVizlayerProps) {
  const { t } = useUiPreferences();
  const isDark = useIsDarkTheme();
  const inlineViewportRef = useRef<HTMLDivElement>(null);
  const [isFullscreen, setIsFullscreen] = useState(false);

  const parsed = useMemo<ParsedVizlayerSpec>(() => parseVizlayerSpec(spec), [spec]);

  const chart = useMemo<BuiltVizlayerChart | null>(() => {
    if (!parsed.ok) {
      return null;
    }

    try {
      return {
        ok: true,
        chart: toChartSpec(parsed.spec),
      };
    } catch (error) {
      return {
        ok: false,
        error: error instanceof Error ? error.message : t("diagram.buildFailed"),
      };
    }
  }, [parsed, t]);

  useEffect(() => {
    const handleFullscreenChange = () => {
      const fullscreenDocument = document as FullscreenDocument;
      const fullscreenElement =
        fullscreenDocument.fullscreenElement ??
        fullscreenDocument.webkitFullscreenElement ??
        fullscreenDocument.mozFullScreenElement ??
        fullscreenDocument.msFullscreenElement ??
        null;

      setIsFullscreen(fullscreenElement === inlineViewportRef.current);
    };

    document.addEventListener("fullscreenchange", handleFullscreenChange);
    document.addEventListener("webkitfullscreenchange", handleFullscreenChange);
    document.addEventListener("mozfullscreenchange", handleFullscreenChange);
    document.addEventListener("MSFullscreenChange", handleFullscreenChange);
    return () => {
      document.removeEventListener("fullscreenchange", handleFullscreenChange);
      document.removeEventListener("webkitfullscreenchange", handleFullscreenChange);
      document.removeEventListener("mozfullscreenchange", handleFullscreenChange);
      document.removeEventListener("MSFullscreenChange", handleFullscreenChange);
    };
  }, []);

  const handleFullscreenToggle = useCallback(async () => {
    const container = inlineViewportRef.current as FullscreenTarget | null;
    if (!container) {
      return;
    }

    try {
      const fullscreenDocument = document as FullscreenDocument;
      const fullscreenElement =
        fullscreenDocument.fullscreenElement ??
        fullscreenDocument.webkitFullscreenElement ??
        fullscreenDocument.mozFullScreenElement ??
        fullscreenDocument.msFullscreenElement ??
        null;

      if (fullscreenElement === container) {
        if (fullscreenDocument.exitFullscreen) {
          await fullscreenDocument.exitFullscreen();
        } else if (fullscreenDocument.webkitExitFullscreen) {
          await fullscreenDocument.webkitExitFullscreen();
        } else if (fullscreenDocument.mozCancelFullScreen) {
          await fullscreenDocument.mozCancelFullScreen();
        } else if (fullscreenDocument.msExitFullscreen) {
          await fullscreenDocument.msExitFullscreen();
        }
      } else if (container.requestFullscreen) {
        await container.requestFullscreen();
      } else if (container.webkitRequestFullscreen) {
        await container.webkitRequestFullscreen();
      } else if (container.mozRequestFullScreen) {
        await container.mozRequestFullScreen();
      } else if (container.msRequestFullscreen) {
        await container.msRequestFullscreen();
      } else {
        throw new Error(t("diagram.fullscreenUnavailable"));
      }
    } catch {
      toastManager.show(t("diagram.fullscreenFailed"), "error");
    }
  }, [t]);

  const handleDownloadSvg = useCallback(() => {
    const viewport = inlineViewportRef.current;
    const svg = viewport?.querySelector("svg");

    if (!svg) {
      toastManager.show(t("diagram.downloadUnavailable"), "error");
      return;
    }

    try {
      const serializedSvg = new XMLSerializer().serializeToString(svg);
      const blob = new Blob([serializedSvg], { type: "image/svg+xml;charset=utf-8" });
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");

      anchor.href = url;
      anchor.download = "vizlayer-diagram.svg";
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      URL.revokeObjectURL(url);
    } catch {
      toastManager.show(t("diagram.downloadFailed"), "error");
    }
  }, [t]);

  if (!parsed.ok) {
    return (
      <div className="group relative my-2 rounded-md bg-background/40 p-3">
        <CopyButton
          value={spec}
          variant="ghost"
          size="icon"
          className="absolute top-4 right-4 z-10 h-7 w-7 rounded-sm opacity-0 transition-opacity group-hover:opacity-100 focus-visible:opacity-100 [&_svg]:h-4 [&_svg]:w-4"
          aria-label={t("diagram.copyVizlayer")}
          title={t("diagram.copyVizlayer")}
        />
        <DiagramError title={parsed.error} message={spec} />
      </div>
    );
  }

  if (chart && !chart.ok) {
    return (
      <div className="group relative my-2 rounded-md bg-background/40 p-3">
        <CopyButton
          value={spec}
          variant="ghost"
          size="icon"
          className="absolute top-4 right-4 z-10 h-7 w-7 rounded-sm opacity-0 transition-opacity group-hover:opacity-100 focus-visible:opacity-100 [&_svg]:h-4 [&_svg]:w-4"
          aria-label={t("diagram.copyVizlayer")}
          title={t("diagram.copyVizlayer")}
        />
        <DiagramError title={chart.error} message={spec} />
      </div>
    );
  }

  return (
    <div className="group relative my-2 rounded-md bg-background/40 p-3">
      <div className="absolute top-2 right-2 z-10 flex items-center gap-1 opacity-0 transition-opacity group-hover:opacity-100 group-focus-within:opacity-100">
        <CopyButton
          value={chart?.ok ? chart.chart : spec}
          variant="ghost"
          size="icon"
          className="relative !top-auto !right-auto h-6 w-6 rounded-sm [&_svg]:h-3.5 [&_svg]:w-3.5"
          aria-label={t("diagram.copyMermaid")}
          title={t("diagram.copyMermaid")}
        />
        <Button
          variant="ghost"
          size="icon"
          className="h-6 w-6 rounded-sm [&_svg]:h-3.5 [&_svg]:w-3.5"
          aria-label={isFullscreen ? t("diagram.exitFullscreen") : t("diagram.fullscreen")}
          title={isFullscreen ? t("diagram.exitFullscreen") : t("diagram.fullscreen")}
          onClick={handleFullscreenToggle}
        >
          <Maximize2 className="!h-3 !w-3" />
        </Button>
        <Button
          variant="ghost"
          size="icon"
          className="h-6 w-6 rounded-sm [&_svg]:h-3.5 [&_svg]:w-3.5"
          aria-label={t("diagram.downloadSvg")}
          title={t("diagram.downloadSvg")}
          onClick={handleDownloadSvg}
        >
          <Download className="!h-3 !w-3" />
        </Button>
      </div>

      <div ref={inlineViewportRef}>
        {renderDiagram({
          spec: parsed.spec,
          isDark,
          renderingLabel: t("diagram.rendering"),
          renderErrorTitle: t("diagram.vizlayerRenderFailed"),
          invalidDocumentTitle: t("diagram.vizlayerInvalid"),
        })}
      </div>
    </div>
  );
}

function parseVizlayerSpec(spec: string): ParsedVizlayerSpec {
  const vizlayerModule = VizlayerReact as typeof VizlayerReact & {
    Vizlayer?: {
      parse: (input: string) => ParsedVizlayerSpec;
    };
    VizlayerSpecParser?: {
      parseVizlayerSpec: (input: string) => ParsedVizlayerSpec;
    };
  };

  if (vizlayerModule.Vizlayer?.parse) {
    return vizlayerModule.Vizlayer.parse(spec);
  }

  if (vizlayerModule.VizlayerSpecParser?.parseVizlayerSpec) {
    return vizlayerModule.VizlayerSpecParser.parseVizlayerSpec(spec);
  }

  return {
    ok: false,
    error: "Unable to parse Vizlayer payload.",
  };
}

function renderDiagram({
  spec,
  isDark,
  renderingLabel,
  renderErrorTitle,
  invalidDocumentTitle,
}: {
  spec: VizlayerSpec;
  isDark: boolean;
  renderingLabel: string;
  renderErrorTitle: string;
  invalidDocumentTitle: string;
}) {
  return (
    <VizlayerDiagram
      {...spec}
      className={cn(
        "flex min-w-max justify-center overflow-x-auto",
        "[&_.edgeLabel]:fill-foreground [&_.label]:fill-foreground [&_.node_label]:fill-foreground"
      )}
      loadingFallback={<DiagramLoadingState label={renderingLabel} />}
      errorFallback={(message: string) => (
        <DiagramError title={renderErrorTitle} message={message} />
      )}
      invalidDocumentFallback={(message: string) => (
        <DiagramError title={invalidDocumentTitle} message={message} />
      )}
      theme={isDark ? "dark" : "default"}
    />
  );
}

function DiagramLoadingState({ label }: { label: string }) {
  return (
    <div className="rounded-md bg-muted/20 px-3 py-8 text-center text-sm text-muted-foreground">
      {label}
    </div>
  );
}

function DiagramError({ title, message }: { title: string; message: string }) {
  return (
    <div className="overflow-hidden rounded-md border border-destructive/40 bg-destructive/5">
      <div className="flex items-center gap-2 border-b border-destructive/20 px-3 py-2 text-sm text-destructive">
        <AlertCircle className="h-4 w-4" />
        <span>{title}</span>
      </div>
      <pre className="overflow-x-auto px-3 py-2 text-xs text-muted-foreground whitespace-pre-wrap">
        {message}
      </pre>
    </div>
  );
}

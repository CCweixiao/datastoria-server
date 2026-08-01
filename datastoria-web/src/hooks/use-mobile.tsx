import * as React from "react";

const MOBILE_BREAKPOINT = 768;
const MOBILE_MEDIA_QUERY = `(max-width: ${MOBILE_BREAKPOINT - 1}px)`;

let mediaQueryList: MediaQueryList | null = null;
const mobileViewportListeners = new Set<() => void>();

function notifyMobileViewportListeners() {
  mobileViewportListeners.forEach((listener) => listener());
}

function getMediaQueryList(): MediaQueryList | null {
  if (typeof window === "undefined") return null;
  mediaQueryList ??= window.matchMedia(MOBILE_MEDIA_QUERY);
  return mediaQueryList;
}

function subscribeToMobileViewport(onStoreChange: () => void): () => void {
  const query = getMediaQueryList();
  if (!query) return () => undefined;

  if (mobileViewportListeners.size === 0) {
    query.addEventListener("change", notifyMobileViewportListeners);
  }
  mobileViewportListeners.add(onStoreChange);

  return () => {
    mobileViewportListeners.delete(onStoreChange);
    if (mobileViewportListeners.size === 0) {
      query.removeEventListener("change", notifyMobileViewportListeners);
    }
  };
}

function getMobileSnapshot(): boolean {
  return getMediaQueryList()?.matches ?? false;
}

export function useIsMobile() {
  return React.useSyncExternalStore(subscribeToMobileViewport, getMobileSnapshot, () => false);
}

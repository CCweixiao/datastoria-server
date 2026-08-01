// @vitest-environment jsdom

import { act, renderHook } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { useIsMobile } from "./use-mobile";

describe("useIsMobile", () => {
  it("shares one media-query listener across all consumers", () => {
    const listeners = new Set<() => void>();
    const mediaQuery = {
      matches: false,
      addEventListener: vi.fn((_event: string, listener: () => void) => listeners.add(listener)),
      removeEventListener: vi.fn((_event: string, listener: () => void) =>
        listeners.delete(listener)
      ),
    } as unknown as MediaQueryList;
    vi.stubGlobal(
      "matchMedia",
      vi.fn(() => mediaQuery)
    );

    const first = renderHook(() => useIsMobile());
    const second = renderHook(() => useIsMobile());

    expect(mediaQuery.addEventListener).toHaveBeenCalledTimes(1);
    expect(first.result.current).toBe(false);
    expect(second.result.current).toBe(false);

    Object.defineProperty(mediaQuery, "matches", { configurable: true, value: true });
    act(() => listeners.forEach((listener) => listener()));

    expect(first.result.current).toBe(true);
    expect(second.result.current).toBe(true);

    first.unmount();
    expect(mediaQuery.removeEventListener).not.toHaveBeenCalled();
    second.unmount();
    expect(mediaQuery.removeEventListener).toHaveBeenCalledTimes(1);
  });
});

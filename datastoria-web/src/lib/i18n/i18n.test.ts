import { describe, expect, it } from "vitest";
import { normalizeLocale, resolveLocale, translate } from "./i18n";

describe("application i18n", () => {
  it("normalizes Chinese language tags", () => {
    expect(normalizeLocale("zh-TW")).toBe("zh-CN");
    expect(normalizeLocale("en-US")).toBe("en");
  });

  it("uses the browser language only for the system preference", () => {
    expect(resolveLocale("system", "zh-CN")).toBe("zh-CN");
    expect(resolveLocale("en", "zh-CN")).toBe("en");
  });

  it("translates the same message key in both catalogs", () => {
    expect(translate("en", "settings.title")).toBe("Settings");
    expect(translate("zh-CN", "settings.title")).toBe("设置");
  });
});

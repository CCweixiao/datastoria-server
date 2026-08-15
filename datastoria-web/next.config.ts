import type { NextConfig } from "next";

const normalizeBasePath = (basePath: string | undefined): string => {
  if (!basePath || basePath === "/") return "";
  const prefixed = basePath.startsWith("/") ? basePath : `/${basePath}`;
  return prefixed.endsWith("/") ? prefixed.slice(0, -1) : prefixed;
};

const basePath = normalizeBasePath(process.env.NEXT_PUBLIC_BASE_PATH);

const nextConfig: NextConfig = {
  ...(basePath && { basePath }),
  distDir: process.env.DATASTORIA_NEXT_DIST_DIR ?? ".next",
  reactStrictMode: false,
  transpilePackages: ["@number-flow/react", "number-flow"],
  // Static export: the build output in `out/` is served by the Spring Boot
  // backend (or any static file server); no Node.js runtime is required.
  output: "export",
  images: {
    // No Next.js image optimization server exists in static export mode.
    unoptimized: true,
  },
  experimental: {
    // Optimize barrel file imports for better performance
    // This transforms barrel imports to direct imports at build time
    optimizePackageImports: ["lucide-react"],
  },
};

export default nextConfig;

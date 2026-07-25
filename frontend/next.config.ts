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
  serverExternalPackages: ["knex"],
  // Enable standalone output for Docker deployment
  output: "standalone",
  // Increase body size limit for API routes to handle large tool results
  // (e.g., get_table_columns with 1500+ columns from system.metric_log)
  experimental: {
    serverActions: {
      bodySizeLimit: "10mb",
    },
    // Optimize barrel file imports for better performance
    // This transforms barrel imports to direct imports at build time
    optimizePackageImports: ["lucide-react"],
  },
};

export default nextConfig;

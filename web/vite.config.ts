/// <reference types="vitest/config" />
import { defineConfig, type Plugin } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import path from "path";
import fs from "fs";

/**
 * Vite plugin that serves EmulatorJS data assets from node_modules
 * at /emulatorjs/data/ in dev mode, and copies them into the build
 * output for production.
 */
function serveEmulatorjs(): Plugin {
  const dataDir = path.resolve(
    __dirname,
    "node_modules/@emulatorjs/emulatorjs/data",
  );
  const coresBaseDir = path.resolve(__dirname, "node_modules/@emulatorjs");
  let resolvedOutDir = "dist";

  const mimeTypes: Record<string, string> = {
    ".js": "application/javascript",
    ".css": "text/css",
    ".wasm": "application/wasm",
    ".json": "application/json",
    ".data": "application/octet-stream",
    ".html": "text/html",
    ".png": "image/png",
    ".svg": "image/svg+xml",
  };

  function serveFile(
    filePath: string,
    res: import("http").ServerResponse,
  ): boolean {
    try {
      const stat = fs.statSync(filePath);
      if (!stat.isFile()) return false;
      const ext = path.extname(filePath);
      res.setHeader(
        "Content-Type",
        mimeTypes[ext] || "application/octet-stream",
      );
      fs.createReadStream(filePath).pipe(res);
      return true;
    } catch {
      return false;
    }
  }

  /**
   * Resolve a request for /cores/{filename} by looking up the matching
   * @emulatorjs/core-{name} package.  Core packages place files like
   * "nestopia-wasm.data" at their package root, and report JSON files
   * inside a "reports/" subdirectory.
   */
  function resolveCorePath(urlPath: string): string | null {
    // urlPath looks like "/nestopia-wasm.data" or "/reports/nestopia.json"
    if (urlPath.startsWith("/reports/")) {
      const reportFile = urlPath.slice("/reports/".length);
      const coreName = reportFile.replace(".json", "");
      const candidate = path.join(
        coresBaseDir,
        `core-${coreName}`,
        "reports",
        reportFile,
      );
      if (fs.existsSync(candidate)) return candidate;
      return null;
    }

    // Extract core name from filenames like "nestopia-wasm.data"
    const fileName = urlPath.slice(1);
    const match = fileName.match(
      /^([a-z0-9_]+?)(?:-thread)?(?:-legacy)?-wasm\.data$/,
    );
    if (!match) return null;
    const coreName = match[1];
    const candidate = path.join(coresBaseDir, `core-${coreName}`, fileName);
    if (fs.existsSync(candidate)) return candidate;
    return null;
  }

  return {
    name: "serve-emulatorjs",
    configResolved(config) {
      resolvedOutDir = config.build.outDir;
    },
    configureServer(server) {
      server.middlewares.use("/emulatorjs/data", (req, res, next) => {
        const url = (req.url ?? "").split("?")[0];
        const filePath = path.join(dataDir, url);

        if (!filePath.startsWith(dataDir)) {
          return next();
        }

        // Try the main data directory first
        if (serveFile(filePath, res)) return;

        // For /cores/* requests, look in @emulatorjs/core-* packages
        if (url.startsWith("/cores/")) {
          const coreSubPath = url.slice("/cores".length);
          const resolved = resolveCorePath(coreSubPath);
          if (resolved && serveFile(resolved, res)) return;
        }

        next();
      });
    },
    closeBundle() {
      const targetDir = path.join(resolvedOutDir, "emulatorjs", "data");
      if (fs.existsSync(dataDir)) {
        fs.cpSync(dataDir, targetDir, { recursive: true });
      }
      // Copy installed core packages into the build output
      try {
        const coresTargetDir = path.join(targetDir, "cores");
        fs.mkdirSync(coresTargetDir, { recursive: true });
        for (const entry of fs.readdirSync(coresBaseDir)) {
          if (!entry.startsWith("core-")) continue;
          const pkgDir = path.join(coresBaseDir, entry);
          for (const file of fs.readdirSync(pkgDir)) {
            if (file.endsWith(".data")) {
              fs.cpSync(
                path.join(pkgDir, file),
                path.join(coresTargetDir, file),
              );
            }
          }
          const reportsDir = path.join(pkgDir, "reports");
          if (fs.existsSync(reportsDir)) {
            fs.cpSync(reportsDir, path.join(coresTargetDir, "reports"), {
              recursive: true,
            });
          }
        }
      } catch {
        // Non-critical: cores can still be fetched from CDN fallback
      }
    },
  };
}

export default defineConfig({
  plugins: [react(), tailwindcss(), serveEmulatorjs()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
  server: {
    proxy: {
      "/api": {
        target: process.env.VITE_API_URL || "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
  test: {
    globals: true,
    environment: "jsdom",
    setupFiles: ["./src/test-setup.ts"],
    css: true,
    exclude: ["node_modules/**", "e2e/**"],
  },
});

/// <reference types="vitest/config" />
import { defineConfig, type Plugin } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import checker from "vite-plugin-checker";
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

      // Only bundle cores that Spela actually uses (non-legacy variants).
      // Any missing core will be fetched at runtime from cdn.emulatorjs.org.
      const bundledCores = new Set([
        "nestopia",
        "snes9x",
        "gambatte",
        "mgba",
        "mupen64plus_next",
        "desmume",
        "genesis_plus_gx",
        "yabause",
        "mednafen_psx_hw",
        "ppsspp",
        "fbneo",
        "mednafen_pce",
        "stella2014",
        "picodrive",
        "beetle_vb",
        "atari800",
        "prosystem",
        "handy",
        "mednafen_ngp",
        "mednafen_wswan",
        "mednafen_pcfx",
        "vice_x64",
        "dosbox_pure",
        "virtualjaguar",
        "puae",
        "gearcoleco",
        "opera",
        "vice_x128",
        "vice_xpet",
        "vice_xplus4",
        "vice_xvic",
        "same_cdi",
      ]);

      try {
        const coresTargetDir = path.join(targetDir, "cores");
        fs.mkdirSync(coresTargetDir, { recursive: true });
        for (const entry of fs.readdirSync(coresBaseDir)) {
          if (!entry.startsWith("core-")) continue;
          const coreName = entry.slice("core-".length);
          if (!bundledCores.has(coreName)) continue;
          const pkgDir = path.join(coresBaseDir, entry);
          for (const file of fs.readdirSync(pkgDir)) {
            if (!file.endsWith(".data")) continue;
            // Skip legacy variants — modern browsers don't need them,
            // and they'll fall back to CDN if somehow requested.
            if (file.includes("-legacy-")) continue;
            fs.cpSync(
              path.join(pkgDir, file),
              path.join(coresTargetDir, file),
            );
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
  plugins: [react(), tailwindcss(), checker({ typescript: true }), serveEmulatorjs()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
  server: {
    headers: {
      "Cross-Origin-Opener-Policy": "same-origin",
      "Cross-Origin-Embedder-Policy": "credentialless",
    },
    proxy: {
      "/api": {
        target: process.env.VITE_API_URL || "http://localhost:8080",
        changeOrigin: true,
        ws: true,
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

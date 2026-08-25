import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    host: "0.0.0.0",
  },
  preview: {
    allowedHosts: true,
  },
  test: {
    environment: "jsdom",
    setupFiles: "./src/tests/setup.ts",
  },
});

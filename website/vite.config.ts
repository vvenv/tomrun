import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tsconfigPaths from "vite-tsconfig-paths";
import tailwindcss from "@tailwindcss/vite";

export default defineConfig({
  build: {
    sourcemap: "hidden",
  },
  server: {
    proxy: {
      "/api": {
        target: "https://run.edao.plus",
        changeOrigin: true,
      },
    },
  },
  plugins: [
    react({
      babel: {
        plugins: ["babel-plugin-react-dev-locator"],
      },
    }),
    tsconfigPaths(),
    tailwindcss(),
  ],
});

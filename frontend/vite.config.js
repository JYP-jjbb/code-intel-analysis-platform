import { defineConfig, loadEnv } from "vite";
import vue from "@vitejs/plugin-vue";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  const devProxyTarget = env.VITE_DEV_PROXY_TARGET || "http://localhost:8080";

  return {
    plugins: [vue()],
    server: {
      port: 5173,
      proxy: {
        "/api": {
          target: devProxyTarget,
          changeOrigin: true
        }
      }
    }
  };
});


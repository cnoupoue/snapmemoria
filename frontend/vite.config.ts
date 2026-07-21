import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const appIconPath = resolve(__dirname, '../src/main/resources/icon.png');

export default defineConfig({
  plugins: [
    react(),
    {
      name: 'memoria-vault-favicon',
      configureServer(server) {
        server.middlewares.use('/favicon.png', (_request, response) => {
          response.setHeader('Content-Type', 'image/png');
          response.end(readFileSync(appIconPath));
        });
      },
      generateBundle() {
        this.emitFile({
          type: 'asset',
          fileName: 'favicon.png',
          source: readFileSync(appIconPath),
        });
      },
    },
  ],

  server: {
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },

  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
  },
});

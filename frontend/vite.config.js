import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

// The browser only ever calls relative URLs such as /api/users.
//   - In production (Kubernetes) nginx forwards them to the three services (see nginx.conf).
//   - In development (npm run dev) the proxy below does the same for services on localhost,
//     which is how you run them from STS: user 8081, product 8082, order 8083.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    proxy: {
      '/api/auth': 'http://localhost:8081',
      '/api/users': 'http://localhost:8081',
      '/api/products': 'http://localhost:8082',
      '/api/cart': 'http://localhost:8083',
      '/api/admin': 'http://localhost:8083',
      '/api/orders': 'http://localhost:8083',
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.js',
    globals: true,
    css: false,
  },
});

/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  build: {
    // Straight into Spring Boot's classpath: `mvn package` runs this build, and the jar
    // serves the page from / with no separate frontend server.
    outDir: '../target/classes/static',
    emptyOutDir: true,
  },
  server: {
    // `npm run dev` against a backend started with `mvn spring-boot:run`.
    proxy: { '/api': 'http://localhost:8080' },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test-setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
  },
});

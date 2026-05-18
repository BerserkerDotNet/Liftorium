/// <reference types="vitest" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test-setup.ts'],
    css: false,
    coverage: {
      provider: 'v8',
      all: true,
      include: ['src/data/**', 'src/domain/**'],
      exclude: [
        'src/components/**',
        'src/pages/**',
        'src/main.tsx',
        'src/App.tsx',
        'src/App.test.tsx',
        'src/test-setup.ts',
        '**/*.config.*',
        '**/*.d.ts',
      ],
      reporter: ['text', 'text-summary', 'html', 'json', 'json-summary'],
      reportsDirectory: './coverage',
      thresholds: {
        lines: 95,
        branches: 95,
        statements: 95,
        functions: 95,
      },
    },
  },
});

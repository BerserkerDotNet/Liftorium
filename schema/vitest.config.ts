import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    globals: true,
    include: ['test/**/*.test.ts'],
    coverage: {
      provider: 'v8',
      all: true,
      include: ['validator.ts', 'semantics.ts', 'hash.ts'],
      exclude: [
        'scripts/**',
        'fixtures/**',
        'test/**',
        '**/*.config.*',
        'program-resource.schema.json',
        'tsconfig.json',
        'package.json',
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

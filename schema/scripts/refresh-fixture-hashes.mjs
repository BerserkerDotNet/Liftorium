#!/usr/bin/env node
// Cross-platform fixture/example contentHash refresher.
// Spawns vitest on the hash-freshness suite only, with REFRESH_FIXTURE_HASHES=1
// in the environment so the test rewrites the canonical hash into each
// fixture/example. Running only this test file avoids the parallel-read race
// where other tests would see stale fixtures mid-rewrite.

import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const moduleDir = dirname(fileURLToPath(import.meta.url));
const schemaDir = resolve(moduleDir, '..');

const isWindows = process.platform === 'win32';
const vitestBin = resolve(
  schemaDir,
  'node_modules',
  '.bin',
  isWindows ? 'vitest.cmd' : 'vitest',
);

const child = spawn(vitestBin, ['run', 'test/hash-freshness.test.ts'], {
  cwd: schemaDir,
  stdio: 'inherit',
  env: { ...process.env, REFRESH_FIXTURE_HASHES: '1' },
  shell: isWindows,
});

child.on('exit', (code) => process.exit(code ?? 0));

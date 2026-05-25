#!/usr/bin/env node
/**
 * Vacuous-pass guard for the Liftorium Web coverage gate.
 *
 * The vitest coverage block enforces ≥95% on lines/branches/statements/
 * functions for `src/data/**` and `src/domain/**`. With vitest's `all: true`,
 * a missing or empty include glob would silently pass (0/0 = 100%), which
 * would let a typo, accidental folder rename, or wholesale deletion disable
 * the gate without anyone noticing.
 *
 * Behavior:
 *   1. If NEITHER `src/data/` nor `src/domain/` exists → exit 0 with a
 *      message ("no coverage targets yet — android-program-runner+ work pending").
 *      This is the import-workflow baseline: Web has no domain/data code yet.
 *   2. If EITHER directory exists but contains zero source files
 *      (.ts/.tsx, excluding *.test.* / *.spec.* / *.d.ts) → exit 1.
 *      That signals the gate has been silently neutered.
 *   3. Otherwise → exit 0 and let the caller proceed to `vitest --coverage`.
 *
 * Run via: `npm run test:coverage` (which chains this script -> vitest).
 */
import { existsSync, statSync, readdirSync } from 'node:fs';
import { join, resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const moduleDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(moduleDir, '..');
const targetDirs = ['src/data', 'src/domain'];

function countSourceFiles(dir) {
  let count = 0;
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    const s = statSync(full);
    if (s.isDirectory()) {
      count += countSourceFiles(full);
      continue;
    }
    if (!/\.(ts|tsx)$/.test(entry)) continue;
    if (/\.(test|spec)\.(ts|tsx)$/.test(entry)) continue;
    if (/\.d\.ts$/.test(entry)) continue;
    count += 1;
  }
  return count;
}

const present = targetDirs
  .map((rel) => ({ rel, abs: resolve(repoRoot, rel) }))
  .filter((t) => existsSync(t.abs) && statSync(t.abs).isDirectory());

if (present.length === 0) {
  console.log(
    '[web/coverage-guard] No coverage targets yet - neither src/data/ nor src/domain/ exists.',
  );
  console.log('[web/coverage-guard] This is expected for the import-workflow Web baseline.');
  console.log('[web/coverage-guard] Gate will activate when android-program-runner+ adds domain/data modules.');
  process.exit(0);
}

let anyEmpty = false;
for (const { rel, abs } of present) {
  const n = countSourceFiles(abs);
  if (n === 0) {
    console.error(
      `[web/coverage-guard] FAIL: ${rel}/ exists but contains zero source files.`,
    );
    console.error(
      `[web/coverage-guard] This would let the coverage gate pass vacuously (0/0 = 100%).`,
    );
    console.error(
      `[web/coverage-guard] Either populate ${rel}/ with real code or remove the empty directory.`,
    );
    anyEmpty = true;
  } else {
    console.log(`[web/coverage-guard] ${rel}/ contains ${n} source file(s) - gate active.`);
  }
}

if (anyEmpty) process.exit(1);
console.log('[web/coverage-guard] All present coverage targets have real source files.');
process.exit(0);

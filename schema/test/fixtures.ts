import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const moduleDir = dirname(fileURLToPath(import.meta.url));

export const FIXTURE_DIR = resolve(moduleDir, '..', 'fixtures');
export const EXAMPLE_DIR = resolve(moduleDir, '..', 'examples');

/** Fixtures the JSON Schema accepts AND whose declared contentHash matches
 *  the canonical hash. These must be refreshed via the hash-refresh test
 *  when their content changes. */
export const HASHED_FIXTURE_FILES: readonly string[] = [
  'valid-activatable.json',
  'valid-activatable-warnings.json',
  'minimal-blocked-empty.json',
  'blocked-drop-set.json',
  'blocked-unknown-exercise.json',
  'blocked-missing-first-week-max.json',
  'blocked-ambiguous-week-order.json',
  'blocked-status-activatable-with-critical.json',
  'blocked-construct-severity-understated.json',
  'blocked-construct-must-be-critical.json',
  'rejected-not-activatable.json',
  'warning-private-import-provenance.json',
];

/** Operator-facing example resources whose hash must also be kept up to date. */
export const EXAMPLE_FILES: readonly string[] = ['example-5-3-1-bbb.json'];

/** Fixtures whose declared contentHash is intentionally wrong (used to
 *  exercise the hash-mismatch semantic check). */
export const HASH_MISMATCH_FIXTURE_FILES: readonly string[] = [
  'blocked-content-hash-mismatch.json',
];

/** Fixtures the JSON Schema rejects. */
export const INVALID_FIXTURE_FILES: readonly string[] = [
  'invalid-missing-program-id.json',
  'invalid-bad-source-hash.json',
  'invalid-unknown-status.json',
  'invalid-missing-content-hash.json',
  'invalid-target-missing-load-unit.json',
];

export function loadFixture(name: string): unknown {
  return JSON.parse(readFileSync(resolve(FIXTURE_DIR, name), 'utf-8'));
}

export function loadExample(name: string): unknown {
  return JSON.parse(readFileSync(resolve(EXAMPLE_DIR, name), 'utf-8'));
}

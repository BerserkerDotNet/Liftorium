import { describe, expect, it } from 'vitest';
import { readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { computeProgramResourceContentHash } from '../hash';
import {
  EXAMPLE_DIR,
  EXAMPLE_FILES,
  FIXTURE_DIR,
  HASHED_FIXTURE_FILES,
} from './fixtures';

const refreshing = process.env['REFRESH_FIXTURE_HASHES'] === '1';

interface Target {
  readonly label: string;
  readonly path: string;
}

const targets: readonly Target[] = [
  ...HASHED_FIXTURE_FILES.map((f) => ({ label: `fixtures/${f}`, path: resolve(FIXTURE_DIR, f) })),
  ...EXAMPLE_FILES.map((f) => ({ label: `examples/${f}`, path: resolve(EXAMPLE_DIR, f) })),
];

describe('fixture and example contentHash freshness', () => {
  for (const target of targets) {
    it(`is up to date for ${target.label}`, () => {
      const raw = readFileSync(target.path, 'utf-8');
      const data = JSON.parse(raw) as { metadata?: { contentHash?: string } };
      if (!data.metadata) {
        throw new Error(`${target.label} has no metadata block`);
      }
      const declared = data.metadata.contentHash;

      // Always compute against the all-zero placeholder so authoring with a
      // fresh hash and re-running gives the same result.
      const original = declared;
      data.metadata.contentHash = '0'.repeat(64);
      const computed = computeProgramResourceContentHash(data);
      data.metadata.contentHash = computed;

      if (refreshing) {
        writeFileSync(target.path, JSON.stringify(data, null, 2) + '\n', 'utf-8');
        return;
      }

      expect(
        original,
        `Run REFRESH_FIXTURE_HASHES=1 npm test to refresh ${target.label}; expected ${computed}`,
      ).toBe(computed);
    });
  }
});

import { describe, expect, it } from 'vitest';
import {
  buildProgramResourceValidator,
  validateProgramResource,
} from '../validator';
import {
  HASHED_FIXTURE_FILES,
  HASH_MISMATCH_FIXTURE_FILES,
  INVALID_FIXTURE_FILES,
  loadExample,
  loadFixture,
} from './fixtures';

describe('program-resource JSON Schema (Ajv strict)', () => {
  const validator = buildProgramResourceValidator();

  for (const file of [...HASHED_FIXTURE_FILES, ...HASH_MISMATCH_FIXTURE_FILES]) {
    it(`accepts the schema-valid fixture ${file}`, () => {
      const outcome = validateProgramResource(loadFixture(file), validator);
      expect(outcome.errors, JSON.stringify(outcome.errors, null, 2)).toEqual([]);
      expect(outcome.valid).toBe(true);
    });
  }

  it('accepts the operator-facing example resource', () => {
    const outcome = validateProgramResource(
      loadExample('example-5-3-1-bbb.json'),
      validator,
    );
    expect(outcome.errors, JSON.stringify(outcome.errors, null, 2)).toEqual([]);
    expect(outcome.valid).toBe(true);
  });

  it('rejects a resource missing the stable programId', () => {
    const outcome = validateProgramResource(
      loadFixture('invalid-missing-program-id.json'),
      validator,
    );
    expect(outcome.valid).toBe(false);
    expect(
      outcome.errors.some(
        (e) => e.params['missingProperty'] === 'programId',
      ),
    ).toBe(true);
  });

  it('rejects an unknown validationStatus value', () => {
    const outcome = validateProgramResource(
      loadFixture('invalid-unknown-status.json'),
      validator,
    );
    expect(outcome.valid).toBe(false);
    expect(
      outcome.errors.some((e) => e.instancePath === '/validationStatus'),
    ).toBe(true);
  });

  it('rejects an importAudit.sourceHash that is not a 64-char hex digest', () => {
    const outcome = validateProgramResource(
      loadFixture('invalid-bad-source-hash.json'),
      validator,
    );
    expect(outcome.valid).toBe(false);
    expect(
      outcome.errors.some((e) => e.instancePath === '/importAudit/sourceHash'),
    ).toBe(true);
  });

  it('rejects a resource missing metadata.contentHash', () => {
    const outcome = validateProgramResource(
      loadFixture('invalid-missing-content-hash.json'),
      validator,
    );
    expect(outcome.valid).toBe(false);
    expect(
      outcome.errors.some(
        (e) =>
          e.instancePath === '/metadata' &&
          e.params['missingProperty'] === 'contentHash',
      ),
    ).toBe(true);
  });

  it('rejects an exact_load_reps target without loadUnit', () => {
    const outcome = validateProgramResource(
      loadFixture('invalid-target-missing-load-unit.json'),
      validator,
    );
    expect(outcome.valid).toBe(false);
    expect(
      outcome.errors.some((e) => e.instancePath.endsWith('/targets/0')),
    ).toBe(true);
  });

  for (const file of INVALID_FIXTURE_FILES) {
    it(`rejects the schema-invalid fixture ${file}`, () => {
      const outcome = validateProgramResource(loadFixture(file), validator);
      expect(outcome.valid).toBe(false);
    });
  }
});

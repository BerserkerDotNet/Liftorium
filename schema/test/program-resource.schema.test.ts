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

  it('accepts a programWeek with optional variantOf and variantLabel fields', () => {
    const resource = loadFixture('valid-activatable.json') as {
      schemaVersion: number;
      programStructure: { blocks: Array<{ weeks: Array<Record<string, unknown>> }> };
    };
    resource.schemaVersion = 2;
    const week = resource.programStructure.blocks[0].weeks[0];
    week['variantLabel'] = 'A';
    const altWeek: Record<string, unknown> = JSON.parse(JSON.stringify(week));
    altWeek['id'] = 'week-1-alt';
    altWeek['weekIndex'] = 2;
    altWeek['variantOf'] = 'week-1';
    altWeek['variantLabel'] = 'B';
    resource.programStructure.blocks[0].weeks.push(altWeek);

    const outcome = validateProgramResource(resource, validator);
    expect(outcome.errors, JSON.stringify(outcome.errors, null, 2)).toEqual([]);
    expect(outcome.valid).toBe(true);
  });

  it('rejects a programWeek with an additional unknown property (additionalProperties:false)', () => {
    const resource = loadFixture('valid-activatable.json') as {
      programStructure: { blocks: Array<{ weeks: Array<Record<string, unknown>> }> };
    };
    resource.programStructure.blocks[0].weeks[0]['variantUnknownExtra'] = 'nope';
    const outcome = validateProgramResource(resource, validator);
    expect(outcome.valid).toBe(false);
    expect(
      outcome.errors.some((e) => e.params['additionalProperty'] === 'variantUnknownExtra'),
    ).toBe(true);
  });

  describe('prescription range extensions (schemaVersion 3 features)', () => {
    type ResourceLike = {
      schemaVersion: number;
      programStructure: {
        blocks: Array<{
          weeks: Array<{
            sessions: Array<{
              groups: Array<{
                prescriptionItems: Array<Record<string, unknown>>;
              }>;
            }>;
          }>;
        }>;
      };
    };

    function load(): ResourceLike {
      const r = loadFixture('valid-activatable.json') as ResourceLike;
      r.schemaVersion = 3;
      return r;
    }
    function firstItem(r: ResourceLike): Record<string, unknown> {
      return r.programStructure.blocks[0].weeks[0].sessions[0].groups[0].prescriptionItems[0];
    }
    function firstTarget(item: Record<string, unknown>): Record<string, unknown> {
      const sets = item['setPrescriptions'] as Array<Record<string, unknown>>;
      const targets = sets[0]['targets'] as Array<Record<string, unknown>>;
      return targets[0];
    }

    it('accepts a percentTarget with percentMin + percentMax (range form)', () => {
      const r = load();
      const t = firstTarget(firstItem(r));
      delete t['percent'];
      t['percentMin'] = 75;
      t['percentMax'] = 80;
      const outcome = validateProgramResource(r, validator);
      expect(outcome.errors, JSON.stringify(outcome.errors, null, 2)).toEqual([]);
    });

    it('rejects a percentTarget with both percent and percentMin (oneOf violation)', () => {
      const r = load();
      const t = firstTarget(firstItem(r));
      t['percentMin'] = 75;
      t['percentMax'] = 80;
      const outcome = validateProgramResource(r, validator);
      expect(outcome.valid).toBe(false);
    });

    it('rejects a percentTarget with neither percent nor percentMin/percentMax', () => {
      const r = load();
      const t = firstTarget(firstItem(r));
      delete t['percent'];
      const outcome = validateProgramResource(r, validator);
      expect(outcome.valid).toBe(false);
    });

    it('rejects a percentTarget with only percentMin (missing percentMax)', () => {
      const r = load();
      const t = firstTarget(firstItem(r));
      delete t['percent'];
      t['percentMin'] = 75;
      const outcome = validateProgramResource(r, validator);
      expect(outcome.valid).toBe(false);
    });

    it('rejects a percentTarget with only percentMax (missing percentMin)', () => {
      const r = load();
      const t = firstTarget(firstItem(r));
      delete t['percent'];
      t['percentMax'] = 80;
      const outcome = validateProgramResource(r, validator);
      expect(outcome.valid).toBe(false);
    });

    it('accepts a prescriptionItem with restSecondsHint + restMaxSecondsHint', () => {
      const r = load();
      const item = firstItem(r);
      item['restSecondsHint'] = 180;
      item['restMaxSecondsHint'] = 240;
      const outcome = validateProgramResource(r, validator);
      expect(outcome.errors, JSON.stringify(outcome.errors, null, 2)).toEqual([]);
    });

    it('rejects restMaxSecondsHint when restSecondsHint is absent (dependentRequired)', () => {
      const r = load();
      const item = firstItem(r);
      delete item['restSecondsHint'];
      item['restMaxSecondsHint'] = 240;
      const outcome = validateProgramResource(r, validator);
      expect(outcome.valid).toBe(false);
    });

    it('accepts a prescriptionItem with warmupSetCount', () => {
      const r = load();
      firstItem(r)['warmupSetCount'] = 4;
      const outcome = validateProgramResource(r, validator);
      expect(outcome.errors, JSON.stringify(outcome.errors, null, 2)).toEqual([]);
    });

    it('accepts validationStatus = "pending_runtime_references"', () => {
      const r = load() as unknown as Record<string, unknown>;
      r['validationStatus'] = 'pending_runtime_references';
      const outcome = validateProgramResource(r, validator);
      expect(outcome.errors, JSON.stringify(outcome.errors, null, 2)).toEqual([]);
    });

    it('rejects validationStatus outside the enum', () => {
      const r = load() as unknown as Record<string, unknown>;
      r['validationStatus'] = 'almost_activatable';
      const outcome = validateProgramResource(r, validator);
      expect(outcome.valid).toBe(false);
    });
  });
});

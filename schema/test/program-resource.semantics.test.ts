import { describe, expect, it } from 'vitest';
import { validateProgramResourceSemantics } from '../semantics';
import { loadFixture, loadExample } from './fixtures';

interface ResourceShape {
  schemaVersion?: unknown;
  importAudit?: { schemaVersionUsed?: unknown };
  exerciseCatalog?: Array<Record<string, unknown>>;
}

describe('validateProgramResourceSemantics — activation outcomes', () => {
  it('marks the fully populated valid fixture activatable with no semantic issues', () => {
    const report = validateProgramResourceSemantics(loadFixture('valid-activatable.json'));
    expect(report.issues).toEqual([]);
    expect(report.activatable).toBe(true);
  });

  it('marks the warnings fixture activatable but emits the later-week warning', () => {
    const report = validateProgramResourceSemantics(
      loadFixture('valid-activatable-warnings.json'),
    );
    expect(report.activatable).toBe(true);
    expect(report.issues.map((i) => i.code)).toContain('reference.missing_later_week');
    expect(report.issues.every((i) => i.severity !== 'critical')).toBe(true);
  });

  it('treats rejected resources as non-activatable even without semantic critical issues', () => {
    const report = validateProgramResourceSemantics(
      loadFixture('rejected-not-activatable.json'),
    );
    expect(report.activatable).toBe(false);
    expect(report.issues.every((i) => i.severity !== 'critical')).toBe(true);
  });

  it('also accepts the operator-facing example', () => {
    const report = validateProgramResourceSemantics(loadExample('example-5-3-1-bbb.json'));
    expect(report.issues).toEqual([]);
    expect(report.activatable).toBe(true);
  });
});

describe('validateProgramResourceSemantics — issue codes', () => {
  it('raises schema.version_unsupported when schemaVersion is not in the supported set', () => {
    const base = loadFixture('valid-activatable.json') as ResourceShape;
    base.schemaVersion = 999;
    const report = validateProgramResourceSemantics(base);
    expect(report.issues.some((i) => i.code === 'schema.version_unsupported')).toBe(true);
    expect(report.activatable).toBe(false);
  });

  it('raises schema.audit_version_mismatch when audit reports a different schema version', () => {
    const base = loadFixture('valid-activatable.json') as ResourceShape;
    if (base.importAudit) {
      base.importAudit.schemaVersionUsed = 2;
    }
    const report = validateProgramResourceSemantics(base);
    expect(report.issues.some((i) => i.code === 'schema.audit_version_mismatch')).toBe(true);
  });

  it('raises metadata.content_hash_mismatch when the declared hash is stale', () => {
    const report = validateProgramResourceSemantics(
      loadFixture('blocked-content-hash-mismatch.json'),
    );
    expect(report.issues.some((i) => i.code === 'metadata.content_hash_mismatch')).toBe(true);
    expect(report.activatable).toBe(false);
  });

  it('raises status.activatable_with_critical when status=activatable carries a critical issue', () => {
    const report = validateProgramResourceSemantics(
      loadFixture('blocked-status-activatable-with-critical.json'),
    );
    expect(report.issues.some((i) => i.code === 'status.activatable_with_critical')).toBe(true);
    expect(report.activatable).toBe(false);
  });

  it('raises catalog.duplicate_exercise_id on duplicate catalog ids', () => {
    const base = loadFixture('valid-activatable.json') as ResourceShape;
    if (Array.isArray(base.exerciseCatalog) && base.exerciseCatalog[0]) {
      base.exerciseCatalog.push({ ...base.exerciseCatalog[0] });
    }
    const report = validateProgramResourceSemantics(base);
    expect(report.issues.some((i) => i.code === 'catalog.duplicate_exercise_id')).toBe(true);
  });

  it('raises catalog.duplicate_alias_text on case-folded duplicate alias text', () => {
    const base = loadFixture('valid-activatable.json') as ResourceShape;
    if (Array.isArray(base.exerciseCatalog) && base.exerciseCatalog[1]) {
      base.exerciseCatalog[1]['aliases'] = [
        { aliasText: '  squat ', source: 'operator' },
      ];
    }
    const report = validateProgramResourceSemantics(base);
    expect(report.issues.some((i) => i.code === 'catalog.duplicate_alias_text')).toBe(true);
  });

  it('raises structure.no_runnable_week on the empty-blocks fixture', () => {
    const report = validateProgramResourceSemantics(
      loadFixture('minimal-blocked-empty.json'),
    );
    expect(report.issues.some((i) => i.code === 'structure.no_runnable_week')).toBe(true);
    expect(report.activatable).toBe(false);
  });

  it('raises structure.ambiguous_week_order on duplicate weekIndex', () => {
    const report = validateProgramResourceSemantics(
      loadFixture('blocked-ambiguous-week-order.json'),
    );
    expect(report.issues.some((i) => i.code === 'structure.ambiguous_week_order')).toBe(true);
  });

  it('raises exercise.unknown_reference when prescribedExerciseId is not in the catalog', () => {
    const report = validateProgramResourceSemantics(
      loadFixture('blocked-unknown-exercise.json'),
    );
    expect(report.issues.some((i) => i.code === 'exercise.unknown_reference')).toBe(true);
  });

  it('raises reference.missing_first_week when a week-1 percent target lacks a supplied max', () => {
    const report = validateProgramResourceSemantics(
      loadFixture('blocked-missing-first-week-max.json'),
    );
    expect(report.issues.some((i) => i.code === 'reference.missing_first_week')).toBe(true);
    expect(report.activatable).toBe(false);
  });

  it('treats a later-week unsupplied reference as warning, not critical', () => {
    const report = validateProgramResourceSemantics(
      loadFixture('valid-activatable-warnings.json'),
    );
    const issue = report.issues.find((i) => i.code === 'reference.missing_later_week');
    expect(issue?.severity).toBe('warning');
  });

  it('raises construct.severity_understated when drop_set is recorded as warning', () => {
    const report = validateProgramResourceSemantics(
      loadFixture('blocked-construct-severity-understated.json'),
    );
    expect(report.issues.some((i) => i.code === 'construct.severity_understated')).toBe(true);
  });

  it('raises construct.must_be_critical when an unknown construct.* code uses lower severity', () => {
    const report = validateProgramResourceSemantics(
      loadFixture('blocked-construct-must-be-critical.json'),
    );
    expect(report.issues.some((i) => i.code === 'construct.must_be_critical')).toBe(true);
  });

  it('accepts a private_import with proper provenance and emits no provenance warnings', () => {
    const fixture = loadFixture('warning-private-import-provenance.json') as Record<string, unknown>;
    const audit = fixture['importAudit'] as Record<string, unknown>;
    audit['sourceHash'] =
      'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa';
    audit['consentGranted'] = true;
    const report = validateProgramResourceSemantics(fixture);
    expect(
      report.issues.some((i) => i.code.startsWith('provenance.')),
    ).toBe(false);
  });

  it('raises provenance.private_import_zero_hash on a private_import with the synthetic sentinel', () => {
    const report = validateProgramResourceSemantics(
      loadFixture('warning-private-import-provenance.json'),
    );
    expect(report.issues.some((i) => i.code === 'provenance.private_import_zero_hash')).toBe(true);
  });

  it('raises provenance.private_import_missing_consent when private_import has no consent flag', () => {
    const report = validateProgramResourceSemantics(
      loadFixture('warning-private-import-provenance.json'),
    );
    expect(
      report.issues.some((i) => i.code === 'provenance.private_import_missing_consent'),
    ).toBe(true);
  });
});

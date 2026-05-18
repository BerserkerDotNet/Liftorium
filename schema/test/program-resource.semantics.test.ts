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
});

describe('validateProgramResourceSemantics — programWeek variants', () => {
  type ResourceLike = {
    schemaVersion: number;
    programStructure: { blocks: Array<{ weeks: Array<Record<string, unknown>> }> };
  };

  function loadVariantBase(): ResourceLike {
    const r = loadFixture('valid-activatable.json') as ResourceLike;
    r.schemaVersion = 2;
    // Fixture has [week-1, week-2]. Insert week-1-alt between them so the
    // variant group {week-1, week-1-alt} is contiguous, then bump week-2's
    // weekIndex so weekIndices stay [1, 2, 3].
    const weeks = r.programStructure.blocks[0].weeks;
    const week1 = weeks[0];
    const alt: Record<string, unknown> = JSON.parse(JSON.stringify(week1));
    alt['id'] = 'week-1-alt';
    alt['weekIndex'] = 2;
    alt['variantOf'] = 'week-1';
    alt['variantLabel'] = 'B';
    // Reassign nested ids so structure.duplicate_id doesn't fire.
    const renumber = (obj: Record<string, unknown>, suffix: string): void => {
      const sessions = (obj['sessions'] as Array<Record<string, unknown>>) ?? [];
      for (const s of sessions) {
        s['id'] = String(s['id']) + suffix;
        const groups = (s['groups'] as Array<Record<string, unknown>>) ?? [];
        for (const g of groups) {
          g['id'] = String(g['id']) + suffix;
          const items = (g['prescriptionItems'] as Array<Record<string, unknown>>) ?? [];
          for (const it of items) {
            it['id'] = String(it['id']) + suffix;
            const sets = (it['setPrescriptions'] as Array<Record<string, unknown>>) ?? [];
            for (const st of sets) {
              st['id'] = String(st['id']) + suffix;
            }
          }
        }
      }
    };
    renumber(alt, '-alt');
    (week1 as Record<string, unknown>)['variantLabel'] = 'A';
    // Bump week-2's weekIndex so [1, 2, 3] stays contiguous.
    (weeks[1] as Record<string, unknown>)['weekIndex'] = 3;
    weeks.splice(1, 0, alt); // [week-1, alt, week-2]
    return r;
  }

  it('accepts a valid base+variant pair (no structural variant issues raised)', () => {
    const r = loadVariantBase();
    const report = validateProgramResourceSemantics(r);
    const variantCodes = report.issues
      .map((i) => i.code)
      .filter((c) => c.startsWith('structure.variant_') || c === 'structure.unknown_variant_target');
    expect(variantCodes).toEqual([]);
  });

  it('raises structure.unknown_variant_target when variantOf references a non-existent week', () => {
    const r = loadVariantBase();
    r.programStructure.blocks[0].weeks[1]['variantOf'] = 'week-does-not-exist';
    const report = validateProgramResourceSemantics(r);
    expect(report.issues.some((i) => i.code === 'structure.unknown_variant_target')).toBe(true);
  });

  it('raises structure.variant_chain_depth when a variant targets another variant', () => {
    const r = loadVariantBase();
    // After loadVariantBase: [week-1@1, alt@2, week-2@3]. Append a new
    // week-3@4 and make week-1 a variant of week-3 so the chain alt → week-1
    // → week-3 has depth 2.
    const week3: Record<string, unknown> = JSON.parse(
      JSON.stringify(r.programStructure.blocks[0].weeks[0]),
    );
    week3['id'] = 'week-3';
    week3['weekIndex'] = 4;
    delete week3['variantOf'];
    delete week3['variantLabel'];
    const renumber = (obj: Record<string, unknown>, suffix: string): void => {
      const sessions = (obj['sessions'] as Array<Record<string, unknown>>) ?? [];
      for (const s of sessions) {
        s['id'] = String(s['id']) + suffix;
        const groups = (s['groups'] as Array<Record<string, unknown>>) ?? [];
        for (const g of groups) {
          g['id'] = String(g['id']) + suffix;
          const items = (g['prescriptionItems'] as Array<Record<string, unknown>>) ?? [];
          for (const it of items) {
            it['id'] = String(it['id']) + suffix;
            const sets = (it['setPrescriptions'] as Array<Record<string, unknown>>) ?? [];
            for (const st of sets) {
              st['id'] = String(st['id']) + suffix;
            }
          }
        }
      }
    };
    renumber(week3, '-w3');
    r.programStructure.blocks[0].weeks.push(week3);
    r.programStructure.blocks[0].weeks[0]['variantOf'] = 'week-3';
    const report = validateProgramResourceSemantics(r);
    expect(report.issues.some((i) => i.code === 'structure.variant_chain_depth')).toBe(true);
  });

  it('raises structure.variant_group_not_contiguous when an unrelated week splits the group', () => {
    const r = loadVariantBase();
    // After loadVariantBase: [week-1@1, alt@2, week-2@3]. Swap alt and week-2
    // so the array becomes [week-1, week-2, alt]: week-2 splits the group.
    const weeks = r.programStructure.blocks[0].weeks;
    [weeks[1], weeks[2]] = [weeks[2], weeks[1]];
    const report = validateProgramResourceSemantics(r);
    expect(
      report.issues.some((i) => i.code === 'structure.variant_group_not_contiguous'),
    ).toBe(true);
  });

  it('raises structure.variant_missing_label when a member of a multi-member group lacks variantLabel', () => {
    const r = loadVariantBase();
    delete r.programStructure.blocks[0].weeks[1]['variantLabel'];
    const report = validateProgramResourceSemantics(r);
    expect(report.issues.some((i) => i.code === 'structure.variant_missing_label')).toBe(true);
  });

  it('raises structure.variant_duplicate_label when two group members share a normalized label', () => {
    const r = loadVariantBase();
    r.programStructure.blocks[0].weeks[1]['variantLabel'] = ' a '; // trims+lowers to "a"
    const report = validateProgramResourceSemantics(r);
    expect(report.issues.some((i) => i.code === 'structure.variant_duplicate_label')).toBe(true);
  });

  it('raises structure.variant_schema_version_too_low when variantOf is used at schemaVersion 1', () => {
    const r = loadVariantBase();
    r.schemaVersion = 1;
    const report = validateProgramResourceSemantics(r);
    expect(
      report.issues.some((i) => i.code === 'structure.variant_schema_version_too_low'),
    ).toBe(true);
  });

  it('uses the base week index for reference validation when a TM is consumed only in the variant', () => {
    // Replicates the rubber-duck-flagged regression: TM consumed only in a
    // variant of week 1 must fire missing_first_week (critical), not the
    // missing_later_week warning.
    const r = loadVariantBase();
    // Mark the existing 'tm-squat' as not supplied; null its consumption in
    // the base week-1 sessions so the only consumption is in week-1-alt (the variant).
    const refs = (r as unknown as { requiredReferences: Array<Record<string, unknown>> })
      .requiredReferences;
    const tm = refs.find((x) => x['id'] === 'tm-squat');
    if (tm) tm['supplied'] = false;

    // Strip percent targets from base week-1, keep them only on week-1-alt.
    const stripTargets = (week: Record<string, unknown>): void => {
      const sessions = (week['sessions'] as Array<Record<string, unknown>>) ?? [];
      for (const s of sessions) {
        const groups = (s['groups'] as Array<Record<string, unknown>>) ?? [];
        for (const g of groups) {
          const items = (g['prescriptionItems'] as Array<Record<string, unknown>>) ?? [];
          for (const it of items) {
            const sets = (it['setPrescriptions'] as Array<Record<string, unknown>>) ?? [];
            for (const st of sets) {
              st['targets'] = [];
            }
          }
        }
      }
    };
    stripTargets(r.programStructure.blocks[0].weeks[0]);

    const report = validateProgramResourceSemantics(r);
    const missingFirst = report.issues.find(
      (i) => i.code === 'reference.missing_first_week',
    );
    expect(missingFirst).toBeDefined();
    expect(missingFirst?.severity).toBe('critical');
    // Conversely, the later-week warning must NOT fire for the same ref.
    const missingLater = report.issues.find(
      (i) => i.code === 'reference.missing_later_week',
    );
    expect(missingLater).toBeUndefined();
  });
});

describe('validateProgramResourceSemantics — prescription range extensions', () => {
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

  function loadExtensionBase(): ResourceLike {
    const r = loadFixture('valid-activatable.json') as ResourceLike;
    r.schemaVersion = 3;
    return r;
  }

  function firstItem(r: ResourceLike): Record<string, unknown> {
    return r.programStructure.blocks[0].weeks[0].sessions[0].groups[0].prescriptionItems[0];
  }

  function firstPercentTarget(item: Record<string, unknown>): Record<string, unknown> {
    const sets = item['setPrescriptions'] as Array<Record<string, unknown>>;
    const targets = sets[0]['targets'] as Array<Record<string, unknown>>;
    return targets[0];
  }

  it('accepts a percent range target (percentMin < percentMax) without issue', () => {
    const r = loadExtensionBase();
    const t = firstPercentTarget(firstItem(r));
    delete t['percent'];
    t['percentMin'] = 75;
    t['percentMax'] = 80;
    const report = validateProgramResourceSemantics(r);
    expect(report.issues.some((i) => i.code === 'target.percent_range_invalid')).toBe(false);
    expect(
      report.issues.some((i) => i.code === 'structure.percent_range_schema_version_too_low'),
    ).toBe(false);
  });

  it('raises target.percent_range_invalid when percentMin equals percentMax', () => {
    const r = loadExtensionBase();
    const t = firstPercentTarget(firstItem(r));
    delete t['percent'];
    t['percentMin'] = 75;
    t['percentMax'] = 75;
    const report = validateProgramResourceSemantics(r);
    const issue = report.issues.find((i) => i.code === 'target.percent_range_invalid');
    expect(issue).toBeDefined();
    expect(issue?.severity).toBe('critical');
  });

  it('raises target.percent_range_invalid when percentMin > percentMax', () => {
    const r = loadExtensionBase();
    const t = firstPercentTarget(firstItem(r));
    delete t['percent'];
    t['percentMin'] = 85;
    t['percentMax'] = 80;
    const report = validateProgramResourceSemantics(r);
    expect(report.issues.some((i) => i.code === 'target.percent_range_invalid')).toBe(true);
  });

  it('raises structure.percent_range_schema_version_too_low when range used at schemaVersion 2', () => {
    const r = loadExtensionBase();
    r.schemaVersion = 2;
    const t = firstPercentTarget(firstItem(r));
    delete t['percent'];
    t['percentMin'] = 75;
    t['percentMax'] = 80;
    const report = validateProgramResourceSemantics(r);
    expect(
      report.issues.some((i) => i.code === 'structure.percent_range_schema_version_too_low'),
    ).toBe(true);
  });

  it('accepts a valid rest range (restMaxSecondsHint > restSecondsHint)', () => {
    const r = loadExtensionBase();
    const item = firstItem(r);
    item['restSecondsHint'] = 180;
    item['restMaxSecondsHint'] = 240;
    const report = validateProgramResourceSemantics(r);
    expect(report.issues.some((i) => i.code === 'item.rest_range_invalid')).toBe(false);
    expect(
      report.issues.some((i) => i.code === 'structure.rest_range_schema_version_too_low'),
    ).toBe(false);
  });

  it('raises item.rest_range_invalid (warning) when restMaxSecondsHint <= restSecondsHint', () => {
    const r = loadExtensionBase();
    const item = firstItem(r);
    item['restSecondsHint'] = 240;
    item['restMaxSecondsHint'] = 180;
    const report = validateProgramResourceSemantics(r);
    const issue = report.issues.find((i) => i.code === 'item.rest_range_invalid');
    expect(issue).toBeDefined();
    expect(issue?.severity).toBe('warning');
  });

  it('raises structure.rest_range_schema_version_too_low when restMaxSecondsHint used at schemaVersion 2', () => {
    const r = loadExtensionBase();
    r.schemaVersion = 2;
    const item = firstItem(r);
    item['restSecondsHint'] = 180;
    item['restMaxSecondsHint'] = 240;
    const report = validateProgramResourceSemantics(r);
    expect(
      report.issues.some((i) => i.code === 'structure.rest_range_schema_version_too_low'),
    ).toBe(true);
  });

  it('accepts a warmupSetCount at schemaVersion 3', () => {
    const r = loadExtensionBase();
    firstItem(r)['warmupSetCount'] = 4;
    const report = validateProgramResourceSemantics(r);
    expect(
      report.issues.some((i) => i.code === 'structure.warmup_count_schema_version_too_low'),
    ).toBe(false);
  });

  it('raises structure.warmup_count_schema_version_too_low when warmupSetCount used at schemaVersion 2', () => {
    const r = loadExtensionBase();
    r.schemaVersion = 2;
    firstItem(r)['warmupSetCount'] = 4;
    const report = validateProgramResourceSemantics(r);
    expect(
      report.issues.some((i) => i.code === 'structure.warmup_count_schema_version_too_low'),
    ).toBe(true);
  });

  it('resolves percent range targets through walkPercentTargets (reference consumption tracked)', () => {
    // Strip targets from week 1, replace with a single percent-range target, and
    // mark the consumed ref as not supplied. We expect reference.missing_first_week
    // to still fire — proving walkPercentTargets sees the range form.
    const r = loadExtensionBase();
    const refs = (r as unknown as { requiredReferences: Array<Record<string, unknown>> })
      .requiredReferences;
    const tm = refs.find((x) => x['id'] === 'tm-squat');
    if (tm) tm['supplied'] = false;
    const item = firstItem(r);
    const set = (item['setPrescriptions'] as Array<Record<string, unknown>>)[0];
    set['targets'] = [
      { kind: 'percent', percentMin: 75, percentMax: 80, referenceId: 'tm-squat' },
    ];
    const report = validateProgramResourceSemantics(r);
    expect(report.issues.some((i) => i.code === 'reference.missing_first_week')).toBe(true);
  });

  it('accepts a set with both percent and rpe targets (conjunctive)', () => {
    const r = loadExtensionBase();
    const item = firstItem(r);
    const set = (item['setPrescriptions'] as Array<Record<string, unknown>>)[0];
    set['targets'] = [
      { kind: 'percent', percent: 75, referenceId: 'tm-squat' },
      { kind: 'rpe', target: 7.5 },
    ];
    const report = validateProgramResourceSemantics(r);
    // Coexistence itself produces no prescription-level critical issues.
    expect(report.issues.some((i) => i.code === 'target.percent_range_invalid')).toBe(false);
    expect(report.issues.some((i) => i.code === 'target.coexistence_invalid')).toBe(false);
  });
});

describe('validateProgramResourceSemantics — pending_runtime_references status', () => {
  function loadPendingBase(): Record<string, unknown> {
    const r = loadFixture('blocked-missing-first-week-max.json') as Record<string, unknown>;
    r['validationStatus'] = 'pending_runtime_references';
    // Drop the operator-iterate validationIssues block (mirrors how the
    // importer would emit this status: only semantic criticals describe the
    // remaining gate).
    r['validationIssues'] = [];
    return r;
  }

  it('does not fire status.activatable_with_critical for a valid pending state', () => {
    const r = loadPendingBase();
    const report = validateProgramResourceSemantics(r);
    expect(report.issues.some((i) => i.code === 'status.activatable_with_critical')).toBe(
      false,
    );
    // The underlying gate still fires.
    expect(report.issues.some((i) => i.code === 'reference.missing_first_week')).toBe(true);
  });

  it('does not fire status.pending_with_blocking_critical when only pending refs remain', () => {
    const r = loadPendingBase();
    const report = validateProgramResourceSemantics(r);
    expect(
      report.issues.some((i) => i.code === 'status.pending_with_blocking_critical'),
    ).toBe(false);
  });

  it('raises status.pending_with_blocking_critical when an unrelated critical exists', () => {
    const r = loadPendingBase();
    // Inject an unrelated critical: blow up the exerciseCatalog with a duplicate id.
    const catalog = r['exerciseCatalog'] as Array<Record<string, unknown>>;
    catalog.push({ ...catalog[0] });
    const report = validateProgramResourceSemantics(r);
    expect(
      report.issues.some((i) => i.code === 'status.pending_with_blocking_critical'),
    ).toBe(true);
  });

  it('raises status.pending_without_pending_refs when status is set but no pending criticals exist', () => {
    const base = loadFixture('valid-activatable.json') as Record<string, unknown>;
    base['validationStatus'] = 'pending_runtime_references';
    const report = validateProgramResourceSemantics(base);
    const warn = report.issues.find((i) => i.code === 'status.pending_without_pending_refs');
    expect(warn).toBeDefined();
    expect(warn?.severity).toBe('warning');
  });

  it('reports activatable: false for a pending resource (gate still applies)', () => {
    const r = loadPendingBase();
    const report = validateProgramResourceSemantics(r);
    expect(report.activatable).toBe(false);
  });
});

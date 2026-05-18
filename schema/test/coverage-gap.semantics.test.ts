import { describe, expect, it } from 'vitest';
import type { ValidateFunction } from 'ajv/dist/2020.js';
import {
  validateProgramResourceSemantics,
  type SemanticIssue,
} from '../semantics';
import { canonicalizeForContentHash, canonicalJsonStringify } from '../hash';
import { validateProgramResource } from '../validator';

// =============================================================================
// Coverage-gap tests. These exist so the schema package can meet the firm
// ≥95% gate on lines/branches/statements/functions for the three domain
// modules (validator.ts, semantics.ts, hash.ts). Every test below targets a
// specific uncovered branch documented in the v3 coverage plan, NOT a new
// behavior — the behaviors are the same defensive guards the modules already
// implement. These tests prove the guards behave as the contract says
// (return cleanly on malformed input, classify codes correctly, etc.) which
// is defense-in-depth, not coverage-driven busywork.
// =============================================================================

function codesOf(issues: readonly SemanticIssue[]): string[] {
  return issues.map((i) => i.code);
}

describe('hash.canonicalizeForContentHash — primitive roots', () => {
  it('returns null as-is', () => {
    expect(canonicalizeForContentHash(null)).toBe(null);
  });

  it('returns a primitive number as-is', () => {
    expect(canonicalizeForContentHash(42)).toBe(42);
  });

  it('returns a primitive string as-is', () => {
    expect(canonicalizeForContentHash('not-an-object')).toBe('not-an-object');
  });

  it('returns undefined as-is', () => {
    expect(canonicalizeForContentHash(undefined)).toBe(undefined);
  });

  it('returns an array as-is (top-level arrays are not the resource shape)', () => {
    const arr: unknown[] = [1, 2, 3];
    expect(canonicalizeForContentHash(arr)).toBe(arr);
  });

  it('skips metadata when metadata is not an object', () => {
    const view = canonicalizeForContentHash({
      schemaVersion: 1,
      metadata: 'not-an-object',
    }) as Record<string, unknown>;
    expect(view['schemaVersion']).toBe(1);
    expect(view['metadata']).toBeUndefined();
  });

  it('skips schemaVersion when undefined', () => {
    const view = canonicalizeForContentHash({}) as Record<string, unknown>;
    expect(view['schemaVersion']).toBeUndefined();
  });
});

describe('hash.canonicalJsonStringify — edge cases', () => {
  it('serializes null as JSON null', () => {
    expect(canonicalJsonStringify(null)).toBe('null');
  });

  it('serializes numbers and strings via JSON.stringify', () => {
    expect(canonicalJsonStringify(42)).toBe('42');
    expect(canonicalJsonStringify('hello')).toBe('"hello"');
  });

  it('serializes arrays element-by-element', () => {
    expect(canonicalJsonStringify([1, 'a', null])).toBe('[1,"a",null]');
  });

  it('filters undefined-valued keys from objects', () => {
    expect(canonicalJsonStringify({ a: 1, b: undefined, c: 2 })).toBe(
      '{"a":1,"c":2}',
    );
  });

  it('sorts object keys deterministically', () => {
    expect(canonicalJsonStringify({ b: 1, a: 2, c: 3 })).toBe(
      '{"a":2,"b":1,"c":3}',
    );
  });
});

describe('validator.validateProgramResource — defensive errors-fallback branch', () => {
  it('falls back to [] when the underlying Ajv validator returns false with null errors', () => {
    const fake = ((_data: unknown) => false) as unknown as ValidateFunction;
    (fake as unknown as { errors: unknown }).errors = null;
    const outcome = validateProgramResource({ irrelevant: true }, fake);
    expect(outcome.valid).toBe(false);
    expect(outcome.errors).toEqual([]);
  });
});

describe('semantics.validateProgramResourceSemantics — malformed root', () => {
  it.each([
    ['null', null],
    ['number primitive', 42],
    ['string primitive', 'resource'],
    ['array root', []],
    ['boolean root', true],
  ])('reports schema.malformed_root for %s', (_label, input) => {
    const report = validateProgramResourceSemantics(input);
    expect(report.activatable).toBe(false);
    expect(codesOf(report.issues)).toEqual(['schema.malformed_root']);
  });
});

describe('semantics — schema version checks', () => {
  it('rejects non-number schemaVersion', () => {
    const report = validateProgramResourceSemantics({ schemaVersion: '1' });
    expect(codesOf(report.issues)).toContain('schema.version_unsupported');
  });

  it('rejects unsupported numeric schemaVersion', () => {
    const report = validateProgramResourceSemantics({ schemaVersion: 99 });
    expect(codesOf(report.issues)).toContain('schema.version_unsupported');
  });

  it('does not emit when schemaVersion is the supported version', () => {
    const report = validateProgramResourceSemantics({ schemaVersion: 1 });
    expect(codesOf(report.issues)).not.toContain('schema.version_unsupported');
  });
});

describe('semantics — audit version mismatch', () => {
  it('emits when importAudit.schemaVersionUsed differs from top schemaVersion', () => {
    const report = validateProgramResourceSemantics({
      schemaVersion: 1,
      importAudit: { schemaVersionUsed: 2 },
    });
    expect(codesOf(report.issues)).toContain('schema.audit_version_mismatch');
  });

  it('returns early when importAudit is not an object', () => {
    const report = validateProgramResourceSemantics({
      schemaVersion: 1,
      importAudit: 'not-an-object',
    });
    expect(codesOf(report.issues)).not.toContain('schema.audit_version_mismatch');
  });

  it('skips check when schemaVersionUsed is not a number', () => {
    const report = validateProgramResourceSemantics({
      schemaVersion: 1,
      importAudit: { schemaVersionUsed: '1' },
    });
    expect(codesOf(report.issues)).not.toContain('schema.audit_version_mismatch');
  });
});

describe('semantics — content hash guards', () => {
  it('returns early when metadata is not an object', () => {
    const report = validateProgramResourceSemantics({
      schemaVersion: 1,
      metadata: 'not-an-object',
    });
    expect(codesOf(report.issues)).not.toContain('metadata.content_hash_mismatch');
  });

  it('returns early when metadata.contentHash is not a string', () => {
    const report = validateProgramResourceSemantics({
      schemaVersion: 1,
      metadata: { contentHash: 42 },
    });
    expect(codesOf(report.issues)).not.toContain('metadata.content_hash_mismatch');
  });

  it('accepts a contentHash that matches case-insensitively', () => {
    const resource = {
      schemaVersion: 1,
      metadata: { contentHash: 'placeholder' },
    };
    // First compute the canonical hash, then re-inject uppercase to prove
    // the toLowerCase() comparison.
    // We deliberately compute via canonicalize+serialize to avoid coupling.
    const view = canonicalizeForContentHash(resource);
    const canonical = canonicalJsonStringify(view);
    // crypto inline to avoid importing it at top-level for one test
    const { createHash } = require('node:crypto') as typeof import('node:crypto');
    const hash = createHash('sha256').update(canonical, 'utf8').digest('hex');
    const resource2 = {
      schemaVersion: 1,
      metadata: { contentHash: hash.toUpperCase() },
    };
    const report = validateProgramResourceSemantics(resource2);
    expect(codesOf(report.issues)).not.toContain('metadata.content_hash_mismatch');
  });
});

describe('semantics — exerciseCatalog guards', () => {
  it('returns early when exerciseCatalog is not an array', () => {
    const report = validateProgramResourceSemantics({
      schemaVersion: 1,
      exerciseCatalog: 'no',
    });
    expect(codesOf(report.issues)).not.toContain('catalog.duplicate_exercise_id');
  });

  it('skips non-object entries', () => {
    const report = validateProgramResourceSemantics({
      schemaVersion: 1,
      exerciseCatalog: ['not-an-object', null, 42],
    });
    expect(codesOf(report.issues)).not.toContain('catalog.duplicate_exercise_id');
  });

  it('emits catalog.duplicate_exercise_id for repeated ids', () => {
    const report = validateProgramResourceSemantics({
      schemaVersion: 1,
      exerciseCatalog: [{ id: 'a' }, { id: 'a' }],
    });
    expect(codesOf(report.issues)).toContain('catalog.duplicate_exercise_id');
  });

  it('skips entries whose id is not a string', () => {
    const report = validateProgramResourceSemantics({
      schemaVersion: 1,
      exerciseCatalog: [{ id: 42 }, { id: 42 }],
    });
    expect(codesOf(report.issues)).not.toContain('catalog.duplicate_exercise_id');
  });

  it('continues past aliases that are not arrays', () => {
    const report = validateProgramResourceSemantics({
      schemaVersion: 1,
      exerciseCatalog: [{ id: 'a', aliases: 'no' }],
    });
    expect(codesOf(report.issues)).not.toContain('catalog.duplicate_alias_text');
  });

  it('skips non-object aliases', () => {
    const report = validateProgramResourceSemantics({
      schemaVersion: 1,
      exerciseCatalog: [{ id: 'a', aliases: ['no', null] }],
    });
    expect(codesOf(report.issues)).not.toContain('catalog.duplicate_alias_text');
  });

  it('skips alias entries whose aliasText is not a string', () => {
    const report = validateProgramResourceSemantics({
      schemaVersion: 1,
      exerciseCatalog: [{ id: 'a', aliases: [{ aliasText: 42 }] }],
    });
    expect(codesOf(report.issues)).not.toContain('catalog.duplicate_alias_text');
  });

  it('emits catalog.duplicate_alias_text when two entries share an alias', () => {
    const report = validateProgramResourceSemantics({
      schemaVersion: 1,
      exerciseCatalog: [
        { id: 'squat', aliases: [{ aliasText: 'Back squat' }] },
        { id: 'bench', aliases: [{ aliasText: 'Back squat' }] },
      ],
    });
    expect(codesOf(report.issues)).toContain('catalog.duplicate_alias_text');
  });
});

describe('semantics — programStructure guards', () => {
  it('returns early when programStructure is not an object', () => {
    const report = validateProgramResourceSemantics({
      schemaVersion: 1,
      programStructure: 'no',
    });
    expect(codesOf(report.issues)).not.toContain('structure.no_runnable_week');
  });

  it('emits structure.no_runnable_week when blocks is missing or empty', () => {
    const report = validateProgramResourceSemantics({
      schemaVersion: 1,
      programStructure: { blocks: [] },
    });
    expect(codesOf(report.issues)).toContain('structure.no_runnable_week');
  });

  it('emits structure.no_runnable_week when blocks is not an array', () => {
    const report = validateProgramResourceSemantics({
      schemaVersion: 1,
      programStructure: { blocks: 'no' },
    });
    expect(codesOf(report.issues)).toContain('structure.no_runnable_week');
  });

  it('emits structure.no_runnable_week when no set prescriptions exist anywhere', () => {
    const report = validateProgramResourceSemantics({
      schemaVersion: 1,
      programStructure: {
        blocks: [
          {
            id: 'b1',
            order: 1,
            weeks: [
              { id: 'w1', weekIndex: 1, sessions: [{ id: 's1', sessionIndex: 1, groups: [] }] },
            ],
          },
        ],
      },
    });
    expect(codesOf(report.issues)).toContain('structure.no_runnable_week');
  });

  it('skips non-object blocks/weeks/sessions/groups/items/sets', () => {
    const report = validateProgramResourceSemantics({
      schemaVersion: 1,
      programStructure: {
        blocks: [
          'not-an-object',
          {
            weeks: [
              'not-an-object',
              {
                sessions: [
                  'not-an-object',
                  {
                    groups: [
                      'not-an-object',
                      {
                        prescriptionItems: [
                          'not-an-object',
                          { setPrescriptions: ['not-an-object', null] },
                        ],
                      },
                    ],
                  },
                ],
              },
            ],
          },
        ],
      },
    });
    // The chain of "continue on non-object" guards must not throw and must
    // still emit no_runnable_week because no sets were collected.
    expect(codesOf(report.issues)).toContain('structure.no_runnable_week');
  });

  it('skips non-array weeks/sessions/groups/items/sets', () => {
    const report = validateProgramResourceSemantics({
      schemaVersion: 1,
      programStructure: {
        blocks: [
          { id: 'b', weeks: 'no' },
          {
            id: 'b2',
            weeks: [
              { id: 'w', sessions: 'no' },
              {
                id: 'w2',
                sessions: [
                  { id: 's', groups: 'no' },
                  {
                    id: 's2',
                    groups: [
                      { id: 'g', prescriptionItems: 'no' },
                      {
                        id: 'g2',
                        prescriptionItems: [
                          { id: 'i', setPrescriptions: 'no' },
                        ],
                      },
                    ],
                  },
                ],
              },
            ],
          },
        ],
      },
    });
    // No throw; structure.no_runnable_week emitted because no sets exist.
    expect(codesOf(report.issues)).toContain('structure.no_runnable_week');
  });

  it('emits structure.duplicate_order for repeated block orders', () => {
    const report = validateProgramResourceSemantics({
      schemaVersion: 1,
      programStructure: {
        blocks: [
          { id: 'b1', order: 1, weeks: [] },
          { id: 'b2', order: 1, weeks: [] },
        ],
      },
    });
    expect(codesOf(report.issues)).toContain('structure.duplicate_order');
  });

  it('emits structure.duplicate_order for repeated group orders', () => {
    const report = validateProgramResourceSemantics(
      buildSingleSetResource({
        groups: [
          { id: 'g1', order: 1 },
          { id: 'g2', order: 1 },
        ],
      }),
    );
    expect(codesOf(report.issues)).toContain('structure.duplicate_order');
  });

  it('emits structure.duplicate_order for repeated prescriptionItem orders', () => {
    const report = validateProgramResourceSemantics(
      buildSingleSetResource({
        items: [
          { id: 'i1', order: 1 },
          { id: 'i2', order: 1 },
        ],
      }),
    );
    expect(codesOf(report.issues)).toContain('structure.duplicate_order');
  });

  it('emits structure.duplicate_order for repeated set orders', () => {
    const report = validateProgramResourceSemantics(
      buildSingleSetResource({
        sets: [
          { id: 's1', order: 1 },
          { id: 's2', order: 1 },
        ],
      }),
    );
    expect(codesOf(report.issues)).toContain('structure.duplicate_order');
  });

  it('emits structure.ambiguous_session_order when session indices are non-contiguous', () => {
    const report = validateProgramResourceSemantics(
      buildSingleSetResource({
        sessions: [
          { id: 's1', sessionIndex: 1 },
          { id: 's3', sessionIndex: 3 },
        ],
      }),
    );
    expect(codesOf(report.issues)).toContain('structure.ambiguous_session_order');
  });

  it('emits structure.ambiguous_week_order when week indices are non-contiguous', () => {
    const report = validateProgramResourceSemantics(
      buildSingleSetResource({
        weeks: [
          { id: 'w1', weekIndex: 1 },
          { id: 'w3', weekIndex: 3 },
        ],
      }),
    );
    expect(codesOf(report.issues)).toContain('structure.ambiguous_week_order');
  });

  it('does not emit ambiguous_session_order for a week with zero sessions (empty contiguity check)', () => {
    const report = validateProgramResourceSemantics({
      schemaVersion: 1,
      programStructure: {
        blocks: [
          {
            id: 'b1',
            order: 1,
            weeks: [{ id: 'w1', weekIndex: 1, sessions: [] }],
          },
        ],
      },
    });
    expect(codesOf(report.issues)).not.toContain('structure.ambiguous_session_order');
    expect(codesOf(report.issues)).not.toContain('structure.ambiguous_week_order');
  });

  it('emits structure.duplicate_id when two structural ids collide', () => {
    const report = validateProgramResourceSemantics({
      schemaVersion: 1,
      programStructure: {
        blocks: [
          { id: 'same', order: 1, weeks: [] },
          { id: 'same', order: 2, weeks: [] },
        ],
      },
    });
    expect(codesOf(report.issues)).toContain('structure.duplicate_id');
  });

  it('skips structural ids that are not strings', () => {
    const report = validateProgramResourceSemantics({
      schemaVersion: 1,
      programStructure: {
        blocks: [{ id: 42, order: 1, weeks: [] }],
      },
    });
    expect(codesOf(report.issues)).not.toContain('structure.duplicate_id');
  });

  it('emits exercise.unknown_reference when prescribedExerciseId is not in catalog', () => {
    const report = validateProgramResourceSemantics({
      schemaVersion: 1,
      exerciseCatalog: [{ id: 'squat' }],
      programStructure: {
        blocks: [
          {
            id: 'b1',
            order: 1,
            weeks: [
              {
                id: 'w1',
                weekIndex: 1,
                sessions: [
                  {
                    id: 's1',
                    sessionIndex: 1,
                    groups: [
                      {
                        id: 'g1',
                        order: 1,
                        prescriptionItems: [
                          {
                            id: 'i1',
                            order: 1,
                            prescribedExerciseId: 'unknown-exercise',
                            setPrescriptions: [{ id: 'sp1', order: 1 }],
                          },
                        ],
                      },
                    ],
                  },
                ],
              },
            ],
          },
        ],
      },
    });
    expect(codesOf(report.issues)).toContain('exercise.unknown_reference');
  });
});

describe('semantics — reference usage', () => {
  it('returns early when requiredReferences is not an array', () => {
    const report = validateProgramResourceSemantics({
      schemaVersion: 1,
      requiredReferences: 'no',
    });
    expect(codesOf(report.issues)).not.toContain('reference.unknown');
  });

  it('skips non-object reference entries and entries without string ids', () => {
    const report = validateProgramResourceSemantics({
      schemaVersion: 1,
      requiredReferences: ['no', null, { id: 42 }],
    });
    expect(codesOf(report.issues)).not.toContain('reference.unknown');
  });

  it('emits reference.unknown when a percent target references an unknown reference id', () => {
    const report = validateProgramResourceSemantics(
      buildResourceWithPercentTarget({
        refs: [],
        targetRefId: 'unknown-ref',
        weekIndex: 1,
      }),
    );
    expect(codesOf(report.issues)).toContain('reference.unknown');
  });

  it('emits reference.missing_first_week when an unsupplied reference is consumed in week 1', () => {
    const report = validateProgramResourceSemantics(
      buildResourceWithPercentTarget({
        refs: [{ id: 'tm', supplied: false }],
        targetRefId: 'tm',
        weekIndex: 1,
      }),
    );
    expect(codesOf(report.issues)).toContain('reference.missing_first_week');
  });

  it('emits reference.missing_later_week when an unsupplied reference is consumed in a later week', () => {
    const report = validateProgramResourceSemantics(
      buildResourceWithPercentTarget({
        refs: [{ id: 'tm', supplied: false }],
        targetRefId: 'tm',
        weekIndex: 2,
      }),
    );
    expect(codesOf(report.issues)).toContain('reference.missing_later_week');
  });

  it('emits reference.declared_week_mismatch when declared first-runnable week differs from actual', () => {
    const report = validateProgramResourceSemantics(
      buildResourceWithPercentTarget({
        refs: [{ id: 'tm', supplied: true, firstRunnableWeekIndex: 3 }],
        targetRefId: 'tm',
        weekIndex: 1,
      }),
    );
    expect(codesOf(report.issues)).toContain('reference.declared_week_mismatch');
  });

  it('emits reference.unused_declaration for declared but never consumed references', () => {
    const report = validateProgramResourceSemantics({
      schemaVersion: 1,
      requiredReferences: [{ id: 'tm-orphan', supplied: true }],
      programStructure: {
        blocks: [
          {
            id: 'b1',
            order: 1,
            weeks: [{ id: 'w1', weekIndex: 1, sessions: [] }],
          },
        ],
      },
    });
    expect(codesOf(report.issues)).toContain('reference.unused_declaration');
  });
});

describe('semantics — walkPercentTargets defensive type guards', () => {
  it('does not throw when every nested level is non-object/non-array', () => {
    expect(() =>
      validateProgramResourceSemantics({
        schemaVersion: 1,
        requiredReferences: [{ id: 'tm', supplied: true }],
        programStructure: 'no',
      }),
    ).not.toThrow();
  });

  it.each([
    ['programStructure not object', { programStructure: 'no' }],
    ['blocks not array', { programStructure: { blocks: 'no' } }],
    ['block not object', { programStructure: { blocks: ['no'] } }],
    ['weeks not array', { programStructure: { blocks: [{ id: 'b', weeks: 'no' }] } }],
    ['week not object', { programStructure: { blocks: [{ id: 'b', weeks: ['no'] }] } }],
    [
      'sessions not array',
      {
        programStructure: {
          blocks: [{ id: 'b', weeks: [{ id: 'w', weekIndex: 1, sessions: 'no' }] }],
        },
      },
    ],
    [
      'session not object',
      {
        programStructure: {
          blocks: [{ id: 'b', weeks: [{ id: 'w', weekIndex: 1, sessions: ['no'] }] }],
        },
      },
    ],
    [
      'groups not array',
      {
        programStructure: {
          blocks: [
            {
              id: 'b',
              weeks: [
                {
                  id: 'w',
                  weekIndex: 1,
                  sessions: [{ id: 's', sessionIndex: 1, groups: 'no' }],
                },
              ],
            },
          ],
        },
      },
    ],
    [
      'group not object',
      {
        programStructure: {
          blocks: [
            {
              id: 'b',
              weeks: [
                {
                  id: 'w',
                  weekIndex: 1,
                  sessions: [{ id: 's', sessionIndex: 1, groups: ['no'] }],
                },
              ],
            },
          ],
        },
      },
    ],
    [
      'items not array',
      {
        programStructure: {
          blocks: [
            {
              id: 'b',
              weeks: [
                {
                  id: 'w',
                  weekIndex: 1,
                  sessions: [
                    {
                      id: 's',
                      sessionIndex: 1,
                      groups: [{ id: 'g', order: 1, prescriptionItems: 'no' }],
                    },
                  ],
                },
              ],
            },
          ],
        },
      },
    ],
    [
      'item not object',
      {
        programStructure: {
          blocks: [
            {
              id: 'b',
              weeks: [
                {
                  id: 'w',
                  weekIndex: 1,
                  sessions: [
                    {
                      id: 's',
                      sessionIndex: 1,
                      groups: [{ id: 'g', order: 1, prescriptionItems: ['no'] }],
                    },
                  ],
                },
              ],
            },
          ],
        },
      },
    ],
    [
      'sets not array',
      {
        programStructure: {
          blocks: [
            {
              id: 'b',
              weeks: [
                {
                  id: 'w',
                  weekIndex: 1,
                  sessions: [
                    {
                      id: 's',
                      sessionIndex: 1,
                      groups: [
                        {
                          id: 'g',
                          order: 1,
                          prescriptionItems: [
                            { id: 'i', order: 1, setPrescriptions: 'no' },
                          ],
                        },
                      ],
                    },
                  ],
                },
              ],
            },
          ],
        },
      },
    ],
    [
      'set not object',
      {
        programStructure: {
          blocks: [
            {
              id: 'b',
              weeks: [
                {
                  id: 'w',
                  weekIndex: 1,
                  sessions: [
                    {
                      id: 's',
                      sessionIndex: 1,
                      groups: [
                        {
                          id: 'g',
                          order: 1,
                          prescriptionItems: [
                            { id: 'i', order: 1, setPrescriptions: ['no'] },
                          ],
                        },
                      ],
                    },
                  ],
                },
              ],
            },
          ],
        },
      },
    ],
    [
      'targets not array',
      {
        programStructure: {
          blocks: [
            {
              id: 'b',
              weeks: [
                {
                  id: 'w',
                  weekIndex: 1,
                  sessions: [
                    {
                      id: 's',
                      sessionIndex: 1,
                      groups: [
                        {
                          id: 'g',
                          order: 1,
                          prescriptionItems: [
                            {
                              id: 'i',
                              order: 1,
                              setPrescriptions: [{ id: 'sp', order: 1, targets: 'no' }],
                            },
                          ],
                        },
                      ],
                    },
                  ],
                },
              ],
            },
          ],
        },
      },
    ],
  ])(
    'walkPercentTargets returns/continues cleanly when %s',
    (_label, partial) => {
      // requiredReferences is set so checkReferenceUsage actually invokes the
      // walker, exercising the walker's own defensive guards (not just the
      // copies inside checkProgramStructure).
      const resource = {
        schemaVersion: 1,
        requiredReferences: [{ id: 'tm-orphan', supplied: true }],
        ...partial,
      };
      const report = validateProgramResourceSemantics(resource);
      // unused_declaration fires for the orphan because nothing consumed it,
      // proving the walker ran to completion without throwing.
      expect(codesOf(report.issues)).toContain('reference.unused_declaration');
    },
  );

  it('skips percent targets whose referenceId is an empty string', () => {
    const report = validateProgramResourceSemantics(
      buildResourceWithCustomTargets({
        refs: [{ id: 'tm', supplied: true }],
        targets: [{ kind: 'percent', percent: 70, referenceId: '' }],
      }),
    );
    // Empty string is a string per typeof, walker visits, but visit short-
    // circuits on `!referenceId` → no reference.* issue beyond unused_declaration.
    expect(codesOf(report.issues)).toContain('reference.unused_declaration');
    expect(codesOf(report.issues)).not.toContain('reference.unknown');
  });

  it('skips targets that are non-object', () => {
    const report = validateProgramResourceSemantics(
      buildResourceWithCustomTargets({
        refs: [{ id: 'tm', supplied: true }],
        targets: ['no', null, 42],
      }),
    );
    // No reference.* issue should be raised because no valid percent target
    // names a reference; but unused_declaration is fine.
    expect(codesOf(report.issues)).toContain('reference.unused_declaration');
    expect(codesOf(report.issues)).not.toContain('reference.unknown');
  });

  it('skips targets whose kind is not percent', () => {
    const report = validateProgramResourceSemantics(
      buildResourceWithCustomTargets({
        refs: [{ id: 'tm', supplied: true }],
        targets: [{ kind: 'load', value: 100, unit: 'kg' }],
      }),
    );
    expect(codesOf(report.issues)).toContain('reference.unused_declaration');
  });

  it('skips percent targets whose referenceId is not a string', () => {
    const report = validateProgramResourceSemantics(
      buildResourceWithCustomTargets({
        refs: [{ id: 'tm', supplied: true }],
        targets: [{ kind: 'percent', percent: 70, referenceId: 42 }],
      }),
    );
    expect(codesOf(report.issues)).toContain('reference.unused_declaration');
  });

  it('uses 0 as weekIndex when week.weekIndex is not a number', () => {
    // This exercises the `typeof week['weekIndex'] === 'number' ? ... : 0` branch.
    const report = validateProgramResourceSemantics({
      schemaVersion: 1,
      requiredReferences: [{ id: 'tm', supplied: false }],
      programStructure: {
        blocks: [
          {
            id: 'b1',
            order: 1,
            weeks: [
              {
                id: 'w1',
                weekIndex: 'first', // non-number => walker uses 0
                sessions: [
                  {
                    id: 's1',
                    sessionIndex: 1,
                    groups: [
                      {
                        id: 'g1',
                        order: 1,
                        prescriptionItems: [
                          {
                            id: 'i1',
                            order: 1,
                            setPrescriptions: [
                              {
                                id: 'sp1',
                                order: 1,
                                targets: [
                                  { kind: 'percent', percent: 70, referenceId: 'tm' },
                                ],
                              },
                            ],
                          },
                        ],
                      },
                    ],
                  },
                ],
              },
            ],
          },
        ],
      },
    });
    // weekIndex defaults to 0 → "<= 1" branch → missing_first_week
    expect(codesOf(report.issues)).toContain('reference.missing_first_week');
  });
});

describe('semantics — construct severity classification', () => {
  it('returns early when validationIssues is not an array', () => {
    const report = validateProgramResourceSemantics({
      schemaVersion: 1,
      validationIssues: 'no',
    });
    expect(codesOf(report.issues)).not.toContain('construct.severity_understated');
  });

  it('skips non-object validationIssues entries', () => {
    const report = validateProgramResourceSemantics({
      schemaVersion: 1,
      validationIssues: ['no', null, 42],
    });
    expect(codesOf(report.issues)).not.toContain('construct.severity_understated');
  });

  it('skips entries whose code or severity is not a string', () => {
    const report = validateProgramResourceSemantics({
      schemaVersion: 1,
      validationIssues: [
        { code: 42, severity: 'critical' },
        { code: 'construct.drop_set', severity: 42 },
      ],
    });
    expect(codesOf(report.issues)).not.toContain('construct.severity_understated');
  });

  it('skips validationIssues codes outside the construct.* namespace', () => {
    const report = validateProgramResourceSemantics({
      schemaVersion: 1,
      validationIssues: [{ code: 'reference.unknown', severity: 'warning' }],
    });
    expect(codesOf(report.issues)).not.toContain('construct.severity_understated');
  });

  it('emits construct.severity_understated when a known critical construct is downgraded', () => {
    const report = validateProgramResourceSemantics({
      schemaVersion: 1,
      validationIssues: [{ code: 'construct.drop_set', severity: 'warning' }],
    });
    expect(codesOf(report.issues)).toContain('construct.severity_understated');
  });

  it('does not emit when a known critical construct is correctly classified', () => {
    const report = validateProgramResourceSemantics({
      schemaVersion: 1,
      validationIssues: [{ code: 'construct.drop_set', severity: 'critical' }],
    });
    expect(codesOf(report.issues)).not.toContain('construct.severity_understated');
  });

  it('emits construct.severity_overstated when a known warning construct is escalated', () => {
    const report = validateProgramResourceSemantics({
      schemaVersion: 1,
      validationIssues: [{ code: 'construct.tempo', severity: 'critical' }],
    });
    expect(codesOf(report.issues)).toContain('construct.severity_overstated');
  });

  it('accepts known warning constructs at warning or info severity', () => {
    const reportWarn = validateProgramResourceSemantics({
      schemaVersion: 1,
      validationIssues: [{ code: 'construct.tempo', severity: 'warning' }],
    });
    const reportInfo = validateProgramResourceSemantics({
      schemaVersion: 1,
      validationIssues: [{ code: 'construct.tempo', severity: 'info' }],
    });
    expect(codesOf(reportWarn.issues)).not.toContain('construct.severity_overstated');
    expect(codesOf(reportInfo.issues)).not.toContain('construct.severity_overstated');
  });

  it('emits construct.must_be_critical for unknown construct codes that are not critical', () => {
    const report = validateProgramResourceSemantics({
      schemaVersion: 1,
      validationIssues: [{ code: 'construct.fictional', severity: 'warning' }],
    });
    expect(codesOf(report.issues)).toContain('construct.must_be_critical');
  });

  it('does not emit when an unknown construct code is correctly classified critical', () => {
    const report = validateProgramResourceSemantics({
      schemaVersion: 1,
      validationIssues: [{ code: 'construct.fictional', severity: 'critical' }],
    });
    expect(codesOf(report.issues)).not.toContain('construct.must_be_critical');
  });
});

describe('semantics — provenance checks', () => {
  it('returns early when importAudit is not an object', () => {
    const report = validateProgramResourceSemantics({
      schemaVersion: 1,
      importAudit: 'no',
    });
    expect(codesOf(report.issues)).not.toContain('provenance.private_import_zero_hash');
  });

  it('emits provenance.private_import_zero_hash when private_import carries the synthetic sentinel', () => {
    const report = validateProgramResourceSemantics({
      schemaVersion: 1,
      importAudit: {
        sourceKind: 'private_import',
        sourceHash: '0'.repeat(64),
      },
    });
    expect(codesOf(report.issues)).toContain('provenance.private_import_zero_hash');
  });

  it('does not flag private_import when sourceHash is a real hash', () => {
    const report = validateProgramResourceSemantics({
      schemaVersion: 1,
      importAudit: {
        sourceKind: 'private_import',
        sourceHash: 'a'.repeat(64),
      },
    });
    expect(codesOf(report.issues)).not.toContain('provenance.private_import_zero_hash');
  });

  it('skips private_import hash check when sourceHash is not a string', () => {
    const report = validateProgramResourceSemantics({
      schemaVersion: 1,
      importAudit: {
        sourceKind: 'private_import',
        sourceHash: 42,
      },
    });
    expect(codesOf(report.issues)).not.toContain('provenance.private_import_zero_hash');
  });

  it('emits provenance.synthetic_with_real_hash when synthetic carries a non-zero hash', () => {
    const report = validateProgramResourceSemantics({
      schemaVersion: 1,
      importAudit: {
        sourceKind: 'synthetic',
        sourceHash: 'a'.repeat(64),
      },
    });
    expect(codesOf(report.issues)).toContain('provenance.synthetic_with_real_hash');
  });

  it('does not flag synthetic with the zero sentinel', () => {
    const report = validateProgramResourceSemantics({
      schemaVersion: 1,
      importAudit: {
        sourceKind: 'synthetic',
        sourceHash: '0'.repeat(64),
      },
    });
    expect(codesOf(report.issues)).not.toContain('provenance.synthetic_with_real_hash');
  });

  it('skips synthetic hash check when sourceHash is not a string', () => {
    const report = validateProgramResourceSemantics({
      schemaVersion: 1,
      importAudit: {
        sourceKind: 'synthetic',
        sourceHash: 42,
      },
    });
    expect(codesOf(report.issues)).not.toContain('provenance.synthetic_with_real_hash');
  });

  it('does not flag any sourceKind outside the matched set', () => {
    const report = validateProgramResourceSemantics({
      schemaVersion: 1,
      importAudit: {
        sourceKind: 'upload',
        sourceHash: 'a'.repeat(64),
      },
    });
    expect(codesOf(report.issues).filter((c) => c.startsWith('provenance.'))).toEqual([]);
  });
});

describe('semantics — activation status branches', () => {
  it('emits status.activatable_with_critical when declaredStatus is activatable AND a declared critical issue exists', () => {
    const report = validateProgramResourceSemantics({
      schemaVersion: 1,
      validationStatus: 'activatable',
      validationIssues: [
        { code: 'something.critical', severity: 'critical', message: 'x' },
      ],
    });
    expect(codesOf(report.issues)).toContain('status.activatable_with_critical');
    expect(report.activatable).toBe(false);
  });

  it('emits status.activatable_with_critical when a semantic critical exists even if declared list is empty', () => {
    const report = validateProgramResourceSemantics({
      schemaVersion: 1,
      validationStatus: 'activatable',
      validationIssues: [],
      programStructure: { blocks: [] }, // → structure.no_runnable_week (critical)
    });
    expect(codesOf(report.issues)).toContain('status.activatable_with_critical');
    expect(report.activatable).toBe(false);
  });

  it('marks declaredStatus=blocked as non-activatable even with no critical issues', () => {
    const report = validateProgramResourceSemantics({
      schemaVersion: 1,
      validationStatus: 'blocked',
    });
    expect(report.activatable).toBe(false);
  });

  it('marks an absent declaredStatus as non-activatable', () => {
    const report = validateProgramResourceSemantics({ schemaVersion: 1 });
    expect(report.activatable).toBe(false);
  });

  it('treats validationIssues=non-array as empty declaredIssues', () => {
    const report = validateProgramResourceSemantics({
      schemaVersion: 1,
      validationStatus: 'activatable',
      validationIssues: 'not-an-array',
    });
    // No declared critical AND no semantic critical → activatable_with_critical NOT raised
    expect(codesOf(report.issues)).not.toContain('status.activatable_with_critical');
    expect(report.activatable).toBe(true);
  });
});

// =============================================================================
// Test helpers — build minimal synthetic resources with controllable shape.
// These are NOT exported fixtures; they exist only to exercise specific
// uncovered branches.
// =============================================================================

interface StructureOverride {
  weeks?: Array<Record<string, unknown>>;
  sessions?: Array<Record<string, unknown>>;
  groups?: Array<Record<string, unknown>>;
  items?: Array<Record<string, unknown>>;
  sets?: Array<Record<string, unknown>>;
}

/** Build a resource with exactly one set prescription, allowing one structure
 *  level to be overridden with a custom array (for duplicate-order /
 *  ambiguous-order tests). */
function buildSingleSetResource(override: StructureOverride): unknown {
  const sets = override.sets ?? [{ id: 'sp1', order: 1 }];
  const items = override.items ?? [
    { id: 'i1', order: 1, setPrescriptions: sets },
  ];
  // If override.items was supplied but items lack setPrescriptions, inject.
  if (override.items) {
    for (const it of override.items) {
      if (it && typeof it === 'object' && !('setPrescriptions' in it)) {
        (it as Record<string, unknown>).setPrescriptions = sets;
      }
    }
  }
  const groups = override.groups ?? [{ id: 'g1', order: 1, prescriptionItems: items }];
  if (override.groups) {
    for (const g of override.groups) {
      if (g && typeof g === 'object' && !('prescriptionItems' in g)) {
        (g as Record<string, unknown>).prescriptionItems = items;
      }
    }
  }
  const sessions = override.sessions ?? [{ id: 's1', sessionIndex: 1, groups }];
  if (override.sessions) {
    for (const s of override.sessions) {
      if (s && typeof s === 'object' && !('groups' in s)) {
        (s as Record<string, unknown>).groups = groups;
      }
    }
  }
  const weeks = override.weeks ?? [{ id: 'w1', weekIndex: 1, sessions }];
  if (override.weeks) {
    for (const w of override.weeks) {
      if (w && typeof w === 'object' && !('sessions' in w)) {
        (w as Record<string, unknown>).sessions = sessions;
      }
    }
  }
  return {
    schemaVersion: 1,
    programStructure: {
      blocks: [{ id: 'b1', order: 1, weeks }],
    },
  };
}

interface PercentTargetOpts {
  refs: Array<Record<string, unknown>>;
  targetRefId: string;
  weekIndex: number;
}

/** Build a resource where one percent target consumes `targetRefId` in
 *  `weekIndex`. */
function buildResourceWithPercentTarget(opts: PercentTargetOpts): unknown {
  return {
    schemaVersion: 1,
    requiredReferences: opts.refs,
    programStructure: {
      blocks: [
        {
          id: 'b1',
          order: 1,
          weeks: [
            {
              id: `w${opts.weekIndex}`,
              weekIndex: opts.weekIndex,
              sessions: [
                {
                  id: 's1',
                  sessionIndex: 1,
                  groups: [
                    {
                      id: 'g1',
                      order: 1,
                      prescriptionItems: [
                        {
                          id: 'i1',
                          order: 1,
                          setPrescriptions: [
                            {
                              id: 'sp1',
                              order: 1,
                              targets: [
                                { kind: 'percent', percent: 70, referenceId: opts.targetRefId },
                              ],
                            },
                          ],
                        },
                      ],
                    },
                  ],
                },
              ],
            },
          ],
        },
      ],
    },
  };
}

interface CustomTargetsOpts {
  refs: Array<Record<string, unknown>>;
  targets: unknown[];
}

/** Build a resource where the set's targets array is provided verbatim — for
 *  exercising walker guards on non-object targets, non-percent kinds, etc. */
function buildResourceWithCustomTargets(opts: CustomTargetsOpts): unknown {
  return {
    schemaVersion: 1,
    requiredReferences: opts.refs,
    programStructure: {
      blocks: [
        {
          id: 'b1',
          order: 1,
          weeks: [
            {
              id: 'w1',
              weekIndex: 1,
              sessions: [
                {
                  id: 's1',
                  sessionIndex: 1,
                  groups: [
                    {
                      id: 'g1',
                      order: 1,
                      prescriptionItems: [
                        {
                          id: 'i1',
                          order: 1,
                          setPrescriptions: [
                            { id: 'sp1', order: 1, targets: opts.targets },
                          ],
                        },
                      ],
                    },
                  ],
                },
              ],
            },
          ],
        },
      ],
    },
  };
}

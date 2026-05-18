import { computeProgramResourceContentHash } from './hash.js';

export const SUPPORTED_SCHEMA_VERSIONS: ReadonlySet<number> = new Set([1, 2, 3]);

export const KNOWN_CRITICAL_CONSTRUCT_CODES: ReadonlySet<string> = new Set([
  'construct.drop_set',
  'construct.density_emom',
  'construct.for_time',
  'construct.unsupported_autoregulation',
  'construct.unknown',
]);

export const KNOWN_WARNING_CONSTRUCT_CODES: ReadonlySet<string> = new Set([
  'construct.tempo',
  'construct.rest_pause',
  'construct.myo_reps',
]);

export type Severity = 'info' | 'warning' | 'critical';

export interface SemanticIssue {
  readonly code: string;
  readonly severity: Severity;
  readonly message: string;
  readonly locationHint?: string;
}

export interface SemanticReport {
  /** True when no semantic critical issue is raised AND the resource's
   *  declared validationStatus + validationIssues do not block activation. */
  readonly activatable: boolean;
  /** Issues raised by the semantic validator. */
  readonly issues: readonly SemanticIssue[];
}

/**
 * Run the semantic checks that the JSON Schema cannot express.
 *
 * Caller is expected to have already validated the resource against the
 * JSON Schema with `validateProgramResource`. If the resource does not
 * conform to the schema this function may still run, but will report only
 * the rules it can evaluate on the malformed input — schema validation is
 * the ground-truth gate.
 */
export function validateProgramResourceSemantics(resource: unknown): SemanticReport {
  const issues: SemanticIssue[] = [];

  if (!isObject(resource)) {
    issues.push({
      code: 'schema.malformed_root',
      severity: 'critical',
      message: 'Program resource root is not a JSON object.',
    });
    return { activatable: false, issues };
  }

  checkSchemaVersion(resource, issues);
  checkAuditVersion(resource, issues);
  checkContentHash(resource, issues);
  checkExerciseCatalog(resource, issues);
  checkProgramStructure(resource, issues);
  checkVariantWeeks(resource, issues);
  checkPrescriptionExtensions(resource, issues);
  checkReferenceUsage(resource, issues);
  checkConstructSeverities(resource, issues);
  checkProvenance(resource, issues);
  checkPendingReferencesStatus(resource, issues);

  const declaredStatus = resource['validationStatus'];
  const declaredIssues = Array.isArray(resource['validationIssues'])
    ? (resource['validationIssues'] as Array<Record<string, unknown>>)
    : [];

  const declaredCritical = declaredIssues.some(
    (i) => isObject(i) && i['severity'] === 'critical',
  );
  const semanticCritical = issues.some((i) => i.severity === 'critical');

  if (declaredStatus === 'activatable' && (declaredCritical || semanticCritical)) {
    issues.push({
      code: 'status.activatable_with_critical',
      severity: 'critical',
      message: 'validationStatus is activatable but at least one critical issue remains.',
    });
  }

  // status.rejected_not_activatable is informational: rejected resources are
  // never activatable, even with no critical issues.
  let activatable: boolean;
  if (declaredStatus === 'activatable') {
    activatable =
      !declaredCritical && !issues.some((i) => i.severity === 'critical');
  } else {
    activatable = false;
  }

  return { activatable, issues };
}

/**
 * Validate the `pending_runtime_references` status:
 *   - It is only meaningful when at least one requiredReference declares
 *     `supplied: false` AND is consumed in a first-runnable week (i.e. the
 *     reference is the actual runtime gate).
 *   - When this status is declared, any SEMANTIC critical other than
 *     `reference.missing_first_week` is a misuse: the resource is not really
 *     "pending references only"; raise `status.pending_with_blocking_critical`.
 *   - When this status is declared but no `reference.missing_first_week`
 *     criticals exist at all, raise `status.pending_without_pending_refs`
 *     (warning) — the resource is in fact activatable and should declare so.
 */
function checkPendingReferencesStatus(
  resource: Record<string, unknown>,
  issues: SemanticIssue[],
): void {
  const declaredStatus = resource['validationStatus'];
  if (declaredStatus !== 'pending_runtime_references') return;

  const hasPendingRefCritical = issues.some(
    (i) => i.code === 'reference.missing_first_week' && i.severity === 'critical',
  );
  const hasOtherCritical = issues.some(
    (i) => i.severity === 'critical' && i.code !== 'reference.missing_first_week',
  );

  if (hasOtherCritical) {
    issues.push({
      code: 'status.pending_with_blocking_critical',
      severity: 'critical',
      message:
        'validationStatus is pending_runtime_references but at least one critical issue other than reference.missing_first_week remains; iterate as blocked.',
    });
  } else if (!hasPendingRefCritical) {
    issues.push({
      code: 'status.pending_without_pending_refs',
      severity: 'warning',
      message:
        'validationStatus is pending_runtime_references but no first-week pending references were detected; resource should be marked activatable.',
    });
  }
}

function checkSchemaVersion(resource: Record<string, unknown>, issues: SemanticIssue[]): void {
  const version = resource['schemaVersion'];
  if (typeof version !== 'number' || !SUPPORTED_SCHEMA_VERSIONS.has(version)) {
    issues.push({
      code: 'schema.version_unsupported',
      severity: 'critical',
      message: `Unsupported schemaVersion ${formatScalar(version)}; supported set is [${[
        ...SUPPORTED_SCHEMA_VERSIONS,
      ].join(', ')}].`,
    });
  }
}

function checkAuditVersion(resource: Record<string, unknown>, issues: SemanticIssue[]): void {
  const top = resource['schemaVersion'];
  const audit = resource['importAudit'];
  if (!isObject(audit)) return;
  const used = audit['schemaVersionUsed'];
  if (typeof top === 'number' && typeof used === 'number' && top !== used) {
    issues.push({
      code: 'schema.audit_version_mismatch',
      severity: 'critical',
      message: `importAudit.schemaVersionUsed (${used}) does not match top-level schemaVersion (${top}).`,
    });
  }
}

function checkContentHash(resource: Record<string, unknown>, issues: SemanticIssue[]): void {
  const md = resource['metadata'];
  if (!isObject(md)) return;
  const declared = md['contentHash'];
  if (typeof declared !== 'string') return;
  const computed = computeProgramResourceContentHash(resource);
  if (declared.toLowerCase() !== computed) {
    issues.push({
      code: 'metadata.content_hash_mismatch',
      severity: 'critical',
      message:
        'metadata.contentHash does not match the canonical hash of the resource content.',
    });
  }
}

function checkExerciseCatalog(
  resource: Record<string, unknown>,
  issues: SemanticIssue[],
): void {
  const catalog = resource['exerciseCatalog'];
  if (!Array.isArray(catalog)) return;

  const seenIds = new Set<string>();
  const seenAliases = new Map<string, string>();

  for (const entry of catalog) {
    if (!isObject(entry)) continue;
    const id = entry['id'];
    if (typeof id === 'string') {
      if (seenIds.has(id)) {
        issues.push({
          code: 'catalog.duplicate_exercise_id',
          severity: 'critical',
          message: `Duplicate exerciseCatalog id '${id}'.`,
        });
      } else {
        seenIds.add(id);
      }
    }
    const aliases = entry['aliases'];
    if (!Array.isArray(aliases)) continue;
    for (const alias of aliases) {
      if (!isObject(alias)) continue;
      const text = alias['aliasText'];
      if (typeof text !== 'string') continue;
      const key = normalizeAliasText(text);
      const existing = seenAliases.get(key);
      if (existing && typeof id === 'string') {
        issues.push({
          code: 'catalog.duplicate_alias_text',
          severity: 'critical',
          message: `Alias text '${text}' duplicates an existing alias (also on '${existing}').`,
        });
      } else if (typeof id === 'string') {
        seenAliases.set(key, id);
      }
    }
  }
}

function checkProgramStructure(
  resource: Record<string, unknown>,
  issues: SemanticIssue[],
): void {
  const structure = resource['programStructure'];
  if (!isObject(structure)) return;
  const blocks = structure['blocks'];
  if (!Array.isArray(blocks) || blocks.length === 0) {
    issues.push({
      code: 'structure.no_runnable_week',
      severity: 'critical',
      message: 'programStructure has no runnable block; no workouts are reachable.',
    });
    return;
  }

  // Catalog index for unknown-reference checks.
  const catalog = resource['exerciseCatalog'];
  const catalogIds = new Set<string>();
  if (Array.isArray(catalog)) {
    for (const entry of catalog) {
      if (isObject(entry) && typeof entry['id'] === 'string') {
        catalogIds.add(entry['id']);
      }
    }
  }

  const seenStructuralIds = new Set<string>();
  let totalSets = 0;

  const blockOrders = new Set<number>();
  for (const block of blocks) {
    if (!isObject(block)) continue;
    addStructuralId(block['id'], seenStructuralIds, issues);
    const order = block['order'];
    if (typeof order === 'number') {
      if (blockOrders.has(order)) {
        issues.push({
          code: 'structure.duplicate_order',
          severity: 'critical',
          message: `Duplicate block order ${order}.`,
        });
      } else {
        blockOrders.add(order);
      }
    }

    const weeks = block['weeks'];
    if (!Array.isArray(weeks)) continue;
    const weekIndices: number[] = [];
    for (const week of weeks) {
      if (!isObject(week)) continue;
      addStructuralId(week['id'], seenStructuralIds, issues);
      const weekIndex = week['weekIndex'];
      if (typeof weekIndex === 'number') weekIndices.push(weekIndex);

      const sessions = week['sessions'];
      if (!Array.isArray(sessions)) continue;
      const sessionIndices: number[] = [];
      for (const session of sessions) {
        if (!isObject(session)) continue;
        addStructuralId(session['id'], seenStructuralIds, issues);
        const sessionIndex = session['sessionIndex'];
        if (typeof sessionIndex === 'number') sessionIndices.push(sessionIndex);

        const groups = session['groups'];
        if (!Array.isArray(groups)) continue;
        const groupOrders = new Set<number>();
        for (const group of groups) {
          if (!isObject(group)) continue;
          addStructuralId(group['id'], seenStructuralIds, issues);
          const groupOrder = group['order'];
          if (typeof groupOrder === 'number') {
            if (groupOrders.has(groupOrder)) {
              issues.push({
                code: 'structure.duplicate_order',
                severity: 'critical',
                message: `Duplicate group order ${groupOrder} within a session.`,
              });
            } else {
              groupOrders.add(groupOrder);
            }
          }

          const items = group['prescriptionItems'];
          if (!Array.isArray(items)) continue;
          const itemOrders = new Set<number>();
          for (const item of items) {
            if (!isObject(item)) continue;
            addStructuralId(item['id'], seenStructuralIds, issues);
            const itemOrder = item['order'];
            if (typeof itemOrder === 'number') {
              if (itemOrders.has(itemOrder)) {
                issues.push({
                  code: 'structure.duplicate_order',
                  severity: 'critical',
                  message: `Duplicate prescriptionItem order ${itemOrder} within a group.`,
                });
              } else {
                itemOrders.add(itemOrder);
              }
            }

            const prescribed = item['prescribedExerciseId'];
            if (typeof prescribed === 'string' && !catalogIds.has(prescribed)) {
              issues.push({
                code: 'exercise.unknown_reference',
                severity: 'critical',
                message: `prescribedExerciseId '${prescribed}' does not match any exerciseCatalog entry.`,
              });
            }

            const sets = item['setPrescriptions'];
            if (!Array.isArray(sets)) continue;
            const setOrders = new Set<number>();
            for (const set of sets) {
              if (!isObject(set)) continue;
              totalSets += 1;
              addStructuralId(set['id'], seenStructuralIds, issues);
              const setOrder = set['order'];
              if (typeof setOrder === 'number') {
                if (setOrders.has(setOrder)) {
                  issues.push({
                    code: 'structure.duplicate_order',
                    severity: 'critical',
                    message: `Duplicate setPrescription order ${setOrder} within a prescriptionItem.`,
                  });
                } else {
                  setOrders.add(setOrder);
                }
              }
            }
          }
        }

        if (sessionIndices.length > 0 && !isContiguousFromOne(sessionIndices)) {
          issues.push({
            code: 'structure.ambiguous_session_order',
            severity: 'critical',
            message: 'Session indices within a week must be unique and contiguous starting at 1.',
          });
        }
      }

      if (weekIndices.length > 0 && !isContiguousFromOne(weekIndices)) {
        issues.push({
          code: 'structure.ambiguous_week_order',
          severity: 'critical',
          message: 'Week indices within a block must be unique and contiguous starting at 1.',
        });
        // De-dupe: only emit once per block.
        break;
      }
    }
  }

  if (totalSets === 0) {
    issues.push({
      code: 'structure.no_runnable_week',
      severity: 'critical',
      message:
        'programStructure contains no set prescriptions; no workouts are reachable.',
    });
  }
}

/**
 * Validate programWeek.variantOf / variantLabel semantics.
 *
 * Variant weeks declare runtime alternates that the user picks one of. Rules
 * enforced here (in order of emission):
 *
 *   1. variantOf must reference a week id that exists within the SAME block.
 *   2. variantOf target must NOT itself have variantOf (chain depth = 1).
 *   3. A variant group (a base week and all its variants) must occupy a
 *      contiguous run in the block's weeks[] array; no unrelated week may
 *      appear between them.
 *   4. Every member of a multi-member variant group must declare variantLabel.
 *   5. Within a single variant group, variantLabels must be unique
 *      (case-insensitive after trimming whitespace).
 *   6. Any resource that uses variantOf MUST declare schemaVersion >= 2.
 */
function checkVariantWeeks(
  resource: Record<string, unknown>,
  issues: SemanticIssue[],
): void {
  const structure = resource['programStructure'];
  if (!isObject(structure)) return;
  const blocks = structure['blocks'];
  if (!Array.isArray(blocks)) return;

  let anyVariant = false;

  for (const block of blocks) {
    if (!isObject(block)) continue;
    const weeks = block['weeks'];
    if (!Array.isArray(weeks)) continue;

    const weekIds = new Set<string>();
    const variantOfById = new Map<string, string>();
    const labelById = new Map<string, string | undefined>();
    const positionById = new Map<string, number>();

    weeks.forEach((week, idx) => {
      if (!isObject(week)) return;
      const id = week['id'];
      if (typeof id !== 'string') return;
      weekIds.add(id);
      positionById.set(id, idx);
      const label = week['variantLabel'];
      labelById.set(id, typeof label === 'string' ? label : undefined);
      const variantOf = week['variantOf'];
      if (typeof variantOf === 'string') {
        anyVariant = true;
        variantOfById.set(id, variantOf);
      }
    });

    // Rule 1 + 2: reference resolution and chain-depth.
    for (const [id, target] of variantOfById.entries()) {
      if (!weekIds.has(target)) {
        issues.push({
          code: 'structure.unknown_variant_target',
          severity: 'critical',
          message: `programWeek '${id}' has variantOf '${target}' but no such week exists within the same block.`,
        });
        continue;
      }
      if (variantOfById.has(target)) {
        issues.push({
          code: 'structure.variant_chain_depth',
          severity: 'critical',
          message: `programWeek '${id}' has variantOf '${target}', which itself has variantOf; chain depth is bounded to 1.`,
        });
      }
    }

    // Group variants by base for the contiguity and label rules.
    const groups = new Map<string, string[]>(); // baseId -> [baseId, ...variantIds in array order]
    for (const week of weeks) {
      if (!isObject(week)) continue;
      const id = week['id'];
      if (typeof id !== 'string') continue;
      if (variantOfById.has(id)) continue; // skip variants on this pass
      groups.set(id, [id]);
    }
    for (const [id, target] of variantOfById.entries()) {
      const list = groups.get(target);
      if (list) list.push(id);
    }

    for (const [baseId, members] of groups.entries()) {
      if (members.length < 2) continue; // not a real variant group

      // Rule 3: contiguous in array order. Sort members by position and check.
      const sortedByPos = [...members].sort(
        (a, b) => (positionById.get(a) ?? 0) - (positionById.get(b) ?? 0),
      );
      const first = positionById.get(sortedByPos[0]) ?? 0;
      const last = positionById.get(sortedByPos[sortedByPos.length - 1]) ?? 0;
      const run = last - first + 1;
      if (run !== members.length) {
        issues.push({
          code: 'structure.variant_group_not_contiguous',
          severity: 'critical',
          message: `Variant group for base week '${baseId}' is not contiguous in weeks[] array order; an unrelated week appears between the base and one of its variants.`,
        });
      }

      // Rule 4: variantLabel required on every member.
      const missingLabel = members.filter((m) => {
        const lbl = labelById.get(m);
        return !lbl || lbl.trim() === '';
      });
      if (missingLabel.length > 0) {
        issues.push({
          code: 'structure.variant_missing_label',
          severity: 'critical',
          message: `Variant group for base week '${baseId}' is missing variantLabel on member(s): ${missingLabel
            .map((id) => `'${id}'`)
            .join(', ')}.`,
        });
      }

      // Rule 5: unique labels (case-insensitive, trimmed).
      const labelCounts = new Map<string, number>();
      for (const m of members) {
        const lbl = labelById.get(m);
        if (typeof lbl !== 'string') continue;
        const norm = lbl.trim().toLowerCase();
        if (norm === '') continue;
        labelCounts.set(norm, (labelCounts.get(norm) ?? 0) + 1);
      }
      const duplicates = [...labelCounts.entries()]
        .filter(([, count]) => count > 1)
        .map(([norm]) => norm);
      if (duplicates.length > 0) {
        issues.push({
          code: 'structure.variant_duplicate_label',
          severity: 'critical',
          message: `Variant group for base week '${baseId}' has duplicate variantLabel value(s): ${duplicates
            .map((d) => `'${d}'`)
            .join(', ')}.`,
        });
      }
    }
  }

  // Rule 6: schemaVersion gate. Emitted once at resource scope so it is not
  // duplicated per offending week.
  if (anyVariant) {
    const version = resource['schemaVersion'];
    if (typeof version === 'number' && version < 2) {
      issues.push({
        code: 'structure.variant_schema_version_too_low',
        severity: 'critical',
        message: `Resource uses programWeek.variantOf but declares schemaVersion ${version}; variants require schemaVersion >= 2 so variant-unaware loaders reject the resource.`,
      });
    }
  }
}

/**
 * Validate percentTarget range form, restMaxSecondsHint, and warmupSetCount.
 * Each of these features requires schemaVersion >= 3 so older loaders reject
 * resources they cannot interpret.
 *
 * Rules enforced:
 *   1. percentMin < percentMax (rejects equal — extractor normalizes equal
 *      ranges to the single-percent form).
 *   2. restMaxSecondsHint > restSecondsHint when both present (warning; the
 *      runtime can clamp).
 *   3. Schema-version gates fire once at resource scope per feature, mirroring
 *      the variant gate.
 */
function checkPrescriptionExtensions(
  resource: Record<string, unknown>,
  issues: SemanticIssue[],
): void {
  const structure = resource['programStructure'];
  if (!isObject(structure)) return;
  const blocks = structure['blocks'];
  if (!Array.isArray(blocks)) return;

  let usesPercentRange = false;
  let usesRestRange = false;
  let usesWarmupCount = false;

  for (const block of blocks) {
    if (!isObject(block)) continue;
    const weeks = block['weeks'];
    if (!Array.isArray(weeks)) continue;
    for (const week of weeks) {
      if (!isObject(week)) continue;
      const sessions = week['sessions'];
      if (!Array.isArray(sessions)) continue;
      for (const session of sessions) {
        if (!isObject(session)) continue;
        const groups = session['groups'];
        if (!Array.isArray(groups)) continue;
        for (const group of groups) {
          if (!isObject(group)) continue;
          const items = group['prescriptionItems'];
          if (!Array.isArray(items)) continue;
          for (const item of items) {
            if (!isObject(item)) continue;
            const itemId = typeof item['id'] === 'string' ? item['id'] : '<unknown>';

            if (typeof item['warmupSetCount'] === 'number') {
              usesWarmupCount = true;
            }

            const restMin = item['restSecondsHint'];
            const restMax = item['restMaxSecondsHint'];
            if (typeof restMax === 'number') {
              usesRestRange = true;
              if (typeof restMin === 'number' && restMax <= restMin) {
                issues.push({
                  code: 'item.rest_range_invalid',
                  severity: 'warning',
                  message: `prescriptionItem '${itemId}' has restMaxSecondsHint ${restMax} <= restSecondsHint ${restMin}; expected max strictly greater than min.`,
                });
              }
            }

            const sets = item['setPrescriptions'];
            if (!Array.isArray(sets)) continue;
            for (const set of sets) {
              if (!isObject(set)) continue;
              const setId = typeof set['id'] === 'string' ? set['id'] : '<unknown>';
              const targets = set['targets'];
              if (!Array.isArray(targets)) continue;
              for (const target of targets) {
                if (!isObject(target)) continue;
                if (target['kind'] !== 'percent') continue;
                const pMin = target['percentMin'];
                const pMax = target['percentMax'];
                if (typeof pMin === 'number' || typeof pMax === 'number') {
                  usesPercentRange = true;
                }
                if (typeof pMin === 'number' && typeof pMax === 'number' && pMin >= pMax) {
                  issues.push({
                    code: 'target.percent_range_invalid',
                    severity: 'critical',
                    message: `percentTarget in setPrescription '${setId}' has percentMin ${pMin} >= percentMax ${pMax}; expected min strictly less than max (extractor normalizes equal values to single-percent form).`,
                  });
                }
              }
            }
          }
        }
      }
    }
  }

  const version = resource['schemaVersion'];
  const v = typeof version === 'number' ? version : 0;

  if (usesPercentRange && v < 3) {
    issues.push({
      code: 'structure.percent_range_schema_version_too_low',
      severity: 'critical',
      message: `Resource uses percentTarget range form (percentMin/percentMax) but declares schemaVersion ${version}; range form requires schemaVersion >= 3 so range-unaware loaders reject the resource.`,
    });
  }
  if (usesRestRange && v < 3) {
    issues.push({
      code: 'structure.rest_range_schema_version_too_low',
      severity: 'critical',
      message: `Resource uses prescriptionItem.restMaxSecondsHint but declares schemaVersion ${version}; rest-range form requires schemaVersion >= 3.`,
    });
  }
  if (usesWarmupCount && v < 3) {
    issues.push({
      code: 'structure.warmup_count_schema_version_too_low',
      severity: 'critical',
      message: `Resource uses prescriptionItem.warmupSetCount but declares schemaVersion ${version}; warmupSetCount requires schemaVersion >= 3.`,
    });
  }
}

function checkReferenceUsage(
  resource: Record<string, unknown>,
  issues: SemanticIssue[],
): void {
  const refs = resource['requiredReferences'];
  if (!Array.isArray(refs)) return;
  const declarations = new Map<string, { supplied: boolean; declaredWeek?: number }>();
  for (const ref of refs) {
    if (!isObject(ref)) continue;
    const id = ref['id'];
    if (typeof id !== 'string') continue;
    declarations.set(id, {
      supplied: ref['supplied'] === true,
      declaredWeek:
        typeof ref['firstRunnableWeekIndex'] === 'number'
          ? (ref['firstRunnableWeekIndex'] as number)
          : undefined,
    });
  }

  const usageByRef = new Map<string, number>(); // referenceId -> earliest week consumed.
  walkPercentTargets(resource, (referenceId, weekIndex) => {
    if (!referenceId) return;
    const existing = usageByRef.get(referenceId);
    if (existing === undefined || weekIndex < existing) {
      usageByRef.set(referenceId, weekIndex);
    }
  });

  for (const [refId, earliestWeek] of usageByRef.entries()) {
    const decl = declarations.get(refId);
    if (!decl) {
      issues.push({
        code: 'reference.unknown',
        severity: 'critical',
        message: `Percent target references unknown reference id '${refId}'.`,
      });
      continue;
    }
    if (!decl.supplied) {
      if (earliestWeek <= 1) {
        issues.push({
          code: 'reference.missing_first_week',
          severity: 'critical',
          message: `Required reference '${refId}' is consumed in week ${earliestWeek} but no value has been supplied.`,
        });
      } else {
        issues.push({
          code: 'reference.missing_later_week',
          severity: 'warning',
          message: `Required reference '${refId}' is consumed in week ${earliestWeek} but no value has been supplied; activation allowed, affected workout start must block until supplied.`,
        });
      }
    }
    if (
      typeof decl.declaredWeek === 'number' &&
      decl.declaredWeek !== earliestWeek
    ) {
      issues.push({
        code: 'reference.declared_week_mismatch',
        severity: 'warning',
        message: `Required reference '${refId}' declares firstRunnableWeekIndex ${decl.declaredWeek} but is first consumed in week ${earliestWeek}.`,
      });
    }
  }

  for (const [refId] of declarations.entries()) {
    if (!usageByRef.has(refId)) {
      issues.push({
        code: 'reference.unused_declaration',
        severity: 'warning',
        message: `Required reference '${refId}' is declared but never consumed by any prescription target.`,
      });
    }
  }
}

function walkPercentTargets(
  resource: Record<string, unknown>,
  visit: (referenceId: string, weekIndex: number) => void,
): void {
  const structure = resource['programStructure'];
  if (!isObject(structure)) return;
  const blocks = structure['blocks'];
  if (!Array.isArray(blocks)) return;

  // Effective-week-index resolution: when a week declares variantOf, percent
  // targets it owns should be attributed to the base week's index, so the
  // first-runnable-week reference checks key off the runtime position rather
  // than the structural position in the linear array. Chain depth is bounded
  // to 1 by checkVariantWeeks; we still defensively cap one indirection here.
  const variantBaseIndex = buildVariantBaseIndexMap(blocks);

  for (const block of blocks) {
    if (!isObject(block)) continue;
    const weeks = block['weeks'];
    if (!Array.isArray(weeks)) continue;
    for (const week of weeks) {
      if (!isObject(week)) continue;
      const ownIndex = typeof week['weekIndex'] === 'number' ? week['weekIndex'] : 0;
      const weekId = typeof week['id'] === 'string' ? week['id'] : '';
      const effectiveIndex = variantBaseIndex.get(weekId) ?? ownIndex;
      const sessions = week['sessions'];
      if (!Array.isArray(sessions)) continue;
      for (const session of sessions) {
        if (!isObject(session)) continue;
        const groups = session['groups'];
        if (!Array.isArray(groups)) continue;
        for (const group of groups) {
          if (!isObject(group)) continue;
          const items = group['prescriptionItems'];
          if (!Array.isArray(items)) continue;
          for (const item of items) {
            if (!isObject(item)) continue;
            const sets = item['setPrescriptions'];
            if (!Array.isArray(sets)) continue;
            for (const set of sets) {
              if (!isObject(set)) continue;
              const targets = set['targets'];
              if (!Array.isArray(targets)) continue;
              for (const target of targets) {
                if (!isObject(target)) continue;
                if (target['kind'] === 'percent' && typeof target['referenceId'] === 'string') {
                  visit(target['referenceId'], effectiveIndex);
                }
              }
            }
          }
        }
      }
    }
  }
}

function buildVariantBaseIndexMap(
  blocks: readonly unknown[],
): Map<string, number> {
  const map = new Map<string, number>();
  for (const block of blocks) {
    if (!isObject(block)) continue;
    const weeks = block['weeks'];
    if (!Array.isArray(weeks)) continue;
    const indexById = new Map<string, number>();
    for (const week of weeks) {
      if (!isObject(week)) continue;
      const id = week['id'];
      const wi = week['weekIndex'];
      if (typeof id === 'string' && typeof wi === 'number') {
        indexById.set(id, wi);
      }
    }
    for (const week of weeks) {
      if (!isObject(week)) continue;
      const id = week['id'];
      const variantOf = week['variantOf'];
      if (typeof id !== 'string' || typeof variantOf !== 'string') continue;
      const baseIndex = indexById.get(variantOf);
      if (typeof baseIndex === 'number') {
        map.set(id, baseIndex);
      }
    }
  }
  return map;
}

function checkConstructSeverities(
  resource: Record<string, unknown>,
  issues: SemanticIssue[],
): void {
  const declared = resource['validationIssues'];
  if (!Array.isArray(declared)) return;
  for (const entry of declared) {
    if (!isObject(entry)) continue;
    const code = entry['code'];
    const severity = entry['severity'];
    if (typeof code !== 'string' || typeof severity !== 'string') continue;
    if (!code.startsWith('construct.')) continue;

    if (KNOWN_CRITICAL_CONSTRUCT_CODES.has(code)) {
      if (severity !== 'critical') {
        issues.push({
          code: 'construct.severity_understated',
          severity: 'critical',
          message: `Construct code '${code}' must be 'critical' per the Program Construct Matrix but was '${severity}'.`,
        });
      }
      continue;
    }
    if (KNOWN_WARNING_CONSTRUCT_CODES.has(code)) {
      if (severity !== 'warning' && severity !== 'info') {
        issues.push({
          code: 'construct.severity_overstated',
          severity: 'critical',
          message: `Construct code '${code}' is classified as note-only (warning); received '${severity}'.`,
        });
      }
      continue;
    }
    // Unknown construct code: default-critical per the matrix.
    if (severity !== 'critical') {
      issues.push({
        code: 'construct.must_be_critical',
        severity: 'critical',
        message: `Construct code '${code}' is not in the Program Construct Matrix and must default to 'critical'; received '${severity}'.`,
      });
    }
  }
}

function checkProvenance(
  resource: Record<string, unknown>,
  issues: SemanticIssue[],
): void {
  const audit = resource['importAudit'];
  if (!isObject(audit)) return;
  const sourceKind = audit['sourceKind'];
  const sourceHash = audit['sourceHash'];

  const ZERO_HASH = '0'.repeat(64);

  if (sourceKind === 'private_import') {
    if (typeof sourceHash === 'string' && sourceHash === ZERO_HASH) {
      issues.push({
        code: 'provenance.private_import_zero_hash',
        severity: 'warning',
        message:
          'private_import resources must carry a real SHA-256 source hash; received the synthetic zero sentinel.',
      });
    }
  } else if (sourceKind === 'synthetic') {
    if (typeof sourceHash === 'string' && sourceHash !== ZERO_HASH) {
      issues.push({
        code: 'provenance.synthetic_with_real_hash',
        severity: 'info',
        message:
          'Synthetic fixture carries a non-zero sourceHash; expected the all-zero synthetic sentinel.',
      });
    }
  }
}

function addStructuralId(
  id: unknown,
  seen: Set<string>,
  issues: SemanticIssue[],
): void {
  if (typeof id !== 'string') return;
  if (seen.has(id)) {
    issues.push({
      code: 'structure.duplicate_id',
      severity: 'critical',
      message: `Duplicate structural id '${id}' within programStructure.`,
    });
  } else {
    seen.add(id);
  }
}

function isContiguousFromOne(values: readonly number[]): boolean {
  if (values.length === 0) return true;
  const sorted = [...values].sort((a, b) => a - b);
  for (let i = 0; i < sorted.length; i += 1) {
    if (sorted[i] !== i + 1) return false;
  }
  return true;
}

function normalizeAliasText(text: string): string {
  return text.trim().replace(/\s+/g, ' ').toLowerCase();
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function formatScalar(value: unknown): string {
  return JSON.stringify(value);
}

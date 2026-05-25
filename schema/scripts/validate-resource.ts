#!/usr/bin/env tsx
/**
 * validate-resource — import-workflow CLI helper for the import-workflow skill.
 *
 * Reads a ProgramResource JSON file and runs the canonical validation
 * stack against it:
 *
 *   1. JSON Schema (Ajv) via `buildProgramResourceValidator()`.
 *   2. Semantic rules via `validateProgramResourceSemantics()`.
 *   3. Canonical content-hash recomputation via
 *      `computeProgramResourceContentHash()` compared to
 *      `metadata.contentHash`.
 *
 * Output is the privacy-safe import report contract owned by
 * `.github/skills/import-report-check/SKILL.md` (six per-issue fields +
 * five report-level fields). The report message text and locationHints
 * NEVER contain raw cell text, formula text, or other source excerpts —
 * those should never have been put into the resource by the import skill
 * in the first place.
 *
 * Exit codes:
 *   0  activatable (no critical issues; hash matches)
 *   1  activatable_with_warnings (no critical issues; hash matches; warnings present)
 *   2  blocked (one or more critical issues, including hash mismatch)
 *   3  rejected (schema invalid; semantic checks may also have run but
 *      schema is the authoritative gate)
 *   4  invalid CLI usage / unreadable file / unparseable JSON
 *
 * Usage:
 *   cd schema
 *   npm run validate:resource -- path\to\resource.json [--json]
 *
 * Flags:
 *   --json    Emit the report as JSON instead of the default text rendering.
 *             Useful for the import skill's sidecar import-report.json.
 */

import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { argv, exit, stderr, stdout } from 'node:process';

import {
  buildProgramResourceValidator,
  computeProgramResourceContentHash,
  validateProgramResource,
  validateProgramResourceSemantics,
  KNOWN_CRITICAL_CONSTRUCT_CODES,
  KNOWN_WARNING_CONSTRUCT_CODES,
  type SemanticIssue,
} from '../validator.js';

type Severity = 'info' | 'warning' | 'critical';

interface ReportIssue {
  readonly severity: Severity;
  readonly code: string;
  readonly sourceReference: string;
  readonly affectedProgramArea: string;
  readonly operatorAction: string;
  readonly activationImpact: string;
}

interface ReportSummary {
  readonly activationDecision:
    | 'activatable'
    | 'activatable_with_warnings'
    | 'pending_runtime_references'
    | 'blocked'
    | 'rejected';
  readonly criticalCount: number;
  readonly warningCount: number;
  readonly unknownExerciseApprovalsNeeded: number;
  readonly privacyProvenanceConcerns: readonly string[];
  readonly issues: readonly ReportIssue[];
}

function usageAndExit(): never {
  stderr.write(
    'Usage: npm run validate:resource -- <path/to/resource.json> [--json]\n',
  );
  exit(4);
}

function parseArgs(): { path: string; emitJson: boolean } {
  const args = argv.slice(2).filter((arg) => arg.length > 0);
  if (args.length === 0) usageAndExit();

  let emitJson = false;
  const positional: string[] = [];
  for (const arg of args) {
    if (arg === '--json') {
      emitJson = true;
    } else if (arg.startsWith('-')) {
      stderr.write(`Unknown flag: ${arg}\n`);
      usageAndExit();
    } else {
      positional.push(arg);
    }
  }
  if (positional.length !== 1) usageAndExit();
  return { path: resolve(positional[0]!), emitJson };
}

function readJson(path: string): unknown {
  let raw: string;
  try {
    raw = readFileSync(path, 'utf-8');
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    stderr.write(`Cannot read ${path}: ${message}\n`);
    exit(4);
  }
  try {
    return JSON.parse(raw);
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    stderr.write(`Cannot parse ${path} as JSON: ${message}\n`);
    exit(4);
  }
}

// ---------------------------------------------------------------------------
// Issue projection
// ---------------------------------------------------------------------------

const PROGRAM_AREA_BY_PREFIX: Readonly<Record<string, string>> = {
  schema: 'resource structure',
  metadata: 'program metadata',
  status: 'activation status',
  construct: 'program constructs',
  exercise: 'exercise catalog',
  reference: 'training maxes / required references',
  structure: 'program structure',
  progression: 'progression rules',
  provenance: 'source provenance',
};

function severityOfConstructCode(code: string): Severity {
  if (KNOWN_WARNING_CONSTRUCT_CODES.has(code)) return 'warning';
  if (KNOWN_CRITICAL_CONSTRUCT_CODES.has(code)) return 'critical';
  // Unknown construct codes default to critical per
  // docs/architecture.md Validation severity contract.
  return 'critical';
}

function operatorActionFor(code: string, severity: Severity): string {
  if (code.startsWith('schema.')) return 'Fix the resource shape to match the JSON Schema.';
  if (code === 'metadata.content_hash_mismatch')
    return 'Recompute metadata.contentHash via schema/hash.ts and re-emit the resource.';
  if (code === 'metadata.unsupported_schema_version')
    return 'Re-emit with a supported schemaVersion (see SUPPORTED_SCHEMA_VERSIONS).';
  if (code === 'metadata.version_conflict')
    return 'Pick a new programVersionId or restore the existing content; activated versions are immutable.';
  if (code === 'status.activatable_with_critical')
    return 'Resolve all critical issues or set validationStatus to blocked.';
  if (code.startsWith('construct.'))
    return severity === 'critical'
      ? 'Replace the construct with a supported pattern, or omit it for MVP.'
      : 'Convert the construct to a note-only annotation if it cannot be executed.';
  if (code === 'exercise.unknown_reference')
    return 'Approve the candidate exercise, alias it to an existing canonical exercise, or remove the reference.';
  if (code === 'exercise.missing_required_canonical')
    return 'Add the canonical exercise to exerciseCatalog or alias it.';
  if (code === 'reference.missing_first_week_value')
    return 'Provide the first-week training max / required value before activation.';
  if (code === 'reference.missing_later_week_value')
    return 'Provide the value before the affected workout starts; activation may proceed.';
  if (code.startsWith('structure.'))
    return 'Fix the program structure ordering / IDs / coverage.';
  if (code.startsWith('progression.'))
    return 'Use a supported progression pattern or move to manual progression.';
  if (code.startsWith('provenance.'))
    return 'Add the required provenance field to importAudit.';
  return 'See message for required action.';
}

function activationImpactFor(code: string, severity: Severity): string {
  if (severity === 'critical') {
    if (code === 'reference.missing_first_week_value')
      return 'Blocks activation. First-week missing reference values must be supplied.';
    if (code === 'metadata.content_hash_mismatch')
      return 'Blocks activation. Resource identity cannot be trusted.';
    if (code === 'metadata.version_conflict')
      return 'Blocks activation. Same programVersionId already exists with different content.';
    return 'Blocks activation until resolved.';
  }
  if (severity === 'warning') {
    if (code === 'reference.missing_later_week_value')
      return 'Activatable; blocks the affected workout at runtime until supplied.';
    return 'Activatable with warnings; will surface in the run UI.';
  }
  return 'Informational; does not affect activation.';
}

function programAreaFor(code: string): string {
  const prefix = code.split('.', 1)[0]!;
  return PROGRAM_AREA_BY_PREFIX[prefix] ?? 'program resource';
}

function projectSemanticIssue(issue: SemanticIssue): ReportIssue {
  return {
    severity: issue.severity,
    code: issue.code,
    sourceReference: issue.locationHint ?? 'resource',
    affectedProgramArea: programAreaFor(issue.code),
    operatorAction: operatorActionFor(issue.code, issue.severity),
    activationImpact: activationImpactFor(issue.code, issue.severity),
  };
}

function projectSchemaError(err: unknown): ReportIssue {
  const e = err as { instancePath?: string; schemaPath?: string; message?: string };
  const path = e.instancePath && e.instancePath.length > 0 ? e.instancePath : '/';
  return {
    severity: 'critical',
    code: 'schema.invalid',
    sourceReference: path,
    affectedProgramArea: 'resource structure',
    operatorAction:
      'Fix the resource shape to match the JSON Schema, then revalidate.',
    activationImpact:
      'Rejected: a schema-invalid resource cannot be activated under any condition.',
  };
}

function projectHashMismatchIssue(): ReportIssue {
  return {
    severity: 'critical',
    code: 'metadata.content_hash_mismatch',
    sourceReference: '/metadata/contentHash',
    affectedProgramArea: 'program metadata',
    operatorAction: operatorActionFor('metadata.content_hash_mismatch', 'critical'),
    activationImpact: activationImpactFor('metadata.content_hash_mismatch', 'critical'),
  };
}
void severityOfConstructCode; // exported via re-use through schema/semantics
void projectHashMismatchIssue;

// ---------------------------------------------------------------------------
// Privacy guard
// ---------------------------------------------------------------------------

/**
 * Defensive guard. The validators in schema/ never put cell values into
 * messages, but we still scan the projected report for sentinel patterns
 * that would indicate raw source bytes leaked in. If anything matches,
 * we flag a privacy concern at the report level (the issue itself stays
 * unchanged — operator needs to know).
 */
const PRIVACY_RED_FLAGS: ReadonlyArray<RegExp> = [
  /\b(?:=|@)[A-Z][A-Z0-9_]*\s*\(/, // formula-ish patterns
  /[\u201C\u201D\u2018\u2019]/, // smart quotes (very rare in IDs/messages)
];

function scanPrivacy(issues: readonly ReportIssue[]): readonly string[] {
  const concerns: string[] = [];
  for (const issue of issues) {
    const corpus = `${issue.code}\n${issue.sourceReference}\n${issue.operatorAction}\n${issue.activationImpact}`;
    for (const pattern of PRIVACY_RED_FLAGS) {
      if (pattern.test(corpus)) {
        concerns.push(
          `Issue ${issue.code} contains a pattern that may be source-derived (${pattern.source}); confirm before publishing the report.`,
        );
      }
    }
  }
  return concerns;
}

// ---------------------------------------------------------------------------
// Main
// ---------------------------------------------------------------------------

interface Metadata {
  contentHash?: unknown;
}
interface DeclaredIssue {
  severity?: unknown;
  code?: unknown;
  message?: unknown;
  locationHint?: unknown;
}
interface ResourceShape {
  metadata?: Metadata;
  validationStatus?: unknown;
  validationIssues?: ReadonlyArray<DeclaredIssue>;
  exerciseCatalog?: ReadonlyArray<{ canonicalExerciseId?: unknown }>;
  programStructure?: {
    blocks?: ReadonlyArray<{
      weeks?: ReadonlyArray<{
        sessions?: ReadonlyArray<{
          exerciseGroups?: ReadonlyArray<{
            prescriptionItems?: ReadonlyArray<{ prescribedExerciseId?: unknown }>;
          }>;
        }>;
      }>;
    }>;
  };
}

function countUnknownExerciseApprovalsNeeded(resource: unknown): number {
  const r = resource as ResourceShape | null;
  if (!r || typeof r !== 'object') return 0;
  const knownIds = new Set<string>();
  for (const entry of r.exerciseCatalog ?? []) {
    if (typeof entry?.canonicalExerciseId === 'string') {
      knownIds.add(entry.canonicalExerciseId);
    }
  }
  const referenced = new Set<string>();
  for (const block of r.programStructure?.blocks ?? []) {
    for (const week of block.weeks ?? []) {
      for (const session of week.sessions ?? []) {
        for (const group of session.exerciseGroups ?? []) {
          for (const item of group.prescriptionItems ?? []) {
            if (typeof item?.prescribedExerciseId === 'string') {
              referenced.add(item.prescribedExerciseId);
            }
          }
        }
      }
    }
  }
  let unknown = 0;
  for (const id of referenced) {
    if (!knownIds.has(id)) unknown += 1;
  }
  return unknown;
}

function projectDeclaredIssue(issue: DeclaredIssue): ReportIssue | null {
  const severity = typeof issue.severity === 'string' ? issue.severity : '';
  if (severity !== 'info' && severity !== 'warning' && severity !== 'critical') {
    return null;
  }
  const code = typeof issue.code === 'string' && issue.code.length > 0 ? issue.code : 'unknown';
  const locationHint = typeof issue.locationHint === 'string' ? issue.locationHint : 'resource';
  return {
    severity,
    code,
    sourceReference: locationHint,
    affectedProgramArea: programAreaFor(code),
    operatorAction: operatorActionFor(code, severity),
    activationImpact: activationImpactFor(code, severity),
  };
}

function main(): never {
  const { path, emitJson } = parseArgs();
  const resource = readJson(path);

  const validator = buildProgramResourceValidator();
  const schemaOutcome = validateProgramResource(resource, validator);

  const issues: ReportIssue[] = [];
  const seen = new Set<string>();
  const addIssue = (issue: ReportIssue): void => {
    const key = `${issue.severity}|${issue.code}|${issue.sourceReference}`;
    if (seen.has(key)) return;
    seen.add(key);
    issues.push(issue);
  };

  if (!schemaOutcome.valid) {
    for (const err of schemaOutcome.errors) {
      addIssue(projectSchemaError(err));
    }
  }

  // Declared issues from the resource itself. The resource declares its
  // status; the import skill should have already written these. We surface
  // them in the report so the operator sees the full picture.
  const r0 = resource as ResourceShape | null;
  if (r0 && Array.isArray(r0.validationIssues)) {
    for (const decl of r0.validationIssues) {
      const projected = projectDeclaredIssue(decl);
      if (projected) addIssue(projected);
    }
  }

  // Run semantics regardless; it tolerates malformed input by reporting
  // schema.malformed_root and stopping cleanly.
  const semanticReport = validateProgramResourceSemantics(resource);
  for (const issue of semanticReport.issues) {
    addIssue(projectSemanticIssue(issue));
  }

  // Independent hash check (semantics already does this, but we double-check
  // here to be the structural "did the import skill compute the hash"
  // self-check called for in the import-workflow plan).
  const r = r0;
  const declared =
    r && typeof r === 'object' && r.metadata && typeof r.metadata === 'object'
      ? (r.metadata.contentHash as unknown)
      : undefined;
  let canonical: string | undefined;
  try {
    canonical = computeProgramResourceContentHash(resource);
  } catch {
    canonical = undefined;
  }
  if (
    canonical !== undefined &&
    typeof declared === 'string' &&
    declared !== canonical &&
    // Don't double-emit if semantics already flagged it.
    !issues.some((i) => i.code === 'metadata.content_hash_mismatch')
  ) {
    addIssue(projectHashMismatchIssue());
  }

  const criticalCount = issues.filter((i) => i.severity === 'critical').length;
  const warningCount = issues.filter((i) => i.severity === 'warning').length;
  const unknownApprovals = countUnknownExerciseApprovalsNeeded(resource);
  const privacy = scanPrivacy(issues);

  const declaredStatus =
    r0 && typeof r0.validationStatus === 'string' ? r0.validationStatus : undefined;

  // pending_runtime_references is gated by semantics: the only allowed
  // semantic critical is `reference.missing_first_week`. If anything else
  // critical is present, semantics raises `status.pending_with_blocking_critical`
  // and the artifact rolls down to blocked.
  const onlyPendingRefCriticals =
    criticalCount > 0 &&
    issues.every(
      (i) =>
        i.severity !== 'critical' ||
        i.code === 'reference.missing_first_week',
    );

  const activationDecision: ReportSummary['activationDecision'] =
    !schemaOutcome.valid
      ? 'rejected'
      : declaredStatus === 'rejected'
        ? 'rejected'
        : declaredStatus === 'pending_runtime_references' && onlyPendingRefCriticals
          ? 'pending_runtime_references'
          : criticalCount > 0 || declaredStatus === 'blocked'
            ? 'blocked'
            : warningCount > 0 || declaredStatus === 'activatable_with_warnings'
              ? 'activatable_with_warnings'
              : 'activatable';

  const summary: ReportSummary = {
    activationDecision,
    criticalCount,
    warningCount,
    unknownExerciseApprovalsNeeded: unknownApprovals,
    privacyProvenanceConcerns: privacy,
    issues,
  };

  if (emitJson) {
    stdout.write(JSON.stringify(summary, null, 2) + '\n');
  } else {
    writeText(summary, path);
  }

  switch (activationDecision) {
    case 'activatable':
      exit(0);
    case 'activatable_with_warnings':
      exit(1);
    case 'pending_runtime_references':
      exit(1);
    case 'blocked':
      exit(2);
    case 'rejected':
      exit(3);
  }
}

function writeText(summary: ReportSummary, path: string): void {
  const lines: string[] = [];
  lines.push(`Resource:    ${path}`);
  lines.push(`Activation:  ${summary.activationDecision}`);
  lines.push(`Critical:    ${summary.criticalCount}`);
  lines.push(`Warnings:    ${summary.warningCount}`);
  lines.push(`Approvals:   ${summary.unknownExerciseApprovalsNeeded} unknown exercise(s) need operator approval`);
  if (summary.privacyProvenanceConcerns.length > 0) {
    lines.push(`Privacy:     ${summary.privacyProvenanceConcerns.length} concern(s):`);
    for (const c of summary.privacyProvenanceConcerns) lines.push(`  - ${c}`);
  } else {
    lines.push(`Privacy:     no source-derived patterns detected in report fields`);
  }
  if (summary.issues.length > 0) {
    lines.push('');
    lines.push('Issues:');
    for (const i of summary.issues) {
      lines.push(`  [${i.severity}] ${i.code}`);
      lines.push(`    at:     ${i.sourceReference}`);
      lines.push(`    area:   ${i.affectedProgramArea}`);
      lines.push(`    action: ${i.operatorAction}`);
      lines.push(`    impact: ${i.activationImpact}`);
    }
  }
  stdout.write(lines.join('\n') + '\n');
}

main();

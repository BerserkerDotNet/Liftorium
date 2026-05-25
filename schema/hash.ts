import { createHash } from 'node:crypto';

/**
 * Build the canonical content view of a program resource for hashing.
 *
 * The content hash covers program content only, not validation state or
 * import audit metadata. ProgramResourceLoader (android-program-runner) uses
 * `metadata.contentHash` to detect the "same program version id with
 * different content = conflict" case, so the hash must remain stable
 * across re-validation of the same content.
 *
 * Excludes:
 *   - `validationStatus`, `validationIssues` (mutate during operator correction).
 *   - `importAudit` (mutates between imports of the same content).
 *   - `metadata.contentHash` itself (avoids chicken-and-egg).
 */
export function canonicalizeForContentHash(resource: unknown): unknown {
  if (!isObject(resource)) {
    return resource;
  }
  const view: Record<string, unknown> = {};
  if (typeof resource['schemaVersion'] !== 'undefined') {
    view['schemaVersion'] = resource['schemaVersion'];
  }
  if (isObject(resource['metadata'])) {
    const md: Record<string, unknown> = {};
    for (const [k, v] of Object.entries(resource['metadata'])) {
      if (k === 'contentHash') continue;
      md[k] = v;
    }
    view['metadata'] = md;
  }
  for (const key of [
    'programDefaults',
    'exerciseCatalog',
    'requiredReferences',
    'programStructure',
    'progressionRules',
  ] as const) {
    if (typeof resource[key] !== 'undefined') {
      view[key] = resource[key];
    }
  }
  return view;
}

/**
 * Deterministically serialize a JSON-compatible value with sorted object
 * keys so that the same content always hashes to the same digest, even if
 * the operator's editor reorders fields.
 */
export function canonicalJsonStringify(value: unknown): string {
  if (value === null || typeof value !== 'object') {
    return JSON.stringify(value);
  }
  if (Array.isArray(value)) {
    return '[' + value.map(canonicalJsonStringify).join(',') + ']';
  }
  const entries = Object.entries(value as Record<string, unknown>).filter(
    ([, v]) => typeof v !== 'undefined',
  );
  // Object.entries returns unique string keys, so the comparator never
  // needs an "equal" branch. Use a 2-arm comparator for honest coverage.
  entries.sort(([a], [b]) => (a < b ? -1 : 1));
  return (
    '{' +
    entries
      .map(([k, v]) => JSON.stringify(k) + ':' + canonicalJsonStringify(v))
      .join(',') +
    '}'
  );
}

/** Compute the SHA-256 content hash for a program resource. */
export function computeProgramResourceContentHash(resource: unknown): string {
  const view = canonicalizeForContentHash(resource);
  const canonical = canonicalJsonStringify(view);
  return createHash('sha256').update(canonical, 'utf-8').digest('hex');
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

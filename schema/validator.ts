import Ajv2020, { type ErrorObject, type ValidateFunction } from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const moduleDir = dirname(fileURLToPath(import.meta.url));

/** Load the canonical program-resource JSON Schema from disk. */
export function loadProgramResourceSchema(): unknown {
  const schemaPath = resolve(moduleDir, 'program-resource.schema.json');
  return JSON.parse(readFileSync(schemaPath, 'utf-8'));
}

/** Build a strict Ajv validator for the program-resource schema. */
export function buildProgramResourceValidator(): ValidateFunction {
  const schema = loadProgramResourceSchema() as Record<string, unknown>;
  const ajv = new Ajv2020({ allErrors: true, strict: true, strictRequired: false });
  addFormats(ajv);
  return ajv.compile(schema);
}

export interface ValidationOutcome {
  readonly valid: boolean;
  readonly errors: readonly ErrorObject[];
}

/** Validate a parsed JSON document against the program-resource schema. */
export function validateProgramResource(
  data: unknown,
  validator: ValidateFunction = buildProgramResourceValidator(),
): ValidationOutcome {
  const valid = validator(data);
  return {
    valid,
    errors: valid ? [] : (validator.errors ?? []),
  };
}

export { computeProgramResourceContentHash, canonicalizeForContentHash } from './hash.js';
export {
  validateProgramResourceSemantics,
  type SemanticIssue,
  type SemanticReport,
  SUPPORTED_SCHEMA_VERSIONS,
  KNOWN_CRITICAL_CONSTRUCT_CODES,
  KNOWN_WARNING_CONSTRUCT_CODES,
} from './semantics.js';

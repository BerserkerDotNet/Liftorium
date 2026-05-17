/**
 * Phase 1 placeholder for the spreadsheet import workflow.
 *
 * Phase 3 (`import-workflow`) replaces this with the real parser, normalizer,
 * exercise catalog mapper, validator, and correction loop. The placeholder
 * keeps the package importable, exposes the consent-required entry shape, and
 * gives downstream workstreams a stable import path.
 */

export interface ImportRequest {
  /** Absolute path to the source workbook. Never persisted; only its hash is. */
  readonly workbookPath: string;
  /** Operator-confirmed consent for cloud-assisted source processing. */
  readonly cloudAssistanceConsent: boolean;
}

export interface ImportPlaceholderResult {
  readonly status: 'placeholder';
  readonly message: string;
}

/**
 * Returns a placeholder result. The real implementation in Phase 3 emits a
 * finalized program resource and validation report.
 */
export function runImport(request: ImportRequest): ImportPlaceholderResult {
  if (!request.workbookPath || request.workbookPath.trim().length === 0) {
    throw new Error('workbookPath is required');
  }
  return {
    status: 'placeholder',
    message:
      'Phase 1 import harness is wired; Phase 3 will replace this with the real pipeline.',
  };
}

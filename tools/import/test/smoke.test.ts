import { describe, expect, it } from 'vitest';
import { runImport } from '../src/index';

describe('tools/import smoke', () => {
  it('returns a placeholder result when invoked with a workbook path', () => {
    const result = runImport({
      workbookPath: 'C:/tmp/example.xlsx',
      cloudAssistanceConsent: false,
    });
    expect(result.status).toBe('placeholder');
    expect(result.message).toMatch(/phase 3/i);
  });

  it('rejects an empty workbook path', () => {
    expect(() =>
      runImport({ workbookPath: '   ', cloudAssistanceConsent: false }),
    ).toThrow(/workbookPath/);
  });
});

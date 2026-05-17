export interface AppProps {
  /** Build identifier displayed for snapshot freshness debugging. */
  readonly buildLabel?: string;
}

/**
 * Phase 1 placeholder shell for the Liftorium read-only Web surface.
 *
 * The MVP Web app is snapshot-based and read-only (see `docs/architecture.md`
 * "Web data-source boundary"). This component intentionally renders nothing
 * actionable; downstream workstreams replace it with real read-only views over
 * versioned snapshot inputs.
 */
export function App({ buildLabel }: AppProps): JSX.Element {
  return (
    <main aria-label="Liftorium read-only Web surface">
      <h1>Liftorium</h1>
      <p>Read-only Web surface. Workout logging happens in the Android app.</p>
      {buildLabel !== undefined && (
        <p>
          <small data-testid="build-label">Build: {buildLabel}</small>
        </p>
      )}
    </main>
  );
}

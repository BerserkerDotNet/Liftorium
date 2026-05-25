package dev.liftorium.domain.resource

import dev.liftorium.core.KoverIgnore
import dev.liftorium.domain.common.ProgramVersionId

/**
 * Result of loading a finalized program resource on Android.
 *
 * android-program-runner implements a `recheck_only` validation strategy (see ADR in
 * `docs/decisions.md`): the device re-asserts file integrity (parsing,
 * schemaVersion gate, content-hash recompute) and activation-state gate,
 * but does NOT re-run semantic validation. import-workflow is the source of
 * truth; this type names every state the runner can be in.
 */
@KoverIgnore
public sealed class LoaderResult {
    /** A new [LoadedProgramVersion][programVersionId] was written to the DB. */
    @KoverIgnore
    public data class Loaded(val programVersionId: ProgramVersionId) : LoaderResult()

    /**
     * The same program version was already present with the same content
     * hash. No DB mutation occurred; this is a successful no-op.
     */
    @KoverIgnore
    public data class Idempotent(val programVersionId: ProgramVersionId) : LoaderResult()

    @KoverIgnore
    public sealed class Failure : LoaderResult() {
        @KoverIgnore
        public data class Malformed(val reason: String) : Failure()

        @KoverIgnore
        public data class UnsupportedSchemaVersion(val actual: Int, val supportedMax: Int) : Failure()

        @KoverIgnore
        public data class HashMismatch(val declared: String, val computed: String) : Failure()

        @KoverIgnore
        public data class StatusBlocked(
            val status: ValidationStatus,
            val criticalIssueCodes: List<String>,
        ) : Failure()

        /**
         * Same `programVersionId` already present in Room but with a
         * different `contentHash`. import-workflow program versions are immutable;
         * a conflict means either the source was edited without bumping
         * `programVersionId` or the file has been tampered with.
         *
         * Traceability: `IT-IMP-006` per `docs/product.md` A7.
         */
        @KoverIgnore
        public data class ConflictDifferentHash(
            val programVersionId: ProgramVersionId,
            val existingHash: String,
            val incomingHash: String,
        ) : Failure()
    }
}

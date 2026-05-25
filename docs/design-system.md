# Liftorium design system

This document is the contributor reference for the canonical Compose tokens
and shared components added by the `android-ui-polish` workstream. New
Android UI work MUST consume these instead of reaching for raw Material 3
defaults — see `docs/workstreams/android-ui-polish.md` Handoff section.

## Theme entry point

Wrap every Liftorium UI surface in `LiftoriumTheme {}` (from
`dev.liftorium.app.ui.theme`). Never wrap in raw `MaterialTheme {}`; the
brand palette, typography overrides, shape scale, and the spacing /
dimension `CompositionLocal`s only get installed by `LiftoriumTheme`.

```kotlin
LiftoriumTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        // screen content
    }
}
```

Dynamic color is intentionally not supported — see the rationale comment
at the top of `LiftoriumColorScheme.kt`. Brand consistency across devices
and deterministic Paparazzi goldens both matter more than per-wallpaper
theming for an offline lifting app.

## Tokens

| Token | How to reach | When to use |
| --- | --- | --- |
| Colors | `MaterialTheme.colorScheme.*` | All color reads. Never hard-code `Color(0x...)` in a screen file. |
| Typography (M3) | `MaterialTheme.typography.*` | Headers, body, labels, button text — the M3 baseline scale. |
| `numeric` typography | `LiftoriumTokens.lTypography.numeric` | Any displayed weight, rep, set, or percent. Enables `tnum` so digits column-align across rows. |
| Spacing | `LiftoriumTokens.spacing.xs / sm / md / lg / xl / xxl` (4 / 8 / 12 / 16 / 24 / 32 dp) | Every padding, gap, arrangement, and explicit `Spacer` size. No raw `.dp` values in screen code. |
| Dimens | `LiftoriumTokens.dimens.*` (`cardElevation`, `badgeMinHeight`, `dividerThickness`, …) | Component-internal sizes that shouldn't be tuned per-call-site. |
| Shapes | `MaterialTheme.shapes.*` | Card / Surface corner radii. |

### Spacing decision rules

- `xs` (4 dp) — adjacent line groups inside the same logical block (label → status).
- `sm` (8 dp) — chip gaps, dialog inner row spacing, between primary text and a metadata line.
- `md` (12 dp) — between cards in a vertical list; padding inside a small card.
- `lg` (16 dp) — outer screen horizontal padding; padding inside a primary card.
- `xl` (24 dp) — centered empty-state padding.
- `xxl` (32 dp) — reserved for future hero / blank-slate layouts.

### Typography decision rules

- `titleMedium` — list row primary text (program name, exercise name).
- `titleSmall` — section / block headers inside a card.
- `bodyMedium` — descriptive text, week labels.
- `bodySmall` — metadata (version, attribution, set descriptions when not numeric).
- `labelMedium` — chip / badge labels (used internally by `StatusBadge`).
- `LiftoriumTokens.lTypography.numeric` — any weight / set / percent line.

## Shared components

### `StatusBadge(text, modifier?, tone = Neutral)`

Non-tappable pill rendered as a plain `Surface` + `Text`. Use it for any
state that the user reads but does not tap (validation status, exercise
role). Do NOT use Material `Chip`/`AssistChip`/`SuggestionChip` for
display-only pills — those carry implicit click semantics that confuse
TalkBack.

Pick a `BadgeTone` value rather than passing raw colors:

| Tone | Use for |
| --- | --- |
| `Primary` | Positive / activatable. |
| `Tertiary` | Pending / needs attention but not an error. |
| `Error` | Blocked / rejected. |
| `Neutral` | Generic labels (exercise role, unknown statuses). |

Validation-status mapping is centralised in
`dev.liftorium.app.ui.program.ValidationStatusBadge` — call
`ValidationStatusBadge.of(status)` from new surfaces rather than mapping
the strings yourself.

### `EmptyState(title, body, modifier?, semanticsTag?)`

Centered empty-state column for list surfaces. Pass `semanticsTag` to
expose the empty state to UI tests (e.g. the program library uses
`"empty-library"`).

## File-organisation rules

- One public Composable per file when feasible, named for that Composable
  (Detekt `MatchingDeclarationName`). Private helpers stay co-located with
  the single public Composable that uses them.
- Promote a private helper to `:app/ui/components/` the moment a second
  caller needs it.
- Soft ceiling ~400 lines / 3 public top-level declarations is a "still
  one concern?" review prompt. Hard ceiling ~800 lines / 6 public
  declarations is Detekt-enforced.

## Visual-review checklist

When adding or changing a screen, after running
`:app:recordPaparazziDebug`, review the generated PNG against this list
before declaring the change done:

1. Light and dark snapshots both render without missing text.
2. No raw Material chip is used for a non-tappable status pill.
3. Numeric values (weights, reps, percents) use the `numeric` style.
4. All padding looks like one of the spacing tokens — no eyeballed `13.dp`
   or `17.dp` gaps.
5. Empty states use `EmptyState`, not an inline `Column { Text; Spacer; Text }`.
6. Status pills go through `ValidationStatusBadge.of(...)` for known
   validation statuses.

## Deferred (not in this workstream)

- Navigation Compose adoption — still ad-hoc `NavRoute` + `mutableStateOf`
  in `LiftoriumNavHost`. Tracked in `docs/workstreams/android-ui-polish.md`.
- Brand adaptive icon + monochrome icon + themed icon. Tracked in the
  same workstream doc.

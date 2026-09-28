# UX/UI State Machine (PlantUML) — Plan

**Deliverable:** `docs/diagrams/ux-state-machine.puml` (new file) + embedded in docs.
**Base:** `JKubeTermApp.java` — connection lifecycle + edit-mode + action flows.

## Design decisions
1. **Two-level state machine** (recommended, single diagram):
   - Main lifecycle: `[*] → Startup → ContextsReady → Connecting → Connected → Closed`
   - Sub-states from `Connected`: `EditOff`, `ConfirmDialog`, `Refresh`, `Apply`, `Delete`, `Logs`, `Exec`, `PortForward`, `Scale`, `Restart`, `Save/New`
2. **Transitions based on real code:**
   - `Connected → Refresh` (kind/ns change or Refresh click)
   - `Connected → ConfirmDialog` (Apply/Delete/Scale/Restart buttons)
   - `ConfirmDialog → Apply` / `ConfirmDialog → Delete` (OK) or back to `Connected` (Cancel)
   - `Connected → LogsDialog` (Pod selected) → `Logs` (container chosen) → back to `Connected`
   - `Connected → EditOn` (`Edit YAML` checkbox) → `EditOff` (row selected)
   - `Connected → PortForward` (process starts, tracked) → `Connected` (output shown on process end, but process continues until `stop()` kills it)
3. **Not included:** individual `ChoiceDialog` sub-states for container selection (simplified as `LogsDialog → Logs → Connected`); `PortForward` does not have a separate persistent state (process tracked externally by `portProcesses`).

## Open question (answered: single diagram, two-level):
- Confirm: single `.puml` file with nested `state` blocks, embedded in `docs/diagrams/plantuml.md` or referenced from `docs/index.md`.

## Validation:
- `plantuml -checkonly` passes.
- States and transitions match `JKubeTermApp` event handlers (`connect`, `refresh`, `applyYaml`, `deleteSelected`, `logs`, `exec`, `portForward`, `scale`, `restart`, `saveYaml`, edit mode toggle, `stop`).

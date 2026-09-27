# UI / UX Wireframe (draw.io)

The interactive wireframe is exported in `docs/ui-wireframe.drawio` (openable in [diagrams.net](https://app.diagrams.net/)). Each major UI region links back to the corresponding architecture documentation page; open the file in a browser with the docs alongside to navigate between the mockup and the detailed technical descriptions.

## Layout (BorderPane structure)

The window (`1380 x 840`) matches `JKubeTermApp.start(Stage)` (`JKubeTermApp.java:82`).

| Region | Mockup label | What it shows / controls | Clickable link to docs |
|---|---|---|---|
| **Top toolbar** | Context combo + Connect + ↻ Config + Namespace combo + Refresh | `KubeconfigLoader` context selection; `new KubernetesService` on Connect; `loadContexts()` on Config reload; namespace change triggers `refresh()`; `Refresh` runs `refresh()` | [components.md](architecture/components.md) — toolbar description; [data-flow.md](architecture/data-flow.md) — connect/refresh flows; [kubeconfig.md](architecture/kubeconfig.md) — discovery algorithm |
| **Left** | Resource kinds (`ListView<ResourceKind>`) | 14 kinds (PODS pre-selected); selection → `refresh()`; kind label from `ResourceKind` enum | [resources.md](architecture/resources.md) — kind catalog with Fabric8 DSL mappings |
| **Middle — filter** | Filter input (`TextField`) | Local filter on `currentItems`: `name.toLowerCase(Locale.ROOT).contains(query)` | [components.md](architecture/components.md) — `showItems()` method |
| **Middle — table** | `TableView<HasMetadata>` (Name / Namespace / Kind / Created) | Columns mapped via `column()`; rows from `service.list()`; row selection loads `service.yaml()`; `CONSTRAINED_RESIZE_POLICY` | [components.md](architecture/components.md) — column definitions; [data-flow.md](architecture/data-flow.md) — refresh sequence |
| **Right — actions** | `FlowPane` with 11 buttons + edit-mode checkbox | `Edit YAML` toggles `details.setEditable()`; Apply → `service.apply()`; Delete → `service.delete()`; Logs → two-phase dialog + `service.logs()`; Exec → `ExternalTools.kubectl()` + `run()`; Port-forward → `ProcessBuilder` (loopback `--address 127.0.0.1`); Scale → `service.scaleDeployment()`; Restart → `restartDeployment()` (restartedAt annotation); Helm → `ExternalTools.helm()`; Save YAML / New YAML — file export / pre-filled ConfigMap | [data-flow.md](architecture/data-flow.md) — apply, delete, logs, exec, scale/restart, helm; [external-tools.md](architecture/external-tools.md) — argv and timeout rules; [resources.md](architecture/resources.md) — kind/applicability rules |
| **Right — manifest** | Manifest `TextArea` | Read-only by default (`service.yaml()`); `editMode.selectedProperty()` toggles editability; `applyYaml()` validates via `Serialization.unmarshal()` with kind/apiVersion/name checks and `serverSideApply()` | [components.md](architecture/components.md) — apply validation rules (`isClusterScoped`, namespace injection); [decisions.md](architecture/decisions.md) — ADR-0006 (server-side apply) |
| **Right — console** | Console `TextArea` | `console.setText()` (single message, overwritten by latest operation); output from `Platform.runLater()` results of `logs()`, `exec()`, `portForward()`, `apply()`, `delete()`, `helm()`; error dialog triggered by `task()` exception wrapper (`ThrowingAction`) | [threading.md](architecture/threading.md) — worker/task pattern; [data-flow.md](architecture/data-flow.md) — error-handling conventions |
| **Bottom — status** | Status bar (`Label`) | Shows context load count (`"N contexts from M kubeconfig file(s)"`), connect progress (`"Connecting to X…"`), connected (`"Connected: X | Kubernetes Y"` with `version()`), refresh results (`"N Pods | context"`), errors (`status.setText(title + ": " + ex.getMessage())`) | [threading.md](architecture/threading.md) — worker shutdown + `stop()`; [data-flow.md](architecture/data-flow.md) — connection lifecycle state diagram |

## Clickable links (draw.io hyperlinks)

In the `.drawio` file, every major region (toolbar, kind list, table, filter, manifest, console, actions, buttons, status bar) has an associated hyperlink pointing back to the architecture documentation. Open the file in [diagrams.net](https://app.diagrams.net/) with the `docs/` folder open; clicking a hyperlink navigates to the corresponding markdown section.

The wireframe also includes two auxiliary diagrams embedded as separate pages:
- **Button Actions detail** (second diagram in the file): each button expanded with its multi-step flow, linking to the sequence diagrams and ADRs.
- **Navigation Flow** (third diagram): shows how user actions trigger `task()` on the worker, how results flow back through `Platform.runLater`, and how `service.close()` and `portProcesses.destroy()` are triggered by `stop()`.

These pages are intended for interactive exploration: click a button, follow its link to the architecture docs, read the sequence diagram or ADR, and return to the wireframe to explore the next interaction path.

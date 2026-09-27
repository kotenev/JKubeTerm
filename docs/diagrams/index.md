# Diagram notation guide

JKubeTerm's architecture is documented with **four notations**, each with a
clear role. All four describe the *same* model — element names below are
deliberately kept identical across notations; when the architecture changes,
update all pages that render the affected view.

## Which notation for what

| Notation | Strength | Used in this site for | Source of truth |
|---|---|---|---|
| **Mermaid** | Renders inline in MkDocs (Mermaid 11 via CDN) — no tooling needed for readers | C4 context/container/component, sequences, state machine, flowcharts, class diagram | [mermaid.md](mermaid.md) + inline on architecture pages |
| **PlantUML** | Full UML expressiveness (class attributes, deployment nodes, activity branches) + ArchiMate rendering via the standard library; scriptable | Detailed component, class, sequence, state, deployment, activity diagrams | [plantuml.md](plantuml.md) |
| **Archi / ArchiMate** | Tool-based enterprise model: elements, relationships and views editable in the [Archi tool](https://www.archimatetool.com/); exchangeable `.archi` file | Business/Application/Technology layering, service realisation view | [archi.md](archi.md) + `docs/models/jkubeterm.archi` |
| **Structurizr (C4 DSL)** | Model-as-code C4 with a single workspace defining elements once and generating multiple views (context, container, component, dynamic) | C4 views + dynamic connect flow | [structurizr.md](structurizr.md) + `docs/models/jkubeterm-structurizr.dsl` |

## View → notation matrix

| View | Mermaid | PlantUML | ArchiMate | Structurizr |
|---|:-:|:-:|:-:|:-:|
| System context (C4 L1) | ✔ `C4Context` | ✔ component-style | ✔ application structure view | ✔ `systemContext` |
| Container view (C4 L2) | ✔ `C4Container` | ✔ component diagram | ✔ application structure view | ✔ `container` |
| Component view (C4 L3) | ✔ `C4Component` | ✔ component diagram | ✔ application structure view | ✔ `component` |
| Class diagram | ✔ | ✔ | — | — |
| Sequence / runtime flows | ✔ `sequenceDiagram` | ✔ | — | ✔ `dynamic` (connect flow) |
| State machine (connection lifecycle) | ✔ `stateDiagram-v2` | ✔ | — | — |
| Deployment | — | ✔ | ✔ technology layer | — |
| Kubeconfig discovery flow | ✔ `flowchart` | ✔ `activity` | — | — |
| Threading partitioning | ✔ | ✔ | — | — |

## Rendering instructions

| Tool | Command / URL |
|---|---|
| Mermaid (this site) | Automatic — `mkdocs serve` renders every ```mermaid block |
| PlantUML locally | `plantuml -tpng diagram.puml` (the `plantuml` JAR or Homebrew `plantuml`) |
| PlantUML web | paste a block into <https://www.plantuml.com/plantuml> |
| Archi | Archi tool → *File → Import → ArchiMate Model Exchange…* → select `docs/models/jkubeterm.archi` |
| Structurizr CLI | `structurizr export -workspace docs/models/jkubeterm-structurizr.dsl -format mermaid` (or upload the DSL to a Structurizr workspace) |

## Element name registry (shared vocabulary)

| Canonical element | Appears as |
|---|---|
| Kubernetes Developer | Person / BusinessActor |
| JKubeTerm | SoftwareSystem / ApplicationComponent |
| UI Shell (JKubeTermApp) | Container / ApplicationComponent |
| Worker Executor | Container / ApplicationComponent |
| Kubernetes Service | Container / ApplicationComponent |
| Kubeconfig Loader | Container / ApplicationComponent |
| External Tools Bridge | Container / ApplicationComponent |
| Kubernetes Cluster (API Server) | External software system / Technology node |
| kubectl / helm | External systems / SystemSoftware |
| Kubeconfig files | External data object / DataObject |

Keeping this table authoritative avoids drift between notations.

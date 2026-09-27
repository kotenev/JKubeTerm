# Archi / ArchiMate model

JKubeTerm is modelled in **ArchiMate 3** layers, ready to open in the
[Archi tool](https://www.archimatetool.com/). The model exchange file lives at
`docs/models/jkubeterm.archi` (import via *File → Import → ArchiMate Model
Exchange…*). This page documents the model and renders it with PlantUML's
ArchiMate notation via PlantUML's ArchiMate standard library (also embedded in
[plantuml.md](plantuml.md)).

## 1. Layer overview

| ArchiMate layer | Elements |
|---|---|
| **Business** | BusinessActor «Kubernetes Developer» |
| **Application** | ApplicationComponents «JKubeTerm», «UI Shell (JKubeTermApp)», «Worker Executor», «Kubernetes Service», «Kubeconfig Loader», «External Tools Bridge» + six ApplicationServices |
| **Technology** | Node «User Workstation (Linux/macOS)», Node «Kubernetes Cluster (API Server)», SystemSoftware «JDK 21 + JavaFX 21 runtime», «kubectl / helm binaries», «Kubernetes API Server» |
| **Passive structure** | DataObjects «Kubeconfig File(s)», «Resource YAML» |

## 2. Element catalog

| ID | ArchiMate element | Name | Maps to |
|---|---|---|---|
| `el-user` | BusinessActor | Kubernetes Developer | — |
| `el-jkt` | ApplicationComponent | JKubeTerm | the JVM process |
| `el-ui` | ApplicationComponent | UI Shell (JKubeTermApp) | `JKubeTermApp` |
| `el-worker` | ApplicationComponent | Worker Executor | `worker` executor field |
| `el-svc` | ApplicationComponent | Kubernetes Service | `KubernetesService` |
| `el-loader` | ApplicationComponent | Kubeconfig Loader | `KubeconfigLoader` |
| `el-bridge` | ApplicationComponent | External Tools Bridge | `ExternalTools` + port-forward `ProcessBuilder` |
| `el-svc-browse` | ApplicationService | Resource Browsing Service | `list()`, `yaml()`, `namespaces()`, `version()` |
| `el-svc-manifest` | ApplicationService | Manifest Management Service | `apply()`, `delete()`, save/export, New YAML |
| `el-svc-log` | ApplicationService | Log Access Service | `logs()`, `containers()` |
| `el-svc-deploy` | ApplicationService | Deployment Control Service | `scaleDeployment()`, `restartDeployment()` |
| `el-svc-ctx` | ApplicationService | Context Discovery Service | `paths()`, `contexts()`, `config()` |
| `el-svc-cli` | ApplicationService | CLI Integration Service | `ExternalTools.run/kubectl/helm`, port-forward |
| `el-kubeconfig` | DataObject | Kubeconfig File(s) | `~/.kube/config`, `$KUBECONFIG` entries |
| `el-yaml` | DataObject | Resource YAML | editor content / exports |
| `el-ws` | Node | User Workstation (Linux/macOS) | deployment target |
| `el-jdk` | SystemSoftware | JDK 21 + JavaFX 21 runtime | runtime of the app |
| `el-cli` | SystemSoftware | kubectl / helm binaries | on PATH |
| `el-k8s` | Node | Kubernetes Cluster (API Server) | API server :443 |

## 3. Relationship catalog

| ID | Relationship | From → To | Meaning |
|---|---|---|---|
| `rel-user-app` | Assignment | user → JKubeTerm | the actor uses the application |
| `rel-comp-*` (5) | Composition | JKubeTerm → each container component | internal structure |
| `rel-ui-worker` | Triggering | UI → Worker | `task()` submissions |
| `rel-real-svc-*` | Realization | KubernetesService → services | browse/manifest/log/deploy services |
| `rel-real-loader` | Realization | KubeconfigLoader → Context Discovery | discovery |
| `rel-real-bridge` | Realization | ExternalToolsBridge → CLI Integration | subprocess execution |
| `rel-serv-*` (6) | Serving | each service → UI Shell | user-facing behaviour |
| `rel-access-kc` | Access (read) | KubeconfigLoader → Kubeconfig files | read-only discovery |
| `rel-access-kc2` | Access (read) | KubernetesService → Kubeconfig files | `Config.fromKubeconfig` |
| `rel-access-yaml` | Access (read/write) | KubernetesService → Resource YAML | editor apply/export |
| `rel-agg-ws-jdk`, `rel-agg-ws-cli` | Aggregation | Workstation → JDK / CLIs | deployment contents |
| `rel-real-deploy` | Realization | JDK+JavaFX → JKubeTerm | the runtime realizes the app |
| `rel-serv-cli` | Serving | kubectl/helm → ExternalToolsBridge | capability provided to the bridge |
| `rel-flow-api` | Flow «HTTPS :443» | KubernetesService → API Server | cluster traffic |
| `rel-flow-cli` | Flow «HTTPS :443» | kubectl/helm → API Server | child-process traffic |

## 4. Primary view — application structure

```plantuml
@startuml
!include <archimate/Archimate>
title ArchiMate view — JKubeTerm application structure

Business_Actor(user, "Kubernetes Developer", "uses JKubeTerm")
Application_Component(jkt, "JKubeTerm", "single JVM")
Application_Component(ui, "UI Shell (JKubeTermApp)", "")
Application_Component(worker, "Worker Executor", "")
Application_Component(svc, "Kubernetes Service", "")
Application_Component(kl, "Kubeconfig Loader", "")
Application_Component(bridge, "External Tools Bridge", "")
Application_Service(s1, "Resource Browsing Service", "")
Application_Service(s2, "Manifest Management Service", "")
Application_Service(s3, "Log Access Service", "")
Application_Service(s4, "Deployment Control Service", "")
Application_Service(s5, "Context Discovery Service", "")
Application_Service(s6, "CLI Integration Service", "")
Application_DataObject(kc, "Kubeconfig files", "")
Application_DataObject(y, "Resource YAML", "")
Technology_Node(k8s, "Kubernetes Cluster", "API Server :443")

Rel_Assignment(user, jkt, "uses")
Rel_Composition(jkt, ui)
Rel_Composition(jkt, worker)
Rel_Composition(jkt, svc)
Rel_Composition(jkt, kl)
Rel_Composition(jkt, bridge)
Rel_Serving(s1, ui)
Rel_Serving(s2, ui)
Rel_Serving(s3, ui)
Rel_Serving(s4, ui)
Rel_Serving(s5, ui)
Rel_Serving(s6, ui)
Rel_Realization(svc, s1)
Rel_Realization(svc, s2)
Rel_Realization(svc, s3)
Rel_Realization(svc, s4)
Rel_Realization(kl, s5)
Rel_Realization(bridge, s6)
Rel_Triggering(ui, worker, "submits tasks")
Rel_Access(kl, kc, "read")
Rel_Access(svc, kc, "read")
Rel_Access(svc, y, "read/write")
Rel_Flow(svc, k8s, "HTTPS :443")
Rel_Flow(bridge, k8s, "via kubectl / helm")
@enduml
```

## 5. Technology / deployment layer

```plantuml
@startuml
!include <archimate/Archimate>
title ArchiMate view — technology layer

Technology_Node(ws, "User Workstation (Linux/macOS)", "developer desktop")
Technology_SystemSoftware(jdk, "JDK 21 + JavaFX 21 runtime", "")
Technology_SystemSoftware(cli, "kubectl / helm binaries", "on PATH")
Application_Component(jkt, "JKubeTerm", "")
Technology_Node(k8s, "Kubernetes Cluster", "API Server :443")
Application_DataObject(kc, "Kubeconfig File(s)", "")

Rel_Aggregation(ws, jdk, "hosts")
Rel_Aggregation(ws, cli, "hosts")
Rel_Realization(jdk, jkt, "runtime realizes application")
Rel_Serving(cli, jkt, "exec, port-forward, helm list")
Rel_Flow(jkt, k8s, "HTTPS :443")
Rel_Access(jkt, kc, "read")
@enduml
```

## 6. Model exchange file

The complete `.archi` model (elements, relationships and the view above) is
maintained as an ArchiMate model-exchange file:

- **Location:** `docs/models/jkubeterm.archi`
- **Import:** Archi → *File → Import → ArchiMate Model Exchange…*
- **View included:** «JKubeTerm Application Structure» with nested component
  nodes and connections.
- MkDocs excludes `docs/models/` from the published site (see `exclude_docs`
  in `mkdocs.yml`); the file is for the Archi tool and version control.

Keep the element names in the `.archi` file in sync with the
[element registry](index.md#element-name-registry-shared-vocabulary) used by
the PlantUML, Mermaid and Structurizr renderings.

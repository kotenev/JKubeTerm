# Mermaid diagram sources

All Mermaid sources in one place. On this site they render automatically
(Mermaid 11 + `pymdownx.superfences`). Equivalent PlantUML sources:
[plantuml.md](plantuml.md).

## 1. System context (C4 level 1)

```mermaid
C4Context
  title System context — JKubeTerm
  Person(user, "Kubernetes Developer", "Views and manages cluster resources from a desktop.")
  System(jkubeterm, "JKubeTerm", "JavaFX desktop Kubernetes client (Linux/macOS, Java 21).")
  SystemDb(kubeconfig, "Kubeconfig files", "Local kubeconfig file(s) with contexts, clusters, users, TLS material.")
  System_Ext(k8s, "Kubernetes Cluster", "API server over HTTPS :443 with kubeconfig TLS/auth.")
  System_Ext(kubectl, "kubectl", "CLI on PATH — one-shot exec, loopback port-forward.")
  System_Ext(helm, "helm", "CLI on PATH — release listing.")

  Rel(user, jkubeterm, "Views and manages resources with")
  Rel(jkubeterm, kubeconfig, "Reads contexts / builds client config", "Local file I/O")
  Rel(jkubeterm, k8s, "Lists, inspects, applies, deletes resources; reads logs", "HTTPS :443 (Fabric8)")
  Rel(jkubeterm, kubectl, "Exec / port-forward via argv + KUBECONFIG env", "Subprocess")
  Rel(jkubeterm, helm, "helm list --all via argv", "Subprocess")
  Rel(kubectl, k8s, "Talks to API server", "HTTPS")
  Rel(helm, k8s, "Talks to API server", "HTTPS")
```

## 2. Container view (C4 level 2)

```mermaid
C4Container
  title Container view — inside the JKubeTerm JVM
  Person(user, "Kubernetes Developer")
  System_Boundary(jkt, "JKubeTerm (single JVM, Java 21)") {
    Container(ui, "UI Shell", "JavaFX 21 — JKubeTermApp", "Toolbar, kind list, resource table + filter, YAML editor, dialogs, console, status bar.")
    Container(worker, "Worker Executor", "java.util.concurrent", "Single daemon thread 'jkubeterm-kubernetes-io'; every blocking task; results via Platform.runLater.")
    Container(svc, "Kubernetes Service", "Java 21, Fabric8 7.9.0 — KubernetesService", "list 14 kinds, YAML codec, logs, apply (SSA), delete, scale, restart, namespaces, version.")
    Container(loader, "Kubeconfig Loader", "Java 21, SnakeYAML 2.3 — KubeconfigLoader", "path resolution ($KUBECONFIG | ~/.kube/config), context-name discovery, Config.fromKubeconfig.")
    Container(bridge, "External Tools Bridge", "Java 21, ProcessBuilder — ExternalTools", "argv-only kubectl/helm runs, KUBECONFIG injection, 30 s timeouts.")
  }
  SystemDb(kubeconfig, "Kubeconfig files", "Local YAML")
  System_Ext(k8s, "Kubernetes API Server", "HTTPS :443")
  System_Ext(cli, "kubectl / helm", "CLIs on PATH")

  Rel(user, ui, "Uses")
  Rel(ui, worker, "Submits blocking tasks", "task(ThrowingAction)")
  Rel(worker, svc, "Invokes")
  Rel(worker, bridge, "Runs CLI commands")
  Rel(ui, loader, "Discovers contexts (FX thread)")
  Rel(svc, loader, "config(contextRef)")
  Rel(loader, kubeconfig, "Reads")
  Rel(svc, k8s, "Typed DSL calls", "HTTPS :443")
  Rel(bridge, cli, "argv + KUBECONFIG env")
```

## 3. Component view — Kubernetes Service internals (C4 level 3)

```mermaid
C4Component
  title Component view — KubernetesService internals
  Container_Boundary(svcB, "Kubernetes Service (KubernetesService)") {
    Component(catalogue, "Resource Catalogue", "Java switch expression", "Maps ResourceKind → typed Fabric8 list call for 14 kinds.")
    Component(codec, "YAML Codec", "Fabric8 Serialization", "asYaml for the editor; unmarshal for apply.")
    Component(validator, "Apply Validator", "Java", "Requires kind, apiVersion, metadata.name; defaults namespace for namespaced kinds; cluster-scoped allowlist.")
    Component(deployCtl, "Deployment Control", "Fabric8", "scale(replicas ≥ 0); restartDeployment via kubectl.kubernetes.io/restartedAt annotation.")
    Component(logReader, "Log Reader", "Fabric8", "tailingLines(500).getLog(); container enumeration.")
    Component(misc, "Namespace & Version", "Fabric8", "namespaces() sorted; version() = gitVersion (connect health-check).")
  }
  Container(ui, "UI Shell", "JavaFX 21")
  ContainerDb(worker, "Worker Executor", "single thread")
  System_Ext(k8s, "Kubernetes API Server", "HTTPS :443")

  Rel(ui, worker, "tasks")
  Rel(worker, catalogue, "list(kind, ns)")
  Rel(worker, codec, "yaml(resource) / unmarshal")
  Rel(worker, validator, "apply(yaml, ns)")
  Rel(validator, codec, "unmarshal first")
  Rel(worker, deployCtl, "scale / restart")
  Rel(worker, logReader, "logs / containers")
  Rel(worker, misc, "namespaces / version")
  Rel(catalogue, k8s, "HTTPS :443")
  Rel(deployCtl, k8s, "HTTPS :443")
  Rel(logReader, k8s, "HTTPS :443")
  Rel(misc, k8s, "HTTPS :443")
```

## 4. Kubeconfig discovery flow

```mermaid
flowchart TD
  A["paths(env = $KUBECONFIG, home = user.home)"] --> B{"env set and not blank?"}
  B -- "no" --> C["default: ~/.kube/config"]
  B -- "yes" --> D["split on File.pathSeparator"]
  D --> E{"entry"}
  C --> E
  E -- "blank" --> F["skip"]
  E --> G["toAbsolutePath().normalize()"]
  G --> H{"regular file?"}
  H -- "no" --> F
  H -- "yes" --> I["collect path"]
  I --> J["contexts(paths)"]
  J --> K["SnakeYAML load per file"]
  K --> L{"Map with 'contexts' list?"}
  L -- "no" --> M["ignore file"]
  L -- "yes" --> N{"entry Map with non-blank name?"}
  N -- "no" --> M
  N -- "yes" --> O["putIfAbsent(name, ContextRef(file, name))"]
  O --> P["List.copyOf — first-seen wins, file order kept"]
```

## 5. Threading model

```mermaid
flowchart LR
  subgraph FX["JavaFX Application Thread"]
    EV["Event handlers"]
    DLG["Alert / Choice / Input / File dialogs"]
    LOAD["loadContexts — local file I/O (accepted caveat)"]
  end
  subgraph WK["Worker — jkubeterm-kubernetes-io (daemon)"]
    SVC["Fabric8 calls: list, logs, apply, delete, scale, restart, namespaces, version"]
    SUB["ExternalTools.run — kubectl exec / helm list (30 s)"]
    DRAIN["Port-forward output drain"]
  end
  API[("Kubernetes API server")]
  CLI["kubectl / helm child processes"]
  EV -- "task(ThrowingAction)" --> WK
  WK -- "Platform.runLater — results & errors" --> FX
  LOAD --> FS[("kubeconfig files")]
  SVC --> API
  SUB --> CLI
  DRAIN --> CLI
```

## 6. Sequence — connect

```mermaid
sequenceDiagram
  autonumber
  participant U as User
  participant FX as FX Thread
  participant W as Worker
  participant KS as KubernetesService
  participant API as K8s API
  U->>FX: click "Connect"
  FX->>W: task(connect)
  W->>KS: new KubernetesService(ref) — Config.fromKubeconfig, TLS on
  W->>KS: version()
  KS->>API: GET /version
  API-->>KS: gitVersion
  W->>KS: namespaces()
  KS->>API: GET /api/v1/namespaces
  API-->>KS: sorted names
  W--)FX: runLater — close old, swap service, fill ns combo, status, refresh()
```

## 7. Sequence — apply YAML (server-side apply)

```mermaid
sequenceDiagram
  autonumber
  participant U as User
  participant FX as FX Thread
  participant W as Worker
  participant KS as KubernetesService
  participant API as K8s API
  U->>FX: click "Apply YAML"
  FX->>U: confirm "Create or replace … in context X?"
  U-->>FX: OK
  FX->>W: task(apply)
  W->>KS: apply(yaml, ns)
  KS->>KS: unmarshal → require kind, apiVersion, metadata.name
  KS->>KS: namespaced & no ns → namespaceOrDefault
  KS->>API: serverSideApply()
  API-->>KS: applied
  W--)FX: console "Applied YAML successfully." — then refresh()
```

## 8. Sequence — port-forward lifecycle

```mermaid
sequenceDiagram
  autonumber
  participant U as User
  participant FX as FX Thread
  participant W as Worker
  participant K as kubectl port-forward
  U->>FX: Pod/Service + "Port forward" + "8080:80"
  FX->>FX: validate regex + port ranges (1–65535)
  FX->>K: ProcessBuilder — --address 127.0.0.1, env KUBECONFIG
  FX->>FX: portProcesses.add(process) — console "started (PID n)"
  FX->>W: drain merged output
  W--)FX: console = output when process ends
  Note over FX,K: stop() destroys every alive port-forward process
```

## 9. State connection lifecycle

```mermaid
stateDiagram-v2
  [*] --> Discovering: start() / "↻ Config"
  Discovering --> ContextsReady: contexts parsed
  ContextsReady --> Connecting: Connect
  Connecting --> Connected: version + namespaces OK (old client closed)
  Connecting --> ContextsReady: failure (new client closed)
  Connected --> Refreshing: kind/namespace change / Refresh
  Refreshing --> Connected: rows cached + filtered
  Connected --> Connecting: switch context
  ContextsReady --> Closed: stop()
  Connected --> Closed: stop()
  Closed --> [*]
```

## 10. Class diagram

```mermaid
classDiagram
  direction LR
  class JKubeTermApp {
    -ExecutorService worker
    -KubernetesService service
    -List~HasMetadata~ currentItems
    -List~Process~ portProcesses
    +start(Stage)
    -loadContexts()
    -connect()
    -refresh()
    -applyYaml()
    -deleteSelected()
    -logs()
    -exec()
    -portForward()
    -scale()
    -restart()
    -helm()
    -task(ThrowingAction)
    +stop()
  }
  class KubernetesService {
    -KubernetesClient client
    -ContextRef context
    +list(ResourceKind, String) List
    +yaml(HasMetadata) String
    +apply(String, String)
    +delete(HasMetadata)
    +logs(String, String, String, int) String
    +containers(String, String) List
    +scaleDeployment(String, String, int)
    +restartDeployment(String, String)
    +namespaces() List
    +version() String
    +close()
  }
  class KubeconfigLoader {
    <<utility>>
    +paths(String, String) List~Path~
    +contexts(List~Path~) List~ContextRef~
    +config(ContextRef) Config
  }
  class ExternalTools {
    <<utility>>
    +run(ContextRef, List~String~, int) String
    +kubectl(ContextRef, String, String...) List~String~
    +helm(ContextRef, String, String...) List~String~
  }
  class ContextRef {
    <<record>>
    +Path file
    +String name
  }
  class ResourceKind {
    <<enumeration>>
    +String label
    +boolean namespaced
  }
  JKubeTermApp *-- KubernetesService : one live
  JKubeTermApp *-- ResourceKind
  JKubeTermApp o-- ContextRef
  JKubeTermApp ..> ExternalTools
  JKubeTermApp ..> KubeconfigLoader
  KubernetesService --> KubeconfigLoader : config()
  KubernetesService *-- ContextRef
  KubeconfigLoader *-- ContextRef
  ExternalTools ..> ContextRef
```

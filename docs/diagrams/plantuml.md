# PlantUML diagram sources

Complete PlantUML source set. Every diagram below renders as an inline SVG
preview via the local PlantUML server in Docker (`docker compose up -d`
→ `http://localhost:8080`; images embedded as base64, offline-readable).
The original sources remain editable as text after each preview. Equivalent
Mermaid sources: [mermaid.md](mermaid.md); ArchiMate modelling:
[archi.md](archi.md).

## 1. System context (C4 level 1)

```plantuml
@startuml
title JKubeTerm — System Context (C4 L1)
left to right direction
skinparam rectangle {
  BackgroundColor #1168bd
  FontColor #ffffff
  BorderColor #0b4480
}
actor "Kubernetes Developer" as user
rectangle "JKubeTerm\nJavaFX desktop Kubernetes client\n(Linux/macOS, Java 21)" as jkt
rectangle "Kubernetes Cluster\nAPI server, HTTPS :443" as k8s
rectangle "kubectl\nCLI on PATH (exec, port-forward)" as kubectl
rectangle "helm\nCLI on PATH (list)" as helm
database "Kubeconfig files\n~/.kube/config, $KUBECONFIG" as kc
user --> jkt : views and manages resources
jkt --> kc : reads context names / builds client config (local I/O)
jkt --> k8s : list / inspect / apply / delete / logs\nHTTPS, kubeconfig TLS + client certs
jkt --> kubectl : argv subprocess + KUBECONFIG env
jkt --> helm : argv subprocess + KUBECONFIG env
kubectl --> k8s : HTTPS :443
helm --> k8s : HTTPS :443
@enduml
```

## 2. Container / component view (C4 level 2)

```plantuml
@startuml
title JKubeTerm — Container view (C4 L2)
skinparam componentStyle rectangle
actor "Kubernetes Developer" as user
package "JKubeTerm — single JVM (Java 21)" {
  component "UI Shell — JKubeTermApp\n(JavaFX 21)" as ui
  component "Worker Executor\nnewSingleThreadExecutor\ndaemon: jkubeterm-kubernetes-io" as worker
  component "Kubernetes Service — KubernetesService\nFabric8 7.9.0 (AutoCloseable)" as service
  component "Kubeconfig Loader — KubeconfigLoader\nSnakeYAML 2.3 + Fabric8 Config" as loader
  component "External Tools Bridge — ExternalTools\nProcessBuilder, argv-only" as bridge
}
cloud "Kubernetes Cluster" {
  component "API Server\nHTTPS :443" as api
}
component "kubectl\n(PATH)" as kubectl
component "helm\n(PATH)" as helm
database "Kubeconfig files" as kc

user --> ui
ui --> worker : task(ThrowingAction)
worker --> service
worker --> bridge : run(argv, timeout)
ui --> loader : loadContexts() on FX thread
service --> loader : config(contextRef)
loader --> kc : read-only
service --> api : typed DSL calls
ui --> api : (indirect, via worker)
bridge --> kubectl
bridge --> helm
kubectl --> api
helm --> api
note bottom of ui
  port-forward: ProcessBuilder started here
  (long-lived, loopback --address 127.0.0.1),
  processes tracked in portProcesses and
  destroyed in stop()
end note
@enduml
```

## 3. Kubernetes Service internals (C4 level 3)

```plantuml
@startuml
title KubernetesService — internal components
skinparam componentStyle rectangle
component "Resource Catalogue\nswitch over ResourceKind\n→ 14 typed Fabric8 list calls" as cat
component "YAML Codec\nSerialization.asYaml / unmarshal" as codec
component "Apply Validator\nkind + apiVersion + metadata.name,\ncluster-scoped namespace rules" as validator
component "Log Reader\ntailingLines(500).getLog()" as logs
component "Deployment Control\nscale(replicas ≥ 0),\nrestart via restartedAt annotation" as deploy
component "Namespace & Version\nnamespaces() sorted, version()" as misc
component "KubernetesClient\n(Fabric8, TLS on)" as client

validator --> codec : unmarshal(yaml)
validator --> client : serverSideApply()
logs --> client
deploy --> client
misc --> client
@enduml
```

## 4. Class diagram

```plantuml
@startuml
title JKubeTerm — class diagram
skinparam classAttributeIconSize 0
hide empty members
package dev.jkubeterm {
  class JKubeTermApp extends javafx.application.Application {
    - worker : ExecutorService
    - service : KubernetesService
    - stage : Stage
    - currentItems : List<HasMetadata>
    - portProcesses : List<Process>
    + start(Stage primaryStage)
    + stop()
    - loadContexts()
    - connect()
    - refresh()
    - showItems()
    - applyYaml()
    - deleteSelected()
    - logs()
    - exec()
    - portForward()
    - scale()
    - restart()
    - helm()
    - saveYaml()
    - output(String message)
    - info(String message)
    - confirm(String title, String body) : boolean
    - error(String title, Throwable ex)
    - task(ThrowingAction action)
  }
  interface ThrowingAction <<FunctionalInterface>> {
    + run() throws Exception
  }
  class KubernetesService implements AutoCloseable {
    - client : KubernetesClient
    - context : ContextRef
    + context() : ContextRef
    + namespaceOrDefault(String namespace) : String
    + namespaces() : List<String>
    + version() : String
    + list(ResourceKind kind, String namespace) : List<? extends HasMetadata>
    + yaml(HasMetadata resource) : String
    + logs(String namespace, String pod, String container, int tail) : String
    + containers(String namespace, String pod) : List<String>
    + apply(String yaml, String namespace)
    + delete(HasMetadata resource)
    + scaleDeployment(String namespace, String name, int replicas)
    + restartDeployment(String namespace, String name)
    + close()
    - {static} isClusterScoped(String kind) : boolean
  }
  class KubeconfigLoader <<utility>> {
    + {static} paths(String env, String home) : List<Path>
    + {static} contexts(List<Path> paths) : List<ContextRef>
    + {static} config(ContextRef context) : Config
  }
  class ContextRef <<record>> {
    + file : Path
    + name : String
  }
  class ExternalTools <<utility>> {
    + {static} run(ContextRef context, List<String> command, int timeoutSeconds) : String
    + {static} kubectl(ContextRef context, String namespace, String... args) : List<String>
    + {static} helm(ContextRef context, String namespace, String... args) : List<String>
  }
  enum ResourceKind {
    PODS
    DEPLOYMENTS
    STATEFUL_SETS
    DAEMON_SETS
    SERVICES
    CONFIG_MAPS
    JOBS
    CRON_JOBS
    INGRESSES
    PERSISTENT_VOLUME_CLAIMS
    EVENTS
    NODES
    NAMESPACES
    PERSISTENT_VOLUMES
    + label : String
    + namespaced : boolean
  }
}
JKubeTermApp o-- KubernetesService : one live instance
JKubeTermApp o-- ContextRef
JKubeTermApp *-- ResourceKind
JKubeTermApp ..> ThrowingAction : defines
JKubeTermApp ..> KubeconfigLoader
JKubeTermApp ..> ExternalTools
KubernetesService --> KubeconfigLoader : config()
KubernetesService o-- ContextRef
KubernetesService --> io.fabric8.kubernetes.client.KubernetesClient
KubeconfigLoader *-- ContextRef
KubeconfigLoader --> io.fabric8.kubernetes.client.Config
ExternalTools ..> ContextRef
@enduml
```

## 5. Sequence diagrams

### 5.1 Connect to a context

```plantuml
@startuml
title Connect — build client, health-check, swap
autonumber
actor User
participant "FX Thread\nJKubeTermApp" as fx
participant "Worker\njkubeterm-kubernetes-io" as w
participant "KubernetesService" as svc
participant "KubeconfigLoader" as kl
participant "K8s API Server" as api

User -> fx : click "Connect"
fx -> fx : no context? info dialog
fx -> w : task(connect)
w -> kl : config(contextRef)
kl -> api : (none — local file)
kl --> w : Config (TLS on, from kubeconfig)
w -> svc : new KubernetesService(ref)
svc -> svc : KubernetesClientBuilder().withConfig(config).build()
w -> svc : version()
svc -> api : GET /version
api --> svc : gitVersion
w -> svc : namespaces()
svc -> api : GET /api/v1/namespaces
api --> svc : sorted names
w -> fx : Platform.runLater {\n  old.close(); service = next;\n  fill ns combo (prefer "default");\n  status "Connected: X | Kubernetes Y";\n  refresh();\n}
note over w : on failure: next.close(); rethrow → error dialog
@enduml
```

### 5.2 Browse / refresh

```plantuml
@startuml
title Refresh — list one kind in one namespace
autonumber
participant "FX Thread" as fx
participant "Worker" as w
participant "KubernetesService" as svc
participant "K8s API Server" as api
fx -> fx : guard: service live && kind selected
fx -> w : task(refresh) [kind, ns captured]
w -> svc : list(kind, nsOrDefault(ns))
svc -> api : typed list call (14 kinds)
api --> svc : List<HasMetadata>
w -> w : List.copyOf(result)
w -> fx : runLater { currentItems = result; showItems();\n  status "N <kind> | <context>" }
fx -> fx : local filter on metadata.name (case-insensitive contains)
@enduml
```

### 5.3 Apply YAML

```plantuml
@startuml
title Apply YAML — confirm, validate, server-side apply
autonumber
actor User
participant "FX Thread" as fx
participant "Worker" as w
participant "KubernetesService" as svc
participant "K8s API Server" as api

User -> fx : click "Apply YAML"
fx -> User : confirm "Create or replace the resource\nin context X? Review manifest and namespace."
User --> fx : OK
fx -> w : task(apply)
w -> svc : apply(yaml, ns)
svc -> svc : Serialization.unmarshal(yaml, HasMetadata)
alt invalid document
  svc --> w : IllegalArgumentException\n(kind / apiVersion / metadata.name missing)
  w -> fx : error dialog
else valid
  svc -> svc : namespaced && namespace empty → set default/selected ns
  svc -> api : client.resource(obj).serverSideApply()
  api --> svc : applied object
  w -> fx : runLater { console "Applied YAML successfully."; refresh(); }
end
@enduml
```

### 5.4 Pod logs (container choice)

```plantuml
@startuml
title Pod logs — two-phase with container choice
autonumber
actor User
participant "FX Thread" as fx
participant "Worker" as w
participant "KubernetesService" as svc

User -> fx : select Pod, click "Pod logs"
fx -> w : task(containers)
w -> svc : containers(ns, pod)
svc --> w : container names (List.of() if pod/spec absent)
w -> fx : runLater { names }
fx -> User : ChoiceDialog (default = first)
User --> fx : container
fx -> w : task(logs)
w -> svc : logs(ns, pod, container, 500)\n→ tailingLines(500).getLog()
svc --> w : log text
w -> fx : runLater { console = log }
@enduml
```

### 5.5 Port-forward lifecycle

```plantuml
@startuml
title Port-forward — validated, loopbound, app-owned process
autonumber
actor User
participant "FX Thread" as fx
participant "Worker" as w
participant "kubectl port-forward\nchild process" as pf

User -> fx : select Pod/Service, "Port forward"
fx -> User : dialog "local:remote" (default 8080:80)
User --> fx : e.g. 8080:80
fx -> fx : regex \\d{1,5}:\\d{1,5}; ports 1–65535
fx -> pf : ProcessBuilder(argv).redirectErrorStream(true)\nenv KUBECONFIG = context file\n--address 127.0.0.1
fx -> fx : portProcesses.add(process)\nconsole "Port-forward started … (PID n)"
fx -> w : worker.submit(read merged output)
w -> pf : inputStream.readAllBytes() [blocks until process ends]
w -> fx : runLater { console = buffered output }
note over fx,pf : stop(): destroy() every alive process
@enduml
```

## 6. State machine — connection lifecycle

```plantuml
@startuml
title Connection lifecycle
[*] --> Discovering : start() / "↻ Config"
Discovering --> ContextsReady : contexts parsed → combo filled
Discovering --> ContextsReady : error dialog (kubeconfig unreadable)
ContextsReady --> Connecting : Connect
Connecting --> Connected : version() + namespaces() OK\n(old client closed)
Connecting --> ContextsReady : failure (new client closed, error dialog)
Connected --> Refreshing : kind/namespace change, Refresh
Refreshing --> Connected : rows cached + filtered
Connected --> Connecting : another context selected
ContextsReady --> Closed : stop()
Connected --> Closed : stop()
Closed --> [*]
@enduml
```

## 7. Deployment diagram

```plantuml
@startuml
title Deployment — workstation to cluster
node "User workstation (Linux/macOS)" as ws {
  node "JDK 21 runtime" as jre {
    artifact "jkubeterm-0.1.0.jar\n(main-class dev.jkubeterm.JKubeTermApp)" as jar
    artifact "JavaFX 21.0.6 modules\n(javafx-controls)" as fx
    artifact "kubernetes-client 7.9.0\nsnakeyaml 2.3" as libs
  }
  folder "kubeconfig file(s)\n~/.kube/config and/or $KUBECONFIG" as kc
  component "kubectl\n(PATH)" as kctl
  component "helm\n(PATH)" as hm
}
node "Kubernetes control plane" as cp {
  component "API Server\nHTTPS :443" as api
}
jar --> jre
fx --> jre
libs --> jre
kc ..> jre : read at startup / connect
jar --> api : HTTPS (kubeconfig TLS + client certs)
kctl --> api : exec stream / port-forward
hm --> api : release list
@enduml
```

## 8. Activity — kubeconfig discovery

```plantuml
@startuml
title Kubeconfig discovery (paths + contexts)
start
:env = $KUBECONFIG;
if (env set and not blank?) then (yes)
  :split on File.pathSeparator;
else (no)
  :path = ~/.kube/config;
endif
while (more entries?) is (yes)
  :trim; toAbsolutePath().normalize();
  if (regular file?) then (yes)
    :add to paths;
  else (no)
    :skip;
  endif
endwhile (no)
:for each file → SnakeYAML load;
while (more files?) is (yes)
  if (Map with "contexts" list?) then (yes)
    while (more entries?) is (yes)
      if (non-blank "name"?) then (yes)
        :putIfAbsent(name, ContextRef(file, name));
      else (no)
      endif
    endwhile (no)
  else (no)
    :ignore;
  endif
endwhile (no)
:List.copyOf(found.values());
stop
@enduml
```

## 9. Threading — FX thread vs. worker

```plantuml
@startuml
title Threading — task() bridges FX → worker → FX
autonumber
participant "FX Application Thread" as fx
participant "Worker\njkubeterm-kubernetes-io (daemon)" as w

fx -> fx : event handler (instant)
fx -> w : worker.submit(task)
activate w
w -> w : blocking I/O allowed here\n(Fabric8 / ProcessBuilder)
w -->> fx : Platform.runLater(result)
fx -> fx : model + UI update
deactivate w
note over fx : loadContexts() runs local file I/O\non FX thread (accepted caveat)
note over w : exceptions → runLater(error dialog\n"Kubernetes operation failed")
@enduml
```

## 10. ArchiMate rendering (application structure)

Rendered with PlantUML's ArchiMate standard library (modern PlantUML moved
ArchiMate into the stdlib) — the same model that is exchangeable with the
Archi tool, see [archi.md](archi.md). Note: this block also gets a preview
from the local server; `!include <archimate/Archimate>` is resolved by the
server-side stdlib, so no local include path is needed.

```plantuml
@startuml
!include <archimate/Archimate>
title ArchiMate — JKubeTerm application structure

Business_Actor(user, "Kubernetes Developer", "uses JKubeTerm on a workstation")
Application_Component(jkt, "JKubeTerm", "desktop JavaFX client")
Application_Component(ui, "UI Shell (JKubeTermApp)", "scene, dialogs, event wiring")
Application_Component(worker, "Worker Executor", "single daemon thread")
Application_Component(svc, "Kubernetes Service", "Fabric8 wrapper")
Application_Component(kl, "Kubeconfig Loader", "SnakeYAML + Fabric8 Config")
Application_Component(bridge, "External Tools Bridge", "argv-only subprocesses")
Application_Service(s1, "Resource Browsing Service", "")
Application_Service(s2, "Manifest Management Service", "")
Application_Service(s3, "Log / Exec / CLI Integration", "")
Application_Service(s4, "Deployment Control Service", "")
Application_Service(s5, "Context Discovery Service", "")
Application_DataObject(kc, "Kubeconfig files", "local YAML")
Application_DataObject(y, "Resource YAML", "editor / export")
Technology_Node(k8s, "Kubernetes Cluster", "API Server HTTPS :443")

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
Rel_Realization(svc, s1)
Rel_Realization(svc, s2)
Rel_Realization(svc, s3)
Rel_Realization(svc, s4)
Rel_Realization(kl, s5)
Rel_Triggering(ui, worker, "task(ThrowingAction)")
Rel_Access(kl, kc, "read (discovery)")
Rel_Access(svc, kc, "read (Config.fromKubeconfig)")
Rel_Access(svc, y, "read/write (editor, apply, export)")
Rel_Flow(svc, k8s, "HTTPS :443 (kubeconfig TLS)")
Rel_Flow(bridge, k8s, "via kubectl / helm")
@enduml
```

## 11. UX state machine — connection lifecycle plus edit mode and action flows

Source file: `ux-state-machine.puml` in this directory (validated with
`plantuml -checkonly`). State names match the Mermaid connection lifecycle in
[data-flow.md](../architecture/data-flow.md#0-connection-lifecycle-state-machine)
and [mermaid.md](mermaid.md#9-state-connection-lifecycle).

```plantuml
@startuml
title JKubeTerm — UX state machine (connection lifecycle + edit mode + action flows)

[*] --> Discovering : start() / "Config" button
Discovering --> ContextsReady : contexts parsed\ncombo filled
Discovering --> ContextsReady : kubeconfig unreadable\nerror dialog
ContextsReady --> Connecting : Connect
Connecting --> Connected : version() + namespaces() OK\nold client closed
Connecting --> ContextsReady : failure\nnew client closed\nerror dialog
Connected --> Connecting : another Connect
ContextsReady --> Closed : stop()
Connected --> Closed : stop()\nkill PF / shutdownNow / close()
Closed --> [*]

state Connected {
  [*] --> Browsing
  Browsing --> Refreshing : kind or namespace change\nRefresh click
  Refreshing --> Browsing : list() OK\nrows cached + filtered
  Browsing --> EditOn : tick Edit YAML\nNew YAML seeds ConfigMap template
  EditOn --> Browsing : row selected\neditMode off + editable false
  Browsing --> ConfirmDialog : Apply YAML\nDelete\nScale OK\nRestart
  ConfirmDialog --> Browsing : Cancel\nno side effect
  ConfirmDialog --> Applying : OK Apply
  ConfirmDialog --> Deleting : OK Delete
  ConfirmDialog --> Scaling : OK Scale
  ConfirmDialog --> Restarting : OK Restart
  Applying --> Refreshing : applied\nconsole + refresh()
  Deleting --> Refreshing : delete submitted\nconsole + refresh()
  Scaling --> Refreshing : scaleDeployment()\nrefresh()
  Restarting --> Refreshing : restartDeployment()\nrefresh()
  Browsing --> LogsDialog : Pod logs\ncontainers() on worker
  LogsDialog --> Logging : container chosen
  LogsDialog --> Browsing : Cancel or empty
  Logging --> Browsing : logs 500 lines\nconsole = log
  Browsing --> ExecDialog : Exec command\nPod selected
  ExecDialog --> ExecRunning : executable entered\nkubectl exec 30s
  ExecDialog --> Browsing : Cancel or blank
  ExecRunning --> Browsing : console = output
  Browsing --> PortForwardDialog : Port forward\nPod or Service selected
  PortForwardDialog --> PortForwardRunning : valid local:remote\nProcessBuilder.start() on FX thread
  PortForwardDialog --> Browsing : Cancel
  PortForwardDialog --> InfoDialog : regex or range fail
  PortForwardRunning --> Browsing : output shown on process end\nprocess tracked until stop()
  Browsing --> ScaleDialog : Scale\nDeployment selected
  ScaleDialog --> ConfirmDialog : n >= 0\nconfirm scale to n
  ScaleDialog --> Browsing : Cancel
  ScaleDialog --> InfoDialog : negative or non-integer
  Browsing --> HelmRunning : Helm releases\nhelm list 30s
  HelmRunning --> Browsing : console = release table
  Browsing --> SaveDialog : Save YAML\nFileChooser resource.yaml
  SaveDialog --> Browsing : saved or Cancel\nlocal only, no worker
  Browsing --> InfoDialog : non-Pod for logs or exec\nnon-Deployment for scale or restart\nnon-Pod-Service for port-forward
  InfoDialog --> Browsing : OK
}

note bottom of Connected
  Threading: Fabric8 and CLI blocking work via
  task() on jkubeterm-kubernetes-io worker.
  UI updates only via Platform.runLater.
  loadContexts(), service.yaml() and
  ProcessBuilder.start() run on FX thread.
end note

note bottom of PortForwardRunning
  portProcesses tracks live processes.
  stop() destroys alive processes before
  worker.shutdownNow() + service.close().
end note

note bottom of ConfirmDialog
  Mutating actions always go
  Connected -> ConfirmDialog -> action
  -> refresh() -> Connected.
end note

note bottom of LogsDialog
  Two-phase worker to FX to worker:
  containers() -> ChoiceDialog
  -> logs 500 lines.
end note
@enduml
```

# Data flow & sequences

Every user action follows the same pattern: **UI event (FX thread) → optional
confirmation dialog → `task()` on the worker → service/CLI call →
`Platform.runLater` back to the UI**. Below are all flows in order.

Sequence diagrams here use Mermaid; the same flows also exist as PlantUML
sources in [diagrams/plantuml.md](../diagrams/plantuml.md#5-sequence-diagrams).

## 0. Connection lifecycle (state machine)

```mermaid
stateDiagram-v2
    [*] --> Discovering: JVM start / start(Stage)
    Discovering --> ContextsReady: kubeconfig files parsed
    Discovering --> ContextsReady: "↻ Config" (re-discover)
    ContextsReady --> Connecting: Connect clicked (context selected)
    Connecting --> Connected: version() + namespaces() OK
    Connecting --> ContextsReady: failure — new client closed, error dialog
    ContextsReady --> Closed: stop()
    Connected --> Refreshing: kind/namespace change or Refresh
    Refreshing --> Connected: list() result → table + filter
    Connected --> Connecting: another Connect (old client closed on success)
    Connected --> Closed: stop() — PF processes destroyed, worker shut down, client closed
    Closed --> [*]
```

## 1. Startup and context discovery

Runs synchronously at the end of `start()` (`JKubeTermApp.java:86`) and again
on **↻ Config**. File I/O happens on the FX thread (accepted cost, see
[threading caveats](threading.md#caveats)).

```mermaid
sequenceDiagram
  autonumber
  participant U as User
  participant FX as FX Thread (JKubeTermApp)
  participant KL as KubeconfigLoader
  participant FS as Filesystem
  U->>FX: launch app / click "↻ Config"
  FX->>KL: paths($KUBECONFIG, user.home)
  KL->>FS: probe each path
  FS-->>KL: existing regular files only
  FX->>KL: contexts(paths)
  KL->>FS: SnakeYAML load each file
  KL-->>FX: List of ContextRef (first-seen per name)
  FX->>FX: combo.setItems, selectFirst, status "N contexts from M file(s)"
```

Error path: any exception → `error("Cannot read kubeconfig", ex)` (status bar +
error dialog).

## 2. Connect to a context

```mermaid
sequenceDiagram
  autonumber
  participant U as User
  participant FX as FX Thread
  participant W as Worker (jkubeterm-kubernetes-io)
  participant KS as KubernetesService
  participant API as K8s API Server
  U->>FX: click "Connect"
  FX->>FX: no context selected? → info dialog, stop
  FX->>W: task(connect)
  W->>KS: new KubernetesService(contextRef)
  KS->>KS: KubeconfigLoader.config → Fabric8 Config (TLS on)
  W->>KS: version()
  KS->>API: GET /version
  API-->>KS: gitVersion
  W->>KS: namespaces()
  KS->>API: GET /api/v1/namespaces
  API-->>KS: sorted namespace names
  W--)FX: Platform.runLater — swap service, close old, fill ns combo, refresh()
  Note over W,API: on any failure: next.close() then rethrow → error dialog
```

Detail: the swap keeps at most one live client ([overview](overview.md#7-runtime-topology)).

## 3. Browse resources (refresh + local filter)

Triggered by kind selection, namespace change or **Refresh**.

```mermaid
sequenceDiagram
  autonumber
  participant FX as FX Thread
  participant W as Worker
  participant KS as KubernetesService
  participant API as K8s API Server
  FX->>W: task(refresh) — kind + namespace captured
  W->>KS: list(kind, ns) — namespaceOrDefault(ns)
  KS->>API: typed list call for kind (14 kinds, see catalog)
  API-->>KS: List of HasMetadata
  W->>W: List.copyOf(result)
  W--)FX: currentItems = result, showItems(), status "N rows — kind, context"
  Note over FX: local filter — name.toLowerCase().contains(query)
```

## 4. Inspect and edit YAML

Row selection is synchronous on the FX thread against the already-listed
object — no network call:

```mermaid
sequenceDiagram
  autonumber
  participant U as User
  participant FX as FX Thread
  participant KS as KubernetesService
  U->>FX: select row
  FX->>KS: yaml(item) → Serialization.asYaml (in-process, no I/O)
  KS-->>FX: YAML text
  FX->>FX: details.setText, editMode=off, editable=false
  U->>FX: tick "Edit YAML" → details editable
  Note over FX: "New YAML" seeds a ConfigMap template and enables edit mode
```

## 5. Apply YAML (server-side apply)

```mermaid
sequenceDiagram
  autonumber
  participant U as User
  participant FX as FX Thread
  participant W as Worker
  participant KS as KubernetesService
  participant API as K8s API Server
  U->>FX: click "Apply YAML"
  FX->>U: confirm "Create or replace the resource in context X? …"
  U-->>FX: OK
  FX->>W: task(apply)
  W->>KS: apply(yaml, ns)
  KS->>KS: Serialization.unmarshal(yaml, HasMetadata)
  KS->>KS: require kind, apiVersion, metadata.name (else IllegalArgumentException)
  KS->>KS: namespaced & namespace empty → set namespaceOrDefault(ns)
  KS->>API: serverSideApply()
  API-->>KS: applied object
  W--)FX: console "Applied YAML successfully." — then refresh()
```

Failure of any step surfaces the exception message in the error dialog; the
manifest in the editor is untouched.

## 6. Delete resource

```mermaid
sequenceDiagram
  autonumber
  participant U as User
  participant FX as FX Thread
  participant W as Worker
  participant KS as KubernetesService
  U->>FX: select row, click "Delete"
  FX->>U: confirm "Delete KIND / name from context X?"
  U-->>FX: OK
  FX->>W: task(delete)
  W->>KS: delete(item) → client.resource(item).delete()
  W--)FX: console "Delete request submitted." — then refresh()
```

## 7. Pod logs (two-phase, container choice)

```mermaid
sequenceDiagram
  autonumber
  participant U as User
  participant FX as FX Thread
  participant W as Worker
  participant KS as KubernetesService
  U->>FX: select Pod, click "Pod logs"
  FX->>W: task(containers)
  W->>KS: containers(ns, pod)
  W--)FX: container names
  FX->>U: ChoiceDialog (default = first container)
  U-->>FX: pick container
  FX->>W: task(logs)
  W->>KS: logs(ns, pod, container, 500) — tailingLines(500).getLog()
  W--)FX: console = log text
  Note over FX: non-Pod selection → info "Select a Pod to read logs."
```

## 8. Exec command via kubectl (one-shot)

```mermaid
sequenceDiagram
  autonumber
  participant U as User
  participant FX as FX Thread
  participant W as Worker
  participant ET as ExternalTools
  participant K as kubectl child process
  participant API as K8s API Server
  U->>FX: select Pod, click "Exec command"
  FX->>U: TextInputDialog default "/bin/sh" (single executable, no shell parsing)
  U-->>FX: executable path
  FX->>ET: kubectl(ctx, ns, "exec", pod, "--", cmd) → argv
  FX->>W: task(run argv, 30 s)
  W->>ET: run(context, argv, 30)
  ET->>K: ProcessBuilder(argv), env KUBECONFIG = kubeconfig file, merged stderr
  K->>API: HTTPS exec stream
  ET-->>W: stdout+stderr (UTF-8) or IOException (timeout / non-zero)
  W--)FX: console = output
```

## 9. Port-forward (kubectl subprocess, loopback)

The only long-lived child process; owned by `JKubeTermApp`, not
`ExternalTools`:

```mermaid
sequenceDiagram
  autonumber
  participant U as User
  participant FX as FX Thread
  participant W as Worker
  participant K as kubectl port-forward process
  U->>FX: select Pod/Service, click "Port forward"
  FX->>U: TextInputDialog "8080:80"
  U-->>FX: local:remote
  FX->>FX: validate ports regex and ranges 1–65535
  FX->>K: ProcessBuilder(kubectl … port-forward --address 127.0.0.1 kind/name local:remote), env KUBECONFIG
  FX->>FX: portProcesses.add(process) — console "Port-forward started (PID n). Stops when JKubeTerm exits."
  FX->>W: worker.submit(read merged output)
  W--)FX: on process end — console = buffered kubectl output
  Note over U,K: app exit → stop() → process.destroy() for every alive PF process
```

See [external tools](external-tools.md#3-port-forward-special-case) for why
this flow bypasses `ExternalTools.run`.

## 10. Scale / restart a Deployment

```mermaid
sequenceDiagram
  autonumber
  participant U as User
  participant FX as FX Thread
  participant W as Worker
  participant KS as KubernetesService
  U->>FX: select Deployment, click "Scale" / "Restart"
  alt Scale
    FX->>U: replica count dialog (default "1") — negative rejected with info
    U-->>FX: n
    FX->>U: confirm "Scale NAME to n replicas?"
    FX->>W: scaleDeployment(ns, name, n) → apps().deployments()…scale(n)
  else Restart
    FX->>U: confirm "Roll out a restart of NAME?"
    FX->>W: restartDeployment(ns, name) → template annotation kubectl.kubernetes.io/restartedAt = now
  end
  W--)FX: refresh()
```

## 11. Helm releases

```mermaid
sequenceDiagram
  autonumber
  participant FX as FX Thread
  participant W as Worker
  participant ET as ExternalTools
  participant H as helm child process
  FX->>ET: helm(ctx, ns|default, "list", "--all") → argv
  FX->>W: task(run argv, 30 s)
  W->>ET: run(context, argv, 30)
  ET->>H: ProcessBuilder(argv), env KUBECONFIG = kubeconfig file
  ET-->>W: stdout or IOException
  W--)FX: console = release table
```

## 12. Export / save YAML

Synchronous, local-only: `FileChooser` (initial name `resource.yaml`, filter
`*.yaml`/`*.yml`) → `Files.writeString`. Errors → `error("Save failed", ex)`.
No cluster interaction.

## 13. Shutdown (`stop()`)

```mermaid
sequenceDiagram
  autonumber
  participant FX as FX Thread (stop)
  participant K as port-forward processes
  participant W as Worker
  participant KS as KubernetesService
  FX->>K: for each process: if alive → destroy()
  FX->>W: shutdownNow() (interrupts queued/running tasks)
  FX->>KS: close() → KubernetesClient.close()
  Note over FX: daemon worker thread cannot block JVM exit
```

## Error-handling conventions

| Layer | Mechanism |
|---|---|
| Worker tasks | `task()` wraps `ThrowingAction`; exceptions → `Platform.runLater` → error dialog `«Kubernetes operation failed»` |
| Context discovery | Local try/catch → `«Cannot read kubeconfig: …»` |
| Connect | Failure closes the freshly built client before rethrowing (`JKubeTermApp.java:119`) |
| Subprocesses | Non-zero exit or timeout → `IOException` carrying the merged output |
| Status bar | Mirrors every transition (loading/connected/error summaries) |

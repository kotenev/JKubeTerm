# Architecture decision records

Status legend: **Accepted** (current behaviour), **Superseded** (replaced by a
later ADR). ADRs are numbered in the order the decisions became binding.

---

## ADR-0001 — JavaFX desktop application, no backend service

- **Status:** Accepted
- **Context:** The tool handles cluster credentials and must stay lightweight;
  a server component would centralise credentials and add attack surface.
- **Decision:** Build a JavaFX (OpenJFX 21) desktop app on Java 21 that talks
  directly to the Kubernetes API server from the user's workstation. No
  server-side component, no telemetry, no credential storage beyond what
  kubeconfig already provides.
- **Consequences:** Credentials stay local; TLS validation is the OS/JVM
  default; the app cannot offer shared state, history or collaboration.
  Packaging uses `mvn javafx:run` for development and `jpackage` app-image
  recipes per platform.

## ADR-0002 — Fabric8 kubernetes-client as the only K8s access path

- **Status:** Accepted
- **Context:** Hand-rolling REST/HTTPS against the API server would duplicate
  auth (exec plugins, tokens, client certs), model classes and TLS handling.
- **Decision:** Use `io.fabric8:kubernetes-client` 7.9.0 for all cluster
  access: typed DSL in `KubernetesService.list`, `Config.fromKubeconfig` for
  auth/TLS, `Serialization` for YAML. SnakeYAML is used only for lightweight
  context-name discovery.
- **Consequences:** One dependency owns protocol behaviour; upgrades (e.g. new
  auth plugins) come for free; custom REST plumbing is prohibited by
  convention.

## ADR-0003 — Single-threaded background executor; FX thread never blocks

- **Status:** Accepted
- **Context:** JavaFX UI updates must happen on the FX thread; network calls
  must not. Concurrent operations would race on `service` and the table model.
- **Decision:** All blocking work runs on one daemon single-thread executor
  (`jkubeterm-kubernetes-io`) via the `task(ThrowingAction)` wrapper; results
  return through `Platform.runLater`.
- **Consequences:** Deterministic ordering, no locking, simple reasoning; a
  slow operation (or a hung port-forward drain) queues subsequent ones.
  Documented in the [threading model](threading.md); revisit only if
  concurrent operations become a requirement.

## ADR-0004 — External CLIs via argv-only subprocesses

- **Status:** Accepted
- **Context:** Some features (exec, port-forward, helm) map naturally onto
  `kubectl`/`helm`; shelling out through a shell would invite injection.
- **Decision:** `ExternalTools` builds argv arrays and runs them with
  `ProcessBuilder` (`redirectErrorStream`, `KUBECONFIG` env injection,
  `--context`/`--kubeconfig`/`--namespace` pinned, 30 s timeouts).
  Port-forwarding is the long-lived exception with its own process ownership
  in `JKubeTermApp` (loopback-only `--address 127.0.0.1`).
- **Consequences:** No shell parsing anywhere; the exec dialog accepts a single
  executable; child processes die with the app (`stop()`).

## ADR-0005 — Read-only, credential-free kubeconfig discovery

- **Status:** Accepted
- **Context:** Listing contexts must be fast and must never leak credentials;
  the cluster connection, however, needs full kubeconfig semantics.
- **Decision:** `KubeconfigLoader.paths`/`contexts` parse kubeconfigs with
  SnakeYAML and extract only context *names* (first-seen wins, file order
  preserved, malformed documents ignored). Full parsing — TLS material, auth
  plugins, relative cert paths — is delegated to Fabric8's
  `Config.fromKubeconfig` at connect time.
- **Consequences:** Context discovery needs no cluster access (unit-testable
  offline); kubeconfig secrets never enter the UI layer.

## ADR-0006 — YAML apply via server-side apply with client-side validation

- **Status:** Accepted (supersedes the create-or-replace behaviour described in
  the original README security note)
- **Context:** The manifest editor needs a deterministic way to apply edited or
  new YAML without clobbering fields the user did not intend to change.
- **Decision:** `KubernetesService.apply` unmarshals the YAML, requires
  `kind`, `apiVersion` and `metadata.name`, injects the selected namespace for
  namespaced kinds, and calls `client.resource(obj).serverSideApply()`. Apply
  is only reachable from the explicit *Apply YAML* button guarded by a
  confirmation dialog — never from loading a resource into the editor.
- **Consequences:** Field-level merge semantics on the server; stale README
  wording ("create-or-replace, not server-side apply") is superseded by this
  document.

## ADR-0007 — Port-forwarding delegated to a kubectl subprocess

- **Status:** Accepted (interim until roadmap item 3)
- **Context:** JKubeTerm needs loopback port-forwarding now; Fabric8's
  port-forward API plus a manager UI is planned but not implemented.
- **Decision:** Run `kubectl port-forward --address 127.0.0.1 …` as a child
  process owned by `JKubeTermApp` (tracked in `portProcesses`, destroyed in
  `stop()`); output is drained on the worker. Validation happens before
  launch (port ranges 1–65535).
- **Consequences:** Requires `kubectl` on PATH; forwards have no stop/reconnect
  UI and stop when the app exits; replacement by a Fabric8-based forward
  manager is tracked in the [roadmap](../operations/roadmap.md).

## ADR-0008 — Hardcoded cluster-scoped kind list for apply

- **Status:** Accepted (interim)
- **Context:** Applying a manifest whose kind is cluster-scoped must not get a
  namespace injected, but v0.1.0 has no API discovery.
- **Decision:** `KubernetesService.isClusterScoped` keeps a static list
  (`Node`, `Namespace`, `PersistentVolume`, `ClusterRole`,
  `ClusterRoleBinding`, `CustomResourceDefinition`, `StorageClass`).
- **Consequences:** Unknown cluster-scoped kinds would be namespaced and fail
  server-side; acceptable for the supported catalog, replaced by API
  discovery / CRD browsing on the roadmap.

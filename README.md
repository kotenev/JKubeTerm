# JKubeTerm 0.1.0

Cross-platform JavaFX desktop Kubernetes client for **Linux and macOS** (Java 21).

## Features available in this starter

- Discover named contexts from `~/.kube/config` or the `KUBECONFIG` environment variable (multiple paths separated by the platform path separator).
- Connect to a selected context with the Fabric8 Java Kubernetes API client, using kubeconfig authentication, CA trust and client certificate/key configuration.
- Select namespaces; browse Pods, Deployments, StatefulSets, DaemonSets, Services, ConfigMaps, Jobs, CronJobs, Ingresses, PVCs, Events, Nodes, Namespaces and PVs.
- Search resources by name, inspect YAML, edit/create/replace YAML with explicit confirmation, delete with confirmation, export YAML.
- Tail the last 500 log lines of a selected Pod container.
- Scale and restart Deployments with confirmation.
- Optional external integrations: `kubectl exec` for a one-shot executable, `kubectl port-forward` bound to loopback, and `helm list`.

**Scope:** This is an initial desktop application, **not a complete KubeTerm clone**. Streaming terminal/logs, CRD discovery, metrics, Prometheus, interactive file browser, Helm install/upgrade/rollback, OIDC login UI and AI assistant are not yet implemented. `kubectl` and `helm` must be on PATH for their corresponding optional actions. The exec dialog runs a single executable, not an interactive TTY or shell command parser.

## Prerequisites

- JDK 21 (Linux or macOS)
- Apache Maven 3.9+
- Desktop graphical session (JavaFX/OpenJFX native dependencies are resolved by Maven for the host platform)
- A reachable Kubernetes cluster, with working kubeconfig and RBAC permissions
- Optional: `kubectl`, `helm` binaries installed on PATH

## Run

```bash
mvn clean javafx:run
```

For Minikube, verify first:

```bash
minikube status
kubectl --context minikube get pods -A
```

Select the `minikube` context in the top bar and click **Connect**.

## Test and package

```bash
mvn test
mvn clean package
```

For a platform-specific native application image, install `jpackage` (comes with JDK 21), then build on the **target operating system**. Maven's dependency plugin can copy runtime dependencies:

```bash
mvn -DskipTests package dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target/dependency
jpackage --type app-image --name JKubeTerm --input target --main-jar jkubeterm-0.1.0.jar \
  --main-class dev.jkubeterm.JKubeTermApp --class-path 'dependency/*' --dest target/dist
```

The `jpackage` recipe is a starting point: JavaFX native launcher/module-path requirements can differ across vendors, so validate the packaged image on Linux and macOS independently. For reliable development use `mvn javafx:run`.

## Security notes

- TLS certificate validation is **not disabled**. Kubeconfig `certificate-authority-data`, `certificate-authority`, client certificates and key paths are handled by Fabric8's kubeconfig loader. Relative file paths should be resolved using the original kubeconfig location.
- If you encounter `CERTIFICATE_VERIFY_FAILED`, verify your selected context, CA certificate, and cluster API endpoint. Never make `trustCerts=true` your default workaround.
- Cluster credentials remain local; the app does not operate a backend service.
- YAML view can contain sensitive configuration data. Avoid sharing screenshots or exports of secrets.
- YAML edits use create-or-replace (not server-side apply) and require confirmation. Preserve the original manifest before making changes.
- Operations obey the user's Kubernetes RBAC privileges. Do not run the application with more privilege than needed.
- Port forwarding binds to `127.0.0.1` by default; child processes terminate on normal application exit.

## Architecture

`JKubeTermApp` (JavaFX UI) → `KubernetesService` (Fabric8 API) → Kubernetes API server.
`KubeconfigLoader` discovers local contexts. `ExternalTools` performs argv-based subprocess invocation for optional commands without invoking a shell. Network calls use a background single-thread executor.

Detailed architectural documentation (C4 views, sequences, threading, ADRs, plus PlantUML, Mermaid, Archi/ArchiMate and Structurizr models) lives in `docs/` — build and preview it with `pip install -r requirements-docs.txt && mkdocs serve` (see `mkdocs.yml`).

## Roadmap

1. Streaming Kubernetes Watch API and incremental list updates.
2. Native interactive Exec WebSocket terminal and log stream.
3. Port-forward manager with stop/reconnect controls.
4. API discovery and CRD browser; Metrics Server and Prometheus dashboard.
5. Helm releases lifecycle, GitOps (Argo CD/Flux), file transfer and pod file browser.
6. Optional AI assistant with read-only default and explicit approval for writes.

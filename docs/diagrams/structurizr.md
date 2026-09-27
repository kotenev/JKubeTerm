# Structurizr (C4 DSL) model

The C4 model of JKubeTerm as a [Structurizr](https://structurizr.com/) DSL
workspace. Source of truth: `docs/models/jkubeterm-structurizr.dsl`
(excluded from the published site; used by the Structurizr CLI and
structurizr.com workspaces).

## 1. Workspace

```dsl
workspace "JKubeTerm" "Architecture of the JKubeTerm JavaFX desktop Kubernetes client (Linux/macOS, Java 21, Fabric8 7.9.0)." {

  model {
    user = person "Kubernetes Developer" "Uses JKubeTerm to view and manage Kubernetes resources from a desktop."

    jkubeterm = softwareSystem "JKubeTerm" "JavaFX desktop Kubernetes client." {

      ui = container "UI Shell" "Context/namespace/kind selection, resource table with filter, YAML editor, dialogs, output console, status bar." "JavaFX 21 (JKubeTermApp)"

      worker = container "Worker Executor" "Single daemon-thread executor (jkubeterm-kubernetes-io); runs every blocking task; results returned via Platform.runLater." "java.util.concurrent"

      service = container "Kubernetes Service" "AutoCloseable wrapper over the Fabric8 client: list 14 kinds, YAML codec, logs (tail 500), server-side apply, delete, scale, restart, namespaces, version." "Java 21, Fabric8 7.9.0"

      loader = container "Kubeconfig Loader" "Resolves $KUBECONFIG (or ~/.kube/config), extracts context names with SnakeYAML, builds Fabric8 Config with TLS and client certificates." "Java 21, SnakeYAML 2.3"

      bridge = container "External Tools Bridge" "argv-only ProcessBuilder execution of kubectl exec / port-forward and helm list with KUBECONFIG injection and 30 s timeouts." "Java 21, ProcessBuilder"
    }

    kubernetes = softwareSystem "Kubernetes Cluster" "Target cluster; API server reached over HTTPS." {
      apiserver = container "API Server" "Kubernetes control plane API." "Kubernetes"
    }

    kubeconfig = softwareSystem "Kubeconfig Files" "Local kubeconfig file(s) holding contexts, clusters, users and TLS material." "Local files"

    kubectl = softwareSystem "kubectl" "kubectl CLI on PATH; used for one-shot exec and loopback port-forwarding." "External CLI"
    helm = softwareSystem "helm" "helm CLI on PATH; used to list releases." "External CLI"

    user -> ui "Views and manages resources with"
    ui -> worker "Submits blocking tasks (task/ThrowingAction)"
    worker -> service "Invokes"
    worker -> bridge "Runs CLI commands (argv, 30 s)"
    ui -> loader "Discovers contexts (FX thread; also '↻ Config')"
    service -> loader "config(contextRef)"
    loader -> kubeconfig "Reads context names and TLS/auth material"
    service -> apiserver "Lists/inspects/applies/deletes resources; reads logs [HTTPS :443]"
    bridge -> kubectl "Executes with pinned --context/--kubeconfig/--namespace"
    bridge -> helm "Executes with pinned --kube-context/--kubeconfig/--namespace"
    kubectl -> apiserver "Exec stream / port-forward [HTTPS :443]"
    helm -> apiserver "Release list [HTTPS :443]"
  }

  views {

    systemContext jkubeterm "SystemContext" "JKubeTerm in its environment" {
      include *
      autolayout lr
    }

    container jkubeterm "Containers" "Internal structure of JKubeTerm" {
      include *
      autolayout tb
    }

    component service "Components" "Inside the Kubernetes Service container" {
      include *
      autolayout lr
    }

    dynamic jkubeterm "ConnectFlow" "Connecting to a kubeconfig context" {
      ui -> worker "submit connect task"
      worker -> loader "Config.fromKubeconfig(name, file)"
      worker -> service "new KubernetesService(...)"
      service -> apiserver "GET /version"
      service -> apiserver "GET /api/v1/namespaces"
      worker -> ui "Platform.runLater: swap client, fill namespaces, refresh"
    }

    dynamic jkubeterm "ApplyYamlFlow" "Applying edited YAML (server-side apply)" {
      ui -> ui "confirmation dialog"
      ui -> worker "task(apply)"
      worker -> service "apply(yaml, ns): unmarshal, validate kind/apiVersion/name, default namespace"
      service -> apiserver "serverSideApply()"
      worker -> ui "console + refresh"
    }

    styles {
      element "Person" {
        shape Person
        background #08427b
        color #ffffff
      }
      element "Software System" {
        background #1168bd
        color #ffffff
      }
      element "Container" {
        background #438dd5
        color #ffffff
      }
      element "External System" {
        background #999999
        color #ffffff
      }
    }
  }
}
```

## 2. View catalog

| View key | Type | Shows |
|---|---|---|
| `SystemContext` | systemContext | User, JKubeTerm, kubeconfig files, cluster, kubectl, helm |
| `Containers` | container | UI Shell, Worker Executor, Kubernetes Service, Kubeconfig Loader, External Tools Bridge + externals |
| `Components` | component | internals of Kubernetes Service (rendered from code scan when using the Structurizr client) |
| `ConnectFlow` | dynamic | runtime: connect → version/namespaces → swap + refresh |
| `ApplyYamlFlow` | dynamic | confirm → validate → serverSideApply → refresh |

## 3. Rendering

```bash
# Structurizr CLI (https://github.com/structurizr/cli)
structurizr export -workspace docs/models/jkubeterm-structurizr.dsl -format mermaid
structurizr export -workspace docs/models/jkubeterm-structurizr.dsl -format plantuml

# Or create a workspace at structurizr.com and paste the DSL into the DSL editor.
```

## 4. Consistency notes

- Element names mirror the [element registry](index.md#element-name-registry-shared-vocabulary)
  used by the PlantUML and ArchiMate models.
- The `.dsl` file is the machine-readable source for C4 views; the Mermaid C4
  blocks in [mermaid.md](mermaid.md) are hand-kept renderings of the same
  model (Mermaid C4 is a subset — no dynamic views).
- If a component changes (e.g. the roadmap replaces kubectl port-forward with
  a Fabric8 forward manager), update this DSL, the Mermaid/PlantUML sources
  and the Archi exchange file in the same change.

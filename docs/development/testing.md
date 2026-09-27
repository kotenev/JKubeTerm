# Testing

## Current suite

`src/test/java/dev/jkubeterm/KubeconfigLoaderTest.java` (JUnit Jupiter
5.11.4, `mvn test`):

| Test | Verifies |
|---|---|
| `discoversContextsWithoutClusterConnection` | `KubeconfigLoader.contexts` parses two synthetic contexts (`minikube`, `development`) from a temp kubeconfig — order preserved, no cluster needed |
| `missingPathIsIgnored` | `KubeconfigLoader.paths` skips non-existent entries and returns an empty list |

## What is covered vs. not

| Area | Coverage |
|---|---|
| Kubeconfig path resolution & context discovery | ✅ unit tests |
| `ResourceKind` catalog / Fabric8 mapping | ❌ requires a cluster (or mock server) — not yet covered |
| `KubernetesService` (list/apply/logs/scale/restart) | ❌ integration-test candidate with a fake API server (e.g. Fabric8 server mock) |
| `ExternalTools` argv building / run timeouts | ❌ unit-testable (argv shape, timeout IOException) — candidates |
| JavaFX UI behaviour | ❌ manual verification (`mvn javafx:run`) |

## Guidance for new tests

- Pure logic (path resolution, argv construction, validation rules) is
  deliberately side-effect-free — prefer plain JUnit 5 unit tests there.
- Cluster-touching code belongs behind `KubernetesService`; use Fabric8's
  KubernetesMockServer for integration tests rather than live clusters.
- Surefire runs with `useModulePath=false`; tests live on the classpath and
  do not need JavaFX (the `Application` class is never instantiated in tests).

## Running

```bash
mvn test
```

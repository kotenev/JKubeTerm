# JKubeTerm — Agent Notes

Single-module Maven Java 21 JavaFX desktop K8s client (`dev.jkubeterm:jkubeterm:0.1.0`). No monorepo, no CI, no linter. All main code flat in `src/main/java/dev/jkubeterm/` (14 classes); entrypoint `dev.jkubeterm.JKubeTermApp`.

## Build / run / test (JDK 21)

- Requires JDK 21 (`maven.compiler.release=21` in `pom.xml`). If default `java` is newer, set `JAVA_HOME` to a JDK 21 home before Maven, e.g. `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn ...`.
- Dev run (validated path): `mvn clean javafx:run` — needs desktop session + reachable cluster + kubeconfig.
- All tests: `mvn test`. Single test: `mvn -Dtest=KubeconfigLoaderTest test` (Surefire runs with `useModulePath=false`, classpath, JUnit 5).
- Package: `mvn clean package`.
- Docs site: `python3 -m pip install -r requirements-docs.txt && mkdocs serve` (preview) / `mkdocs build` (output in `site/`). PlantUML needs `docker compose up plantuml-server` on `localhost:8080`.

## jpackage gotcha

- `README.md` jpackage snippet (`--class-path 'dependency/*'`) is stale and fails with "JavaFX runtime components are missing". Trust `docs/development/build.md`: copy main JAR + runtime deps **flat** into one `--input` dir, no `--class-path` flag. Build on the target OS; validate Linux/macOS images independently.

## Architecture rules (do not break)

- Threading: all `KubernetesService` network calls run on the single daemon worker `jkubeterm-kubernetes-io`, never on the JavaFX thread. `ExternalTools.runStreaming` drains output on the calling thread — always call from the worker. UI updates via `Platform.runLater`.
- `ResourceKind` (14 values) owns the catalog; `namespaced=false` only for Nodes, Namespaces, PersistentVolumes. `KubernetesService.list()` switches on it; cluster-scoped kinds ignore namespace.
- `KubeconfigLoader`: parses `KUBECONFIG` multi-path via `File.pathSeparator`, skips missing files, dedups by context name (first wins). Relative cert/key paths are resolved by Fabric8 against the kubeconfig file path — do not re-resolve manually.
- `ExternalTools`: argv-based `ProcessBuilder`, never a shell. Sets `KUBECONFIG` env to the context file; `kubectl`/`helm` argv always include `--context/--kube-context --kubeconfig --namespace`. `kubectl`/`helm` must be on `PATH`; port-forward binds `127.0.0.1`.
- YAML apply is `client.resource(obj).serverSideApply()` (create-or-replace, not `kubectl apply`), requires `kind`/`apiVersion`/`metadata.name`, defaults namespace for non-cluster-scoped kinds. All destructive ops (apply/delete/scale/restart) require UI confirmation — never auto-execute on load.
- `AppConfig` (`~/.jkubeterm/config.properties`) is plain-properties + clamping, no JavaFX — keep it unit-testable.
- TLS verification stays enabled (kubeconfig CA/client certs via Fabric8). Never add `trustCerts=true` as a workaround.

## Testing conventions

- `docs/development/testing.md` coverage table is stale (lists only `KubeconfigLoaderTest`; there are 9 test classes). Pure logic (path resolution, argv shape, validation, clamping) → plain JUnit 5 unit tests, no cluster. Cluster-touching code belongs behind `KubernetesService`; use Fabric8 mock server, never a live cluster. JavaFX UI is manual-only (`mvn javafx:run`); never instantiate `Application` in tests.
- Do not edit build outputs: `target/`, `site/`, `docs/**/*.pdf` (gitignored).

## Docs

- Source of truth for behavior is `pom.xml` + `src/` + `docs/development/build.md`. Detailed design (threading, sequences, ADRs) lives in `docs/architecture/`; `docs/models/*` (`.archi`, `.dsl`) is versioned but excluded from the MkDocs site via `exclude_docs`.

# Build & packaging

## Prerequisites

- JDK 21 (Linux or macOS)
- Apache Maven 3.9+
- A desktop graphical session (JavaFX native dependencies resolve via Maven)
- Optional: `kubectl` and/or `helm` on `PATH` for the external-tool features

## Commands

| Task | Command |
|---|---|
| Run in development | `mvn clean javafx:run` |
| Run tests | `mvn test` |
| Package JAR | `mvn clean package` |
| Copy runtime deps for `jpackage` | `mvn -DskipTests package dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target/dependency` |
| Native app image | see the `jpackage` recipe in the repository-root `README.md` — `--main-class dev.jkubeterm.JKubeTermApp` |

```bash
# Full packaging sequence (per-platform, run on the target OS).
# NOTE: jpackage has no --class-path option. All jars (main + runtime deps)
# must sit FLAT in --input; --main-jar is relative to that dir.
# A 'dependency/*' subdir inside --input is NOT on the classpath — the app
# fails at launch with "JavaFX runtime components are missing".
mvn -DskipTests package dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target/dependency
rm -rf target/jpackage-input target/dist && mkdir -p target/jpackage-input
cp target/jkubeterm-0.1.0.jar target/dependency/*.jar target/jpackage-input/
jpackage --type app-image --name JKubeTerm --input target/jpackage-input \
  --main-jar jkubeterm-0.1.0.jar --main-class dev.jkubeterm.JKubeTermApp --dest target/dist
```

Validate the packaged image on Linux and macOS independently — JavaFX
module-path requirements differ across JDK vendors.

## Maven structure

| Element | Value |
|---|---|
| Coordinates | `dev.jkubeterm:jkubeterm:0.1.0` |
| Compiler release | 21 (`maven.compiler.release`) |
| Main class | `dev.jkubeterm.JKubeTermApp` (`javafx-maven-plugin`) |
| Surefire | `useModulePath=false` (classpath tests with JUnit 5) |

## Documentation site

```bash
python3 -m pip install -r requirements-docs.txt
mkdocs serve   # http://127.0.0.1:8000
mkdocs build   # output in site/
```

- Mermaid renders client-side (Mermaid 11 CDN + `docs/js/mermaid-init.js`,
  wired through `pymdownx.superfences` custom fences).
- PlantUML blocks are published as sources; render with the PlantUML JAR/web
  server, or enable `plantuml-markdown` (commented in `requirements-docs.txt`)
  for server-side images.
- `docs/models/*` (`.archi`, `.dsl`) are excluded from the site output via
  `exclude_docs` in `mkdocs.yml` but versioned with the repository.

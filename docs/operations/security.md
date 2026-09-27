# Security

JKubeTerm's architecture treats cluster credentials and TLS as first-class
constraints. Summary of the enforced posture (code-verified):

## Cluster connection

- **TLS certificate validation is never disabled.** The client config comes
  from `Config.fromKubeconfig` with `trustCerts` left untouched — CA data,
  CA files, client certificates and keys are applied exactly as the kubeconfig
  specifies.
- **No backend service.** The app is a desktop process; cluster credentials
  never leave the machine except to the selected cluster API endpoint.
- **Credentials are never displayed or logged.** Context discovery reads only
  context *names*; kubeconfig bodies are parsed in-memory and discarded.
- Operations run with the **user's RBAC privileges**; the app performs no
  privilege escalation. Run it with no more privilege than needed.

## Mutating operations

| Operation | Guard |
|---|---|
| Apply YAML | Confirmation dialog quoting the target context; client-side validation (kind, apiVersion, metadata.name); **server-side apply**; never triggered by merely viewing a resource |
| Delete | Confirmation dialog quoting kind/name/context |
| Scale / Restart | Numeric validation + confirmation dialogs |
| Port forward | Input validation (ports 1–65535) **before** process start |

## External processes

- **argv-only invocation** — `ProcessBuilder` with argument arrays; no shell is
  ever invoked, so quoting/injection of pod names, namespaces or commands is
  not possible through the UI paths.
- The exec dialog accepts a **single executable** — explicitly not a shell
  command parser.
- `kubectl`/`helm` are pinned to the selected context via
  `--context/--kubeconfig/--namespace` (and `KUBECONFIG` env), so an ambient
  kubeconfig cannot silently redirect child processes.
- Port-forwarding is **loopback-only** (`--address 127.0.0.1`).
- All child processes are destroyed on application exit (`stop()`); external
  tool runs enforce a 30 s timeout with `destroyForcibly()`.

## Data handling cautions

- The YAML view can contain sensitive configuration (secrets, tokens,
  endpoints). Avoid sharing screenshots or exports of secret manifests.
- Port-forward listeners are local-only, but anything that can reach
  `127.0.0.1` on the workstation can reach the forwarded port while it runs.
- If `CERTIFICATE_VERIFY_FAILED` occurs, verify the selected context, CA and
  API endpoint — **never** "fix" it by enabling `trustCerts` as a default.

## Threat-model notes for contributors

- New subprocess integrations must keep the argv-only pattern
  (ADR-0004) and pin the kubeconfig/context explicitly.
- New mutating features must add a confirmation dialog (principle P5) and
  honour cluster-scoped kind handling (ADR-0008).
- Do not log exceptions that embed kubeconfig content; the error dialog shows
  exception messages only.

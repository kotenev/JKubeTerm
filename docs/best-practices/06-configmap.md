# ConfigMaps — 30 best practices

*Plain-text config without rebuilds — and without secrets.*

> **PDF:** `06-configmap.pdf` — `tools/export-book-pdf.sh` (Chromium `--print-to-pdf`, same as guides/training).

## In JKubeTerm

Open any ConfigMaps row: the **Object** view breaks it into typed sections, **Best practices** cards flag violations with Fix hints, and drill-down jumps to related objects. Practices Wiki (`Help → Practices Wiki…`) holds the same entries with per-user Markdown overrides.

## Practices

### :rotating_light: Never store private keys in ConfigMaps {#cm-no-plaintext-tls-keys}

**ID:** `cm-no-plaintext-tls-keys` · **Severity:** CRITICAL · **Applies to:** ConfigMap

**Why it matters.** Private keys in ConfigMaps are readable by anyone with get on ConfigMaps cluster-wide (usually far broader than Secret access) and ship in backups, exports and the JKubeTerm Save YAML flow.

**Fix in JKubeTerm.** Move all private keys to Secrets (TLS Secrets for certs) with RBAC scoped to consuming ServiceAccounts; scan ConfigMaps for `PRIVATE KEY` strings in CI.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/configuration/secret/)

### :warning: Never dump whole ConfigMaps into env {#cm-no-env-dump}

**ID:** `cm-no-env-dump` · **Severity:** WARN · **Applies to:** ConfigMap

**Why it matters.** envFrom of giant ConfigMaps leaks unrelated keys into every process environment, expanding breach blast radius.

**Fix in JKubeTerm.** Import only needed keys via valueFrom; keep env surface minimal and reviewed.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/tasks/configure-pod-container/configure-pod-configmap/)

### :warning: Scan ConfigMaps for secret patterns in CI {#cm-secret-scan-ci}

**ID:** `cm-secret-scan-ci` · **Severity:** WARN · **Applies to:** ConfigMap

**Why it matters.** Credentials slip into ConfigMaps through copy-paste and env dumps, living in plain text across backups.

**Fix in JKubeTerm.** Run secret scanners (gitleaks/trufflehog) on manifests; block merges on matches except allowlisted examples.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/configuration/secret/)

### :warning: Treat secret-looking filenames as review triggers {#cm-sensitive-filenames}

**ID:** `cm-sensitive-filenames` · **Severity:** WARN · **Applies to:** ConfigMap

**Why it matters.** Keys named `password`, `token` or `ca.crt` inside ConfigMaps usually mean credentials that missed their Secret, and reviewers skim past familiar filenames.

**Fix in JKubeTerm.** JKubeTerm flags credential-looking keys as WARN findings; move matches to Secrets. Keep allowlist comments for false positives (example placeholders).

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/configuration/secret/)

### :warning: Keep ConfigMaps small and few per Pod {#cm-size-limits}

**ID:** `cm-size-limits` · **Severity:** WARN · **Applies to:** ConfigMap

**Why it matters.** etcd caps objects around 1.5MB and the API rejects bigger ConfigMaps, while mounting dozens of ConfigMaps slows Pod startup and kubelet sync measurably.

**Fix in JKubeTerm.** Keep ConfigMaps well under 1MB, split by consumer, mount only needed keys with `items:`. Large blobs belong in object storage or images, not etcd.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/configuration/configmap/)

### :warning: Validate config at deploy time, not at 3 AM {#cm-validation}

**ID:** `cm-validation` · **Severity:** WARN · **Applies to:** ConfigMap

**Why it matters.** A typo in nginx.conf or a bad JSON blob passes Apply and only explodes when Pods restart, turning a config edit into an outage hours later.

**Fix in JKubeTerm.** Validate in CI (nginx -t, jq, schema checks) and add init-container validation that fails fast before app containers start. Block deploys on validation errors.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/configuration/configmap/)

### :warning: Never put secrets into ConfigMaps (alias) {#config-no-secrets}

**ID:** `config-no-secrets` · **Severity:** WARN · **Applies to:** ConfigMap

**Why it matters.** Compatibility alias for the ClusterAdvisor trigger `config-no-secrets`; canonical guidance lives in `cm-no-secrets` in this wiki.

**Fix in JKubeTerm.** See `cm-no-secrets` above for the full JKubeTerm workflow.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/configuration/secret/)

### :information_source: Don't churn annotations that trigger rollouts {#cm-annotation-churn}

**ID:** `cm-annotation-churn` · **Severity:** INFO · **Applies to:** ConfigMap

**Why it matters.** Checksum annotations that include volatile fields (timestamps, build IDs) roll Pods on every pipeline run even when config is identical, wasting capacity and risking incidents.

**Fix in JKubeTerm.** Hash only the config content, not metadata; exclude timestamps from checksummed data. Verify rollout triggers only on real content diffs.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/overview/working-with-objects/annotations/)

### :information_source: Keep ConfigMaps in Git even when created imperatively {#cm-backup-git}

**ID:** `cm-backup-git` · **Severity:** INFO · **Applies to:** ConfigMap

**Why it matters.** `kubectl create configmap --from-file` one-liners vanish from history; the next cluster rebuild or namespace delete loses config nobody can reproduce.

**Fix in JKubeTerm.** Export (`kubectl get -o yaml`, JKubeTerm Save YAML) into the GitOps repo immediately after imperative creation, then manage declaratively from then on.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/configuration/configmap/)

### :information_source: Prefer binaryData for non-UTF8 blobs {#cm-binary-data}

**ID:** `cm-binary-data` · **Severity:** INFO · **Applies to:** ConfigMap

**Why it matters.** Stuffing certificates or archives into string `data:` corrupts bytes through UTF-8 validation and YAML escaping, producing intermittent TLS or parse failures.

**Fix in JKubeTerm.** Put binary blobs in `binaryData` (base64), keep text in `data`. In JKubeTerm the Object view counts binaryKeys separately — a nonzero count deserves a look.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/configuration/configmap/)

### :information_source: Trigger rollouts on config change with checksums {#cm-checksum-reload}

**ID:** `cm-checksum-reload` · **Severity:** INFO · **Applies to:** ConfigMap, Deployment, StatefulSet, DaemonSet

**Why it matters.** ConfigMap edits alone restart zero Pods, so the change sits applied-but-inactive until someone happens to redeploy, creating config drift between Git and running Pods.

**Fix in JKubeTerm.** Annotate Pod templates with the ConfigMap checksum (Helm `sha256sum`, Kustomize hashes, or reloader controllers) so every edit rolls the workload automatically.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/configuration/configmap/)

### :information_source: Resolve generator collisions in Kustomize explicitly {#cm-conflict-kustomize}

**ID:** `cm-conflict-kustomize` · **Severity:** INFO · **Applies to:** ConfigMap

**Why it matters.** Two generators producing the same ConfigMap name with different content flip-flop on every build, and the winner depends on file order nobody controls.

**Fix in JKubeTerm.** Name generators distinctly or use `behavior: replace`, and pin the hash suffix discipline per overlay. Diff generated output in CI before applying.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/tasks/manage-kubernetes-objects/kustomization/)

### :information_source: Document every key: owner, format, reload behavior {#cm-docs-ownership}

**ID:** `cm-docs-ownership` · **Severity:** INFO · **Applies to:** ConfigMap

**Why it matters.** Undocumented keys accumulate: nobody knows who consumes `FEATURE_X`, whether it needs restarts, or what values are valid, so changes become guesswork.

**Fix in JKubeTerm.** Annotate ConfigMaps with owner, per-key format and reload notes; link the owning chart. JKubeTerm attribute help explains keys generically — ownership lives in annotations.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/configuration/configmap/)

### :information_source: Trace config consumers with drill-down before editing {#cm-drill-ownership}

**ID:** `cm-drill-ownership` · **Severity:** INFO · **Applies to:** ConfigMap

**Why it matters.** Editing a shared ConfigMap without knowing its consumers restarts (or breaks) unrelated workloads that mounted the same keys.

**Fix in JKubeTerm.** In JKubeTerm drill down from the ConfigMap to using workloads first (used-by targets), then decide between in-place edit, versioned copy, or per-consumer split.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/configuration/configmap/)

### :information_source: Dry-run and diff ConfigMaps before applying {#cm-dry-run-diff}

**ID:** `cm-dry-run-diff` · **Severity:** INFO · **Applies to:** ConfigMap

**Why it matters.** Blind applies overwrite working config with typos; rollback needs the previous content nobody saved.

**Fix in JKubeTerm.** Always dry-run -o yaml plus diff against live before apply; keep previous versions in Git.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/configuration/configmap/)

### :information_source: Mind YAML block scalars for multiline config {#cm-encoding-yaml}

**ID:** `cm-encoding-yaml` · **Severity:** INFO · **Applies to:** ConfigMap

**Why it matters.** Multiline configs with wrong indentation or tabs silently change meaning (`|` vs `>` newlines, tab rejection), producing configs that look right in YAML but parse wrong in the app.

**Fix in JKubeTerm.** Use `|` literal blocks for configs, validate rendered output with `kubectl create configmap --dry-run -o yaml`, and diff before applying. JKubeTerm shows `\\n` escapes in previews — expand via full YAML.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/configuration/configmap/)

### :information_source: Choose env for flags, volumes for files {#cm-env-vs-volume}

**ID:** `cm-env-vs-volume` · **Severity:** INFO · **Applies to:** ConfigMap

**Why it matters.** Env-injected config needs Pod restarts to change (invisible staleness), while file mounts update live but surprise apps that cache file contents at startup.

**Fix in JKubeTerm.** Use env for small flags consumed at boot, volumes for files the app re-reads or watches. Document which pattern each key uses in the chart README.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/tasks/configure-pod-container/configure-pod-configmap/)

### :information_source: Migrate to immutable with dual-name rollout {#cm-immutable-migration}

**ID:** `cm-immutable-migration` · **Severity:** INFO · **Applies to:** ConfigMap

**Why it matters.** Flipping immutable in place is rejected; teams force-delete and cause outages.

**Fix in JKubeTerm.** Create the -v2 immutable copy, flip references, verify, then delete v1 — never edit in place.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/configuration/configmap/#immutable-configmaps)

### :information_source: Keep key counts reviewable per ConfigMap {#cm-key-count}

**ID:** `cm-key-count` · **Severity:** INFO · **Applies to:** ConfigMap

**Why it matters.** Hundred-key ConfigMaps mix owners and reload semantics; one edit risks unrelated consumers.

**Fix in JKubeTerm.** Split by consumer with under ~20 keys each; list consumers in annotations.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/configuration/configmap/)

### :information_source: Split large configs by update cadence {#cm-large-file-split}

**ID:** `cm-large-file-split` · **Severity:** INFO · **Applies to:** ConfigMap

**Why it matters.** One giant ConfigMap mixing stable certificates with daily feature flags forces full rollouts for trivial flag flips and risks touching TLS material accidentally.

**Fix in JKubeTerm.** Split into stable (certs, schemas) and volatile (flags, templates) ConfigMaps with separate rollout policies. Version the stable one, checksum the volatile one.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/configuration/configmap/)

### :information_source: Normalize line endings in file-backed keys {#cm-line-endings}

**ID:** `cm-line-endings` · **Severity:** INFO · **Applies to:** ConfigMap

**Why it matters.** CRLF from Windows editors breaks Unix parsers and checksums, causing phantom diffs and parse errors.

**Fix in JKubeTerm.** Enforce LF via .gitattributes and CI checks on config repos.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/configuration/configmap/)

### :information_source: Duplicate shared config per namespace, don't cross-mount {#cm-namespace-scope}

**ID:** `cm-namespace-scope` · **Severity:** INFO · **Applies to:** ConfigMap

**Why it matters.** ConfigMaps are namespace-scoped; cross-namespace mounts need reflectors or CSI tricks that break the security boundary and surprise auditors.

**Fix in JKubeTerm.** Replicate truly shared config per namespace via GitOps or a reflector controller with explicit RBAC; keep the source of truth in Git, not in copy scripts.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/configuration/configmap/)

### :information_source: Keep binary blobs out of string data {#cm-no-binary-in-data}

**ID:** `cm-no-binary-in-data` · **Severity:** INFO · **Applies to:** ConfigMap

**Why it matters.** UTF-8 validation rejects or mangles binary pasted into data, failing Apply with cryptic errors.

**Fix in JKubeTerm.** Use binaryData for non-UTF8; validate with dry-run before applying.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/configuration/configmap/)

### :information_source: Mark cross-namespace or bootstrap refs optional deliberately {#cm-optional-refs}

**ID:** `cm-optional-refs` · **Severity:** INFO · **Applies to:** ConfigMap

**Why it matters.** A missing non-optional ConfigMap blocks Pod startup with CreateContainerConfigError, which is correct for required config but fatal for best-effort overrides.

**Fix in JKubeTerm.** Set `optional: true` only for genuinely optional overlays; keep required config strict so misconfigurations fail fast and loud in Events.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/tasks/configure-pod-container/configure-pod-configmap/)

### :information_source: Annotate owner and reload contract {#cm-owner-annotation}

**ID:** `cm-owner-annotation` · **Severity:** INFO · **Applies to:** ConfigMap

**Why it matters.** Ownerless ConfigMaps fear edits; nobody knows the blast radius or reload path.

**Fix in JKubeTerm.** Annotate owner team, consumers and reload behavior; link the owning chart.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/overview/working-with-objects/annotations/)

### :information_source: Reload app config without restarts where supported {#cm-reloader-sidecar}

**ID:** `cm-reloader-sidecar` · **Severity:** INFO · **Applies to:** ConfigMap

**Why it matters.** Restarting stateful or connection-heavy apps for every config tweak causes churn and dropped connections, yet stale config is equally unacceptable.

**Fix in JKubeTerm.** Where the app supports SIGHUP or watch APIs, add a reloader sidecar (or inotify logic) that signals on mounted-file changes instead of rolling Pods. Prefer this over restarts for hot paths.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/configuration/configmap/)

### :information_source: Escape template delimiters in config bodies {#cm-template-delimiters}

**ID:** `cm-template-delimiters` · **Severity:** INFO · **Applies to:** ConfigMap

**Why it matters.** Helm/Go delimiters inside nginx or app configs render as empty strings, shipping broken configs that parse fine as YAML.

**Fix in JKubeTerm.** Escape delimiters or use alternative delimiters for config bodies containing template syntax.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/configuration/configmap/)

### :information_source: Version ConfigMap names for atomic rollouts {#cm-versioned-names}

**ID:** `cm-versioned-names` · **Severity:** INFO · **Applies to:** ConfigMap

**Why it matters.** Editing a shared ConfigMap rolls config gradually across Pods with up-to-a-minute skew, so old and new code run against mismatched config simultaneously.

**Fix in JKubeTerm.** Create `app-config-v2` alongside v1, flip the Deployment reference in one rollout, then delete v1. Tools like Kustomize configMapGenerator hash names automatically.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/configuration/configmap/)

### :information_source: Decide watch vs restart per consumer {#cm-watch-vs-restart}

**ID:** `cm-watch-vs-restart` · **Severity:** INFO · **Applies to:** ConfigMap

**Why it matters.** Assuming all mounts hot-reload (or all need restarts) causes stale config or needless churn fleet-wide.

**Fix in JKubeTerm.** Document per-key reload behavior; wire watches or checksums accordingly per workload.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/configuration/configmap/)

### :information_source: Mark stable ConfigMaps immutable (alias) {#config-immutable}

**ID:** `config-immutable` · **Severity:** INFO · **Applies to:** ConfigMap

**Why it matters.** Compatibility alias for the ClusterAdvisor trigger `config-immutable`; canonical guidance lives in `cm-immutable` in this wiki.

**Fix in JKubeTerm.** See `cm-immutable` above for the full JKubeTerm workflow.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/configuration/configmap/#immutable-configmaps)

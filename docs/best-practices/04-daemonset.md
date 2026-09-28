# DaemonSets — 30 best practices

*One agent per node, done right.*

> **PDF:** `04-daemonset.pdf` — `tools/export-book-pdf.sh` (Chromium `--print-to-pdf`, same as guides/training).

## In JKubeTerm

Open any DaemonSets row: the **Object** view breaks it into typed sections, **Best practices** cards flag violations with Fix hints, and drill-down jumps to related objects. Practices Wiki (`Help → Practices Wiki…`) holds the same entries with per-user Markdown overrides.

## Practices

### :rotating_light: Never share host network, PID or IPC (alias) {#host-namespaces}

**ID:** `host-namespaces` · **Severity:** CRITICAL · **Applies to:** Pod, Deployment, StatefulSet, DaemonSet

**Why it matters.** Compatibility alias for the ClusterAdvisor trigger `host-namespaces`; canonical guidance lives in `pod-host-namespaces` in this wiki.

**Fix in JKubeTerm.** See `pod-host-namespaces` above for the full JKubeTerm workflow.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/security/pod-security-standards/)

### :warning: Probe DaemonSet agents so broken nodes surface {#daemonset-health-probes}

**ID:** `daemonset-health-probes` · **Severity:** WARN · **Applies to:** DaemonSet

**Why it matters.** An agent wedged on one node keeps its Pod Running forever with no signal, so dashboards silently miss that node's metrics and logs for days.

**Fix in JKubeTerm.** Add liveness and readiness probes to every agent container; alert on DaemonSet desired vs ready divergence per node, not just totals.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/)

### :warning: Mount host paths read-only and minimal {#daemonset-hostpath-minimal}

**ID:** `daemonset-hostpath-minimal` · **Severity:** WARN · **Applies to:** DaemonSet

**Why it matters.** Agents commonly mount /var/log or /run, but a writable / or Docker socket mount turns any agent bug into full node compromise.

**Fix in JKubeTerm.** Mount only the needed host paths, readOnly where possible, never the socket unless the agent is a container runtime component. Prefer projected kubelet endpoints over host mounts.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/storage/volumes/#hostpath)

### :warning: Cap DaemonSet resource usage per node {#daemonset-resources}

**ID:** `daemonset-resources` · **Severity:** WARN · **Applies to:** DaemonSet

**Why it matters.** An uncapped log or monitoring agent grows with node log volume until it starves application Pods of CPU and memory on every node simultaneously.

**Fix in JKubeTerm.** Set conservative requests and firm limits per agent container; alert on per-node agent usage trending up week over week.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/)

### :warning: Harden DaemonSet security contexts despite host access needs {#daemonset-security-context}

**ID:** `daemonset-security-context` · **Severity:** WARN · **Applies to:** DaemonSet

**Why it matters.** DaemonSets often run privileged for host access, and teams copy-paste privileged:true to every agent including ones that only read logs.

**Fix in JKubeTerm.** Grant privileged/host access only to agents that prove the need (CNI/CSI); run log and metrics agents with runAsNonRoot, dropped capabilities and read-only mounts.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/tasks/configure-pod-container/security-context/)

### :warning: Pin image tags, never float on latest (alias) {#pinned-image}

**ID:** `pinned-image` · **Severity:** WARN · **Applies to:** Pod, Deployment, StatefulSet, DaemonSet, Job, CronJob

**Why it matters.** Compatibility alias for the ClusterAdvisor trigger `pinned-image`; canonical guidance lives in `pod-pinned-image` in this wiki.

**Fix in JKubeTerm.** See `pod-pinned-image` above for the full JKubeTerm workflow.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/containers/images/#image-names)

### :warning: Enforce seccompProfile RuntimeDefault {#pod-seccomp}

**ID:** `pod-seccomp` · **Severity:** WARN · **Applies to:** Pod, Deployment, StatefulSet, DaemonSet, Job, CronJob

**Why it matters.** Without a seccomp profile every container keeps some 300+ syscalls available, including exotic ones useful only for exploits. RuntimeDefault trims this to a sane allowlist with zero app changes in almost all cases.

**Fix in JKubeTerm.** Set `securityContext.seccompProfile.type: RuntimeDefault` at Pod level so it covers all containers including init ones. Test in staging first; legacy apps needing exotic syscalls are the rare exception.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/tasks/configure-pod-container/security-context/) · [kubernetes.io](https://kubernetes.io/docs/concepts/security/pod-security-standards/)

### :warning: Every container needs a readinessProbe (alias) {#readiness-probe}

**ID:** `readiness-probe` · **Severity:** WARN · **Applies to:** Pod, Deployment, StatefulSet, DaemonSet

**Why it matters.** Compatibility alias for the ClusterAdvisor trigger `readiness-probe`; canonical guidance lives in `pod-probe-readiness` in this wiki.

**Fix in JKubeTerm.** See `pod-probe-readiness` above for the full JKubeTerm workflow.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/)

### :warning: Set resource requests on every container (alias) {#resource-requests}

**ID:** `resource-requests` · **Severity:** WARN · **Applies to:** Pod, Deployment, StatefulSet, DaemonSet, Job, CronJob

**Why it matters.** Compatibility alias for the ClusterAdvisor trigger `resource-requests`; canonical guidance lives in `pod-resources-requests` in this wiki.

**Fix in JKubeTerm.** See `pod-resources-requests` above for the full JKubeTerm workflow.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/)

### :warning: Run containers as non-root (alias) {#run-as-non-root}

**ID:** `run-as-non-root` · **Severity:** WARN · **Applies to:** Pod, Deployment, StatefulSet, DaemonSet, Job, CronJob

**Why it matters.** Compatibility alias for the ClusterAdvisor trigger `run-as-non-root`; canonical guidance lives in `pod-run-as-nonroot` in this wiki.

**Fix in JKubeTerm.** See `pod-run-as-nonroot` above for the full JKubeTerm workflow.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/tasks/configure-pod-container/security-context/)

### :information_source: Trigger rollouts on config change with checksums {#cm-checksum-reload}

**ID:** `cm-checksum-reload` · **Severity:** INFO · **Applies to:** ConfigMap, Deployment, StatefulSet, DaemonSet

**Why it matters.** ConfigMap edits alone restart zero Pods, so the change sits applied-but-inactive until someone happens to redeploy, creating config drift between Git and running Pods.

**Fix in JKubeTerm.** Annotate Pod templates with the ConfigMap checksum (Helm `sha256sum`, Kustomize hashes, or reloader controllers) so every edit rolls the workload automatically.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/configuration/configmap/)

### :information_source: Plan DaemonSet behavior during drains and upgrades {#daemonset-drain-behavior}

**ID:** `daemonset-drain-behavior` · **Severity:** INFO · **Applies to:** DaemonSet

**Why it matters.** `kubectl drain` ignores DaemonSets by default, so CNI agents keep running (good) but log shippers also keep running and can block the drain if misconfigured with finalizers.

**Fix in JKubeTerm.** Document which agents must survive drains; avoid finalizers on DaemonSet Pods; use --ignore-daemonsets deliberately in runbooks with per-agent expectations.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/tasks/administer-cluster/safely-drain-node/)

### :information_source: Rotate and cap agent log volume {#daemonset-log-rotation}

**ID:** `daemonset-log-rotation` · **Severity:** INFO · **Applies to:** DaemonSet

**Why it matters.** Verbose agents fill node disks, triggering DiskPressure evictions of application Pods — the monitor kills what it watches.

**Fix in JKubeTerm.** Set container log rotation (kubelet flags) plus agent-side sampling and level controls; alert on node disk growth rate, not just thresholds.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/cluster-administration/logging/)

### :information_source: Scrape DaemonSet agents themselves with Prometheus {#daemonset-metrics-self}

**ID:** `daemonset-metrics-self` · **Severity:** INFO · **Applies to:** DaemonSet

**Why it matters.** Agents reporting on others while unmonitored themselves fail silently; node-exporter down on 10% of nodes looks like those nodes being quiet.

**Fix in JKubeTerm.** Expose /metrics on agents, add ServiceMonitor or PodMonitor coverage, and alert on up==0 per DaemonSet per node.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/cluster-administration/monitoring/)

### :information_source: Scope DaemonSets with node selectors or affinity {#daemonset-node-selector}

**ID:** `daemonset-node-selector` · **Severity:** INFO · **Applies to:** DaemonSet

**Why it matters.** A DaemonSet without scoping lands on every node including GPU, Windows or control-plane nodes where the agent crashes, wastes resources or breaks compliance.

**Fix in JKubeTerm.** Add nodeSelector or nodeAffinity for the intended OS, arch and role labels; keep control-plane scheduling only for CNI-class agents with explicit tolerations.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/controllers/daemonset/)

### :information_source: Clean up orphaned DaemonSet Pods after label changes {#daemonset-orphan-cleanup}

**ID:** `daemonset-orphan-cleanup` · **Severity:** INFO · **Applies to:** DaemonSet

**Why it matters.** Changing a DaemonSet selector or node labels orphans old Pods that no controller owns; they keep running old agents invisibly.

**Fix in JKubeTerm.** After selector or label changes, list Pods without owners and delete orphans; prefer creating a new DaemonSet over editing selectors in place.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/controllers/daemonset/)

### :information_source: Give node-critical DaemonSets high priority {#daemonset-priority}

**ID:** `daemonset-priority` · **Severity:** INFO · **Applies to:** DaemonSet

**Why it matters.** During node pressure the kubelet evicts by priority; a CNI or logging agent with default priority can be killed exactly when the node most needs observability and networking.

**Fix in JKubeTerm.** Assign system-node-critical or a high custom PriorityClass to CNI, CSI and logging agents; keep best-effort agents at default or lower.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/scheduling-eviction/pod-priority-preemption/)

### :information_source: Validate each node after DaemonSet rollout steps {#daemonset-rolling-validation}

**ID:** `daemonset-rolling-validation` · **Severity:** INFO · **Applies to:** DaemonSet

**Why it matters.** A bad agent version that breaks networking or mounts takes down nodes one by one as the rollout advances, with no automatic rollback signal.

**Fix in JKubeTerm.** After each rollout step verify node Ready, agent metrics and a canary Pod on the updated node before continuing; keep the previous version one command away.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/controllers/daemonset/#updating-a-daemonset)

### :information_source: DaemonSets need tolerations for tainted nodes (alias) {#daemonset-tolerations}

**ID:** `daemonset-tolerations` · **Severity:** INFO · **Applies to:** DaemonSet

**Why it matters.** Compatibility alias for the ClusterAdvisor trigger `daemonset-tolerations`; canonical guidance lives in `ds-tolerations` in this wiki.

**Fix in JKubeTerm.** See `ds-tolerations` above for the full JKubeTerm workflow.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/controllers/daemonset/)

### :information_source: Roll DaemonSets with maxUnavailable, never all at once {#daemonset-update-surge}

**ID:** `daemonset-update-surge` · **Severity:** INFO · **Applies to:** DaemonSet

**Why it matters.** The default RollingUpdate with maxUnavailable 1 is safe; raising it or using OnDelete without a rollout process leaves nodes on mixed agent versions indefinitely.

**Fix in JKubeTerm.** Keep RollingUpdate maxUnavailable at 1 (or 10% on large fleets); for OnDelete, script the node-by-node Pod deletion and verify each node before moving on.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/controllers/daemonset/#updating-a-daemonset)

### :information_source: Keep DaemonSet versions in step with kubelet {#daemonset-version-skew}

**ID:** `daemonset-version-skew` · **Severity:** INFO · **Applies to:** DaemonSet

**Why it matters.** CNI and CSI agents lagging several minors behind kubelet hit deprecated APIs and feature gates, causing subtle networking or mount failures after node upgrades.

**Fix in JKubeTerm.** Upgrade DaemonSets as part of every node-upgrade runbook step; pin versions in Git and verify agent-to-kubelet skew stays within policy.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/setup/release/version-skew-policy/)

### :information_source: Split Windows and Linux DaemonSets explicitly {#daemonset-windows-linux}

**ID:** `daemonset-windows-linux` · **Severity:** INFO · **Applies to:** DaemonSet

**Why it matters.** A single DaemonSet with Linux images schedules failing Pods on Windows nodes (and vice versa), polluting Events and masking real failures.

**Fix in JKubeTerm.** Use nodeSelector kubernetes.io/os per DaemonSet variant; keep images and host paths OS-appropriate. Mixed clusters need this from day one.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/controllers/daemonset/)

### :information_source: Match imagePullPolicy to the tagging strategy (alias) {#image-pull-policy}

**ID:** `image-pull-policy` · **Severity:** INFO · **Applies to:** Pod, Deployment, StatefulSet, DaemonSet, Job, CronJob

**Why it matters.** Compatibility alias for the ClusterAdvisor trigger `image-pull-policy`; canonical guidance lives in `pod-image-pull-policy` in this wiki.

**Fix in JKubeTerm.** See `pod-image-pull-policy` above for the full JKubeTerm workflow.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/containers/images/#updating-images)

### :information_source: Add a livenessProbe distinct from readiness (alias) {#liveness-probe}

**ID:** `liveness-probe` · **Severity:** INFO · **Applies to:** Pod, Deployment, StatefulSet, DaemonSet

**Why it matters.** Compatibility alias for the ClusterAdvisor trigger `liveness-probe`; canonical guidance lives in `pod-probe-liveness` in this wiki.

**Fix in JKubeTerm.** See `pod-probe-liveness` above for the full JKubeTerm workflow.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/)

### :information_source: Disable privilege escalation explicitly (alias) {#no-privilege-escalation}

**ID:** `no-privilege-escalation` · **Severity:** INFO · **Applies to:** Pod, Deployment, StatefulSet, DaemonSet, Job, CronJob

**Why it matters.** Compatibility alias for the ClusterAdvisor trigger `no-privilege-escalation`; canonical guidance lives in `pod-no-escalation` in this wiki.

**Fix in JKubeTerm.** See `pod-no-escalation` above for the full JKubeTerm workflow.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/tasks/configure-pod-container/security-context/)

### :information_source: Pull private images with imagePullSecrets, not public mirrors {#pod-imagepullsecrets}

**ID:** `pod-imagepullsecrets` · **Severity:** INFO · **Applies to:** Pod, Deployment, StatefulSet, DaemonSet, Job, CronJob

**Why it matters.** ImagePullBackOff from expired or missing registry credentials is a top self-inflicted outage, and copying images to public namespaces leaks proprietary code.

**Fix in JKubeTerm.** Create a `docker-registry` Secret per registry and reference it in `imagePullSecrets`; rotate before expiry and alert on pull errors in Events. Never bake credentials into the image.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/tasks/configure-pod-container/pull-image-private-registry/)

### :information_source: Gate startup on dependencies with init containers {#pod-init-containers}

**ID:** `pod-init-containers` · **Severity:** INFO · **Applies to:** Pod, Deployment, StatefulSet, DaemonSet, Job, CronJob

**Why it matters.** Main containers that start before a database migration or a config download finishes crash in a loop, spamming restarts and hiding the real error. Init containers run strictly in order and must all succeed before app containers start, turning racy startups into deterministic ones.

**Fix in JKubeTerm.** Add init containers for migrations, permission fixes and config fetches; keep them small and fast. In JKubeTerm select the Pod row and use Pod logs to pick the init container by name when startup hangs.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/pods/init-containers/)

### :information_source: Prioritize system and critical workloads explicitly {#pod-priority-class}

**ID:** `pod-priority-class` · **Severity:** INFO · **Applies to:** Pod, Deployment, StatefulSet, DaemonSet

**Why it matters.** Without priorities, the scheduler and descheduler treat a logging agent and the payment API as equals during preemption and eviction storms.

**Fix in JKubeTerm.** Create PriorityClasses (e.g. high-priority: 1000000, batch-low: -1000, never negative for prod) and assign them; set `preemptionPolicy: Never` for best-effort batch so it never kills others.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/scheduling-eviction/pod-priority-preemption/)

### :information_source: Disable ServiceAccount token automount when unused {#pod-serviceaccount-token}

**ID:** `pod-serviceaccount-token` · **Severity:** INFO · **Applies to:** Pod, Deployment, StatefulSet, DaemonSet, Job, CronJob

**Why it matters.** Every Pod gets an API token by default, so a compromised app container can immediately talk to the API server with the Pod's RBAC. Most app Pods never call the API at all.

**Fix in JKubeTerm.** Set `automountServiceAccountToken: false` at Pod spec level (or per ServiceAccount) unless the app uses the API, Helm hooks or cloud SDKs needing it. In JKubeTerm check the Pod YAML for the projected volume disappearing.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/tasks/configure-pod-container/configure-service-account/)

### :information_source: Set resource limits to contain leaks (alias) {#resource-limits}

**ID:** `resource-limits` · **Severity:** INFO · **Applies to:** Pod, Deployment, StatefulSet, DaemonSet, Job, CronJob

**Why it matters.** Compatibility alias for the ClusterAdvisor trigger `resource-limits`; canonical guidance lives in `pod-resources-limits` in this wiki.

**Fix in JKubeTerm.** See `pod-resources-limits` above for the full JKubeTerm workflow.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/)

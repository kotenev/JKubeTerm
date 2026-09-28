# Deployments — 37 best practices

*Stateless rollouts without downtime.*

> **PDF:** `02-deployment.pdf` — `tools/export-book-pdf.sh` (Chromium `--print-to-pdf`, same as guides/training).

## In JKubeTerm

Open any Deployments row: the **Object** view breaks it into typed sections, **Best practices** cards flag violations with Fix hints, and drill-down jumps to related objects. Practices Wiki (`Help → Practices Wiki…`) holds the same entries with per-user Markdown overrides.

## Practices

### :rotating_light: Never share host network, PID or IPC (alias) {#host-namespaces}

**ID:** `host-namespaces` · **Severity:** CRITICAL · **Applies to:** Pod, Deployment, StatefulSet, DaemonSet

**Why it matters.** Compatibility alias for the ClusterAdvisor trigger `host-namespaces`; canonical guidance lives in `pod-host-namespaces` in this wiki.

**Fix in JKubeTerm.** See `pod-host-namespaces` above for the full JKubeTerm workflow.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/security/pod-security-standards/)

### :warning: Keep replicas off the same node with anti-affinity {#deploy-anti-affinity}

**ID:** `deploy-anti-affinity` · **Severity:** WARN · **Applies to:** Deployment, StatefulSet

**Why it matters.** Two replicas on one node share power, kubelet, disk and fate; a single node failure becomes a full outage despite replicas: 2 looking safe on paper.

**Fix in JKubeTerm.** Add requiredDuringScheduling podAntiAffinity on hostname for critical services (preferred for best-effort). Verify in JKubeTerm that Pod node names differ after a Restart.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/scheduling-eviction/assign-pod-node/#affinity-and-anti-affinity)

### :warning: Fail stuck rollouts fast with progressDeadlineSeconds {#deploy-progress-deadline}

**ID:** `deploy-progress-deadline` · **Severity:** WARN · **Applies to:** Deployment

**Why it matters.** Without a deadline a rollout with an unpullable image sits at 0 updated forever while alerts stay green, because nothing formally declares the Deployment failed.

**Fix in JKubeTerm.** Set `progressDeadlineSeconds: 600` (lower for fast pipelines) so the Deployment reports ProgressDeadlineExceeded, then alert on that condition and auto-roll back.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/controllers/deployment/#failed-deployment)

### :warning: Keep a tested rollback runbook, not just undo hope {#deploy-rollback-runbook}

**ID:** `deploy-rollback-runbook` · **Severity:** WARN · **Applies to:** Deployment

**Why it matters.** `rollout undo` fails exactly when you need it most: pruned history, changed selectors, or migrated schemas that old code cannot read.

**Fix in JKubeTerm.** Document per service: history depth, schema compatibility window, and the forward-fix vs undo decision. Rehearse undo on staging quarterly; keep deploys forward- and backward-compatible for one release.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/controllers/deployment/#rolling-back-a-deployment)

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

### :warning: Give apps time to shut down gracefully {#pod-termination-grace}

**ID:** `pod-termination-grace` · **Severity:** WARN · **Applies to:** Pod, Deployment, StatefulSet

**Why it matters.** The default 30 second grace is too short for connection draining or queue flushing, so rolling updates drop in-flight requests. SIGTERM followed by too-early SIGKILL is the classic source of 502 spikes during deploys.

**Fix in JKubeTerm.** Set `terminationGracePeriodSeconds` to cover drain time (60-120 for web, more for workers) and implement SIGTERM handling plus a `preStop` sleep matching the readiness delay. In JKubeTerm watch the old Pods go Terminating one by one during a Restart.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/#pod-termination)

### :warning: ReadWriteOnce volumes allow a single writer (alias) {#pvc-rwo-replicas}

**ID:** `pvc-rwo-replicas` · **Severity:** WARN · **Applies to:** PersistentVolumeClaim, Deployment, StatefulSet

**Why it matters.** Compatibility alias for the ClusterAdvisor trigger `pvc-rwo-replicas`; canonical guidance lives in `pvc-rwo-single-writer` in this wiki.

**Fix in JKubeTerm.** See `pvc-rwo-single-writer` above for the full JKubeTerm workflow.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/storage/persistent-volumes/#access-modes)

### :warning: Every container needs a readinessProbe (alias) {#readiness-probe}

**ID:** `readiness-probe` · **Severity:** WARN · **Applies to:** Pod, Deployment, StatefulSet, DaemonSet

**Why it matters.** Compatibility alias for the ClusterAdvisor trigger `readiness-probe`; canonical guidance lives in `pod-probe-readiness` in this wiki.

**Fix in JKubeTerm.** See `pod-probe-readiness` above for the full JKubeTerm workflow.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/)

### :warning: Run at least two replicas for availability (alias) {#replica-count}

**ID:** `replica-count` · **Severity:** WARN · **Applies to:** Deployment

**Why it matters.** Compatibility alias for the ClusterAdvisor trigger `replica-count`; canonical guidance lives in `deploy-replica-count` in this wiki.

**Fix in JKubeTerm.** See `deploy-replica-count` above for the full JKubeTerm workflow.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/controllers/deployment/)

### :warning: Set resource requests on every container (alias) {#resource-requests}

**ID:** `resource-requests` · **Severity:** WARN · **Applies to:** Pod, Deployment, StatefulSet, DaemonSet, Job, CronJob

**Why it matters.** Compatibility alias for the ClusterAdvisor trigger `resource-requests`; canonical guidance lives in `pod-resources-requests` in this wiki.

**Fix in JKubeTerm.** See `pod-resources-requests` above for the full JKubeTerm workflow.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/)

### :warning: A stuck rollout is an incident, not patience (alias) {#rollout-complete}

**ID:** `rollout-complete` · **Severity:** WARN · **Applies to:** Deployment

**Why it matters.** Compatibility alias for the ClusterAdvisor trigger `rollout-complete`; canonical guidance lives in `deploy-rollout-complete` in this wiki.

**Fix in JKubeTerm.** See `deploy-rollout-complete` above for the full JKubeTerm workflow.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/controllers/deployment/#checking-rollout-status)

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

### :information_source: Roll Pods automatically when ConfigMaps change {#deploy-config-rollout}

**ID:** `deploy-config-rollout` · **Severity:** INFO · **Applies to:** Deployment

**Why it matters.** Updating a ConfigMap changes zero running Pods: env values need restarts and mounted files skew for a minute, so half the fleet silently runs stale config.

**Fix in JKubeTerm.** Add a checksum annotation (`config-checksum: sha256:...`, tools like reloader do it automatically) to the Pod template so every config change triggers a rolling update. Or adopt a reloader controller.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/configuration/configmap/)

### :information_source: Let HPA own replica counts, not humans {#deploy-hpa}

**ID:** `deploy-hpa` · **Severity:** INFO · **Applies to:** Deployment

**Why it matters.** Manual Scale clicks fight the autoscaler: HPA immediately reverts hand-set counts, and static counts either waste money idle or melt under load.

**Fix in JKubeTerm.** Define HPA with min/max plus CPU and custom-metric targets and scaling behavior (stabilization windows); scale manually only by editing the HPA. In JKubeTerm expect the replica count to move on its own — that is HPA working.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/tasks/run-application/horizontal-pod-autoscale/)

### :information_source: Hold new Pods with minReadySeconds before killing old ones {#deploy-min-ready}

**ID:** `deploy-min-ready` · **Severity:** INFO · **Applies to:** Deployment

**Why it matters.** A Pod that passes readiness once then crashes on the first real request still counts as a successful rollout step, cascading the bad version across the fleet at full speed.

**Fix in JKubeTerm.** Set `minReadySeconds: 20-60` so each new Pod must stay ready before the rollout advances. Combine with real readiness probes, not TCP checks.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/controllers/deployment/)

### :information_source: Pause runaway rollouts instead of deleting things {#deploy-pause}

**ID:** `deploy-pause` · **Severity:** INFO · **Applies to:** Deployment

**Why it matters.** Deleting Pods mid-rollout to "stop" it just spawns replacements and deepens the incident, while the bad template keeps rolling forward.

**Fix in JKubeTerm.** Run `kubectl rollout pause deploy/name` to freeze the rollout, investigate Pods and Events, then resume or undo. Teach the runbook before the incident, not during it.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/controllers/deployment/#pausing-and-resuming-a-deployment)

### :information_source: Keep enough revision history for rollbacks {#deploy-revision-history}

**ID:** `deploy-revision-history` · **Severity:** INFO · **Applies to:** Deployment

**Why it matters.** The default revisionHistoryLimit of 10 is often trimmed by GitOps tools, leaving no ReplicaSet to roll back to when a bad deploy is discovered hours later.

**Fix in JKubeTerm.** Set `revisionHistoryLimit: 10` or higher explicitly and verify `kubectl rollout history` shows entries before you need them. JKubeTerm Restart never deletes history; manual ReplicaSet cleanup does.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/controllers/deployment/#rolling-back-a-deployment)

### :information_source: Use native sidecars for proxies and agents {#deploy-sidecar-native}

**ID:** `deploy-sidecar-native` · **Severity:** INFO · **Applies to:** Deployment

**Why it matters.** Classic sidecars start in random order relative to the app and linger after it exits, breaking proxies that must be up first and draining that must finish last.

**Fix in JKubeTerm.** On 1.29+ declare sidecars as init containers with `restartPolicy: Always` so they start before the app and stop after it. Keep their resources small and probes independent.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/pods/sidecar-containers/)

### :information_source: Tune maxSurge and maxUnavailable to your capacity {#deploy-surge-tuning}

**ID:** `deploy-surge-tuning` · **Severity:** INFO · **Applies to:** Deployment

**Why it matters.** Defaults (25%/25%) surge capacity you may not have on small clusters, causing Pending during every deploy, or allow too much unavailability for single-replica services.

**Fix in JKubeTerm.** On tight clusters use `maxSurge: 1, maxUnavailable: 0`; for fast stateless fleets allow larger surge. Single-replica services must use maxUnavailable 0 plus a PDB.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/controllers/deployment/#rolling-update-deployment)

### :information_source: Match imagePullPolicy to the tagging strategy (alias) {#image-pull-policy}

**ID:** `image-pull-policy` · **Severity:** INFO · **Applies to:** Pod, Deployment, StatefulSet, DaemonSet, Job, CronJob

**Why it matters.** Compatibility alias for the ClusterAdvisor trigger `image-pull-policy`; canonical guidance lives in `pod-image-pull-policy` in this wiki.

**Fix in JKubeTerm.** See `pod-image-pull-policy` above for the full JKubeTerm workflow.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/containers/images/#updating-images)

### :information_source: Use recommended app.kubernetes.io labels (alias) {#labels-standard}

**ID:** `labels-standard` · **Severity:** INFO · **Applies to:** Deployment, StatefulSet

**Why it matters.** Compatibility alias for the ClusterAdvisor trigger `labels-standard`; canonical guidance lives in `deploy-labels-standard` in this wiki.

**Fix in JKubeTerm.** See `deploy-labels-standard` above for the full JKubeTerm workflow.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/overview/working-with-objects/common-labels/)

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

### :information_source: Co-locate or separate with affinity rules {#pod-affinity}

**ID:** `pod-affinity` · **Severity:** INFO · **Applies to:** Pod, Deployment, StatefulSet

**Why it matters.** Cache-heavy sidecars perform badly when split across nodes, while replicas of the same app on one node share fate. Random placement gives you the worst of both.

**Fix in JKubeTerm.** Use `podAffinity` to co-locate chatty pairs and `podAntiAffinity` (preferredDuringScheduling) to separate replicas. Keep rules soft unless correctness demands hard constraints.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/scheduling-eviction/assign-pod-node/)

### :information_source: Prefer CoreDNS rewrites over hostAliases {#pod-host-aliases}

**ID:** `pod-host-aliases` · **Severity:** INFO · **Applies to:** Pod, Deployment

**Why it matters.** hostAliases bake hostnames into every Pod spec, so a migration means editing dozens of manifests, and entries silently override real DNS including service names.

**Fix in JKubeTerm.** Reserve hostAliases for air-gapped bootstrap cases; otherwise add CoreDNS rewrite rules or proper DNS records. Grep manifests for hostAliases during review.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/tasks/network/customize-hosts-file-for-pods/)

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

### :information_source: Use preStop hooks for clean connection draining {#pod-lifecycle-hooks}

**ID:** `pod-lifecycle-hooks` · **Severity:** INFO · **Applies to:** Pod, Deployment, StatefulSet

**Why it matters.** Even with a grace period, apps that ignore SIGTERM keep serving until SIGKILL, then drop connections mid-request. A preStop hook buys deterministic drain behavior.

**Fix in JKubeTerm.** Add `lifecycle.preStop.exec.command: ["sh","-c","sleep 10"]` (or a real drain endpoint call) sized to your readiness period. Pair with terminationGracePeriodSeconds longer than hook plus drain time.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/containers/container-lifecycle-hooks/)

### :information_source: Prioritize system and critical workloads explicitly {#pod-priority-class}

**ID:** `pod-priority-class` · **Severity:** INFO · **Applies to:** Pod, Deployment, StatefulSet, DaemonSet

**Why it matters.** Without priorities, the scheduler and descheduler treat a logging agent and the payment API as equals during preemption and eviction storms.

**Fix in JKubeTerm.** Create PriorityClasses (e.g. high-priority: 1000000, batch-low: -1000, never negative for prod) and assign them; set `preemptionPolicy: Never` for best-effort batch so it never kills others.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/scheduling-eviction/pod-priority-preemption/)

### :information_source: Aim for Guaranteed QoS on critical workloads {#pod-qos-guaranteed}

**ID:** `pod-qos-guaranteed` · **Severity:** INFO · **Applies to:** Pod, Deployment, StatefulSet

**Why it matters.** Burstable and BestEffort Pods are evicted first under node pressure, exactly when you can least afford losing the critical path. QoS class decides who dies.

**Fix in JKubeTerm.** Set requests equal to limits for CPU and memory on critical containers to reach Guaranteed. Check `status.qosClass` in the Pod YAML in JKubeTerm after applying.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/tasks/configure-pod-container/quality-service-pod/)

### :information_source: Gate readiness on load-balancer attachment {#pod-readiness-gates}

**ID:** `pod-readiness-gates` · **Severity:** INFO · **Applies to:** Deployment

**Why it matters.** A Pod can be Ready for kube-proxy while the cloud load balancer still hasn't attached it, so the first seconds of traffic 502 during every scale-up.

**Fix in JKubeTerm.** Add readinessGates (e.g. cloud-provider LB attachment conditions) for LB-fronted services so rollout steps wait for real traffic readiness.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/#pod-readiness-gate)

### :information_source: Disable ServiceAccount token automount when unused {#pod-serviceaccount-token}

**ID:** `pod-serviceaccount-token` · **Severity:** INFO · **Applies to:** Pod, Deployment, StatefulSet, DaemonSet, Job, CronJob

**Why it matters.** Every Pod gets an API token by default, so a compromised app container can immediately talk to the API server with the Pod's RBAC. Most app Pods never call the API at all.

**Fix in JKubeTerm.** Set `automountServiceAccountToken: false` at Pod spec level (or per ServiceAccount) unless the app uses the API, Helm hooks or cloud SDKs needing it. In JKubeTerm check the Pod YAML for the projected volume disappearing.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/tasks/configure-pod-container/configure-service-account/)

### :information_source: Spread Pods across zones with topologySpreadConstraints {#pod-topology-spread}

**ID:** `pod-topology-spread` · **Severity:** INFO · **Applies to:** Pod, Deployment, StatefulSet

**Why it matters.** The scheduler happily packs all replicas onto one node or zone, so a single failure takes the whole service down. Affinity alone cannot express "spread evenly".

**Fix in JKubeTerm.** Add `topologySpreadConstraints` on `topology.kubernetes.io/zone` (maxSkew 1) and on hostname for larger fleets. Verify spread in JKubeTerm Pods view by comparing node names in the YAML.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/scheduling-eviction/topology-spread-constraints/)

### :information_source: Set resource limits to contain leaks (alias) {#resource-limits}

**ID:** `resource-limits` · **Severity:** INFO · **Applies to:** Pod, Deployment, StatefulSet, DaemonSet, Job, CronJob

**Why it matters.** Compatibility alias for the ClusterAdvisor trigger `resource-limits`; canonical guidance lives in `pod-resources-limits` in this wiki.

**Fix in JKubeTerm.** See `pod-resources-limits` above for the full JKubeTerm workflow.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/)

### :information_source: Prefer RollingUpdate over Recreate (alias) {#rolling-update}

**ID:** `rolling-update` · **Severity:** INFO · **Applies to:** Deployment

**Why it matters.** Compatibility alias for the ClusterAdvisor trigger `rolling-update`; canonical guidance lives in `deploy-rolling-update` in this wiki.

**Fix in JKubeTerm.** See `deploy-rolling-update` above for the full JKubeTerm workflow.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/controllers/deployment/#strategy)

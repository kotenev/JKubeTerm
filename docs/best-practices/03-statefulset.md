# StatefulSets — 37 best practices

*Stable identity plus per-Pod storage.*

> **PDF:** `03-statefulset.pdf` — `tools/export-book-pdf.sh` (Chromium `--print-to-pdf`, same as guides/training).

## In JKubeTerm

Open any StatefulSets row: the **Object** view breaks it into typed sections, **Best practices** cards flag violations with Fix hints, and drill-down jumps to related objects. Practices Wiki (`Help → Practices Wiki…`) holds the same entries with per-user Markdown overrides.

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

### :warning: Spread StatefulSet replicas across failure domains {#sts-anti-affinity}

**ID:** `sts-anti-affinity` · **Severity:** WARN · **Applies to:** StatefulSet

**Why it matters.** Three database replicas on one rack or zone lose everything together, and local disks make the loss permanent instead of merely disruptive.

**Fix in JKubeTerm.** Require anti-affinity on hostname and prefer spread across zones; combine with topologySpreadConstraints. Verify Pod node and zone placement in JKubeTerm after scaling.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/scheduling-eviction/assign-pod-node/#affinity-and-anti-affinity)

### :warning: Snapshot StatefulSet volumes on a schedule and test restores {#sts-backup-snapshots}

**ID:** `sts-backup-snapshots` · **Severity:** WARN · **Applies to:** StatefulSet

**Why it matters.** PVCs preserve data across Pod restarts but not across accidental deletes, retention-policy mistakes or AZ loss; untested backups are Schrödinger backups.

**Fix in JKubeTerm.** Add VolumeSnapshot schedules (or Velero) per data StatefulSet and restore to a scratch namespace quarterly. Alert on failed snapshots, not just missing ones.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/storage/volume-snapshots/)

### :warning: Give stateful apps long graceful shutdowns {#sts-graceful-shutdown}

**ID:** `sts-graceful-shutdown` · **Severity:** WARN · **Applies to:** StatefulSet

**Why it matters.** Databases flush memtables, transfer leadership and sync disks on SIGTERM; a 30 second default truncates this, risking unclean shutdowns and long recoveries.

**Fix in JKubeTerm.** Set terminationGracePeriodSeconds to minutes for databases plus preStop hooks that step down leadership. Verify clean shutdown in Pod logs before SIGKILL.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/#pod-termination)

### :warning: Every StatefulSet needs a headless governing Service {#sts-headless-service}

**ID:** `sts-headless-service` · **Severity:** WARN · **Applies to:** StatefulSet

**Why it matters.** Without `serviceName` pointing at a `clusterIP: None` Service the StatefulSet is rejected or Pods lose stable DNS identities, breaking peer discovery for clustered apps.

**Fix in JKubeTerm.** Create the headless Service first with matching selectors, reference it in `spec.serviceName`, and use `pod-0.svc.namespace.svc.cluster.local` DNS in app config.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/controllers/statefulset/)

### :warning: Protect quorum systems with a majority PDB {#sts-quorum-pdb}

**ID:** `sts-quorum-pdb` · **Severity:** WARN · **Applies to:** StatefulSet

**Why it matters.** A drain that evicts two of three etcd or Kafka voters at once kills the quorum and the whole cluster, even though each eviction looked innocent alone.

**Fix in JKubeTerm.** Set PDB minAvailable to quorum size (e.g. 2 of 3) and rehearse drains. Node upgrades must proceed one node at a time for stateful namespaces.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/pods/disruptions/)

### :information_source: Trigger rollouts on config change with checksums {#cm-checksum-reload}

**ID:** `cm-checksum-reload` · **Severity:** INFO · **Applies to:** ConfigMap, Deployment, StatefulSet, DaemonSet

**Why it matters.** ConfigMap edits alone restart zero Pods, so the change sits applied-but-inactive until someone happens to redeploy, creating config drift between Git and running Pods.

**Fix in JKubeTerm.** Annotate Pod templates with the ConfigMap checksum (Helm `sha256sum`, Kustomize hashes, or reloader controllers) so every edit rolls the workload automatically.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/configuration/configmap/)

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

### :information_source: Control PVC cleanup with retention policies on 1.35 and newer {#stateful-retention}

**ID:** `stateful-retention` · **Severity:** INFO · **Applies to:** StatefulSet

**Why it matters.** The default keeps every PVC forever: scaled-down ordinals leave orphaned disks billing you monthly, while careless deletion scripts wipe data that should survive.

**Fix in JKubeTerm.** Set `persistentVolumeClaimRetentionPolicy.whenDeleted/whenScaled` explicitly (Retain for prod data, Delete for scratch). JKubeTerm surfaces this opportunity on 1.35+ servers.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/controllers/statefulset/#persistentvolumeclaim-retention)

### :information_source: Control PVC cleanup with retention policies on 1.35 and newer (alias) {#stateful-retention}

**ID:** `stateful-retention` · **Severity:** INFO · **Applies to:** StatefulSet

**Why it matters.** Compatibility alias for the ClusterAdvisor trigger `stateful-retention`; canonical guidance lives in `stateful-retention` in this wiki.

**Fix in JKubeTerm.** See `sts-retention-policy` above for the full JKubeTerm workflow.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/controllers/statefulset/#persistentvolumeclaim-retention)

### :information_source: StatefulSets need volumeClaimTemplates (alias) {#stateful-storage}

**ID:** `stateful-storage` · **Severity:** INFO · **Applies to:** StatefulSet

**Why it matters.** Compatibility alias for the ClusterAdvisor trigger `stateful-storage`; canonical guidance lives in `sts-claim-templates` in this wiki.

**Fix in JKubeTerm.** See `sts-claim-templates` above for the full JKubeTerm workflow.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/controllers/statefulset/)

### :information_source: Run schema and bootstrap steps in init containers {#sts-init-migrations}

**ID:** `sts-init-migrations` · **Severity:** INFO · **Applies to:** StatefulSet

**Why it matters.** Ordinals racing to bootstrap the same schema concurrently corrupt migrations or deadlock on locks, and Parallel policy makes the race certain.

**Fix in JKubeTerm.** Gate bootstrap in an init container with locking (only ordinal 0 migrates, others wait). Keep migrations idempotent and forward-compatible.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/pods/init-containers/)

### :information_source: Choose OrderedReady or Parallel deliberately {#sts-ordered-policy}

**ID:** `sts-ordered-policy` · **Severity:** INFO · **Applies to:** StatefulSet

**Why it matters.** OrderedReady serializes every scale and update through ordinals, so a 20-replica rollout takes ages; Parallel everywhere breaks apps needing strict bootstrap order like etcd or Kafka.

**Fix in JKubeTerm.** Keep OrderedReady for quorum systems; use Parallel for independent shards. Document which one and why in the chart values.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/controllers/statefulset/#pod-management-policies)

### :information_source: Canary StatefulSets with update partitions {#sts-partition-canary}

**ID:** `sts-partition-canary` · **Severity:** INFO · **Applies to:** StatefulSet

**Why it matters.** A full rolling update pushes a bad binary to every database replica at once, risking quorum loss with no healthy copy left to roll back to.

**Fix in JKubeTerm.** Set `updateStrategy.rollingUpdate.partition` so only the top ordinals update first; verify, then lower the partition stepwise. Pair with per-ordinal health checks.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/controllers/statefulset/#rolling-updates)

### :information_source: Size per-Pod requests for steady-state plus compaction {#sts-resources-per-pod}

**ID:** `sts-resources-per-pod` · **Severity:** INFO · **Applies to:** StatefulSet

**Why it matters.** Databases idle low but spike during compaction, repair or rebalancing; requests sized to idle cause throttling and missed heartbeats exactly under load.

**Fix in JKubeTerm.** Size requests to p95 usage including background tasks, limits higher with headroom. Monitor per-Pod usage in Grafana per ordinal, not averaged.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/)

### :information_source: Scale StatefulSets gradually and watch each ordinal {#sts-scale-ordered}

**ID:** `sts-scale-ordered` · **Severity:** INFO · **Applies to:** StatefulSet

**Why it matters.** Bulk scaling floods the storage provisioner with simultaneous PVC claims and bootstrap storms, leaving half the ordinals Pending and the set degraded.

**Fix in JKubeTerm.** Scale a few ordinals at a time, confirm each reaches Ready and syncs data before adding more. Alert on PVC Pending longer than ten minutes.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/controllers/statefulset/#deployment-and-scaling-guarantees)

### :information_source: Build cluster config on stable ordinal DNS {#sts-stable-dns}

**ID:** `sts-stable-dns` · **Severity:** INFO · **Applies to:** StatefulSet

**Why it matters.** Hardcoding Pod IPs or relying on Service round-robin breaks quorum formation after every reschedule, because only ordinal DNS names survive Pod replacement.

**Fix in JKubeTerm.** Configure seed lists and peer URLs with `$(podname).service` hostnames. Test by deleting the highest ordinal Pod and watching rejoin via stable DNS.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/controllers/statefulset/#stable-network-id)

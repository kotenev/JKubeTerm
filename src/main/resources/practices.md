# JKubeTerm best-practices wiki
# One `## practice <id>` section per entry. User entries in ~/.jkubeterm/practices.md override bundled ones by id.

## practice pod-init-containers
Kinds: Pod, Deployment, StatefulSet, DaemonSet, Job, CronJob
Severity: INFO
Title: Gate startup on dependencies with init containers
Docs:
- https://kubernetes.io/docs/concepts/workloads/pods/init-containers/
### Why
Main containers that start before a database migration or a config download finishes crash in a loop, spamming restarts and hiding the real error. Init containers run strictly in order and must all succeed before app containers start, turning racy startups into deterministic ones.
### Fix
Add init containers for migrations, permission fixes and config fetches; keep them small and fast. In JKubeTerm select the Pod row and use Pod logs to pick the init container by name when startup hangs.

## practice pod-dns-policy
Kinds: Pod
Severity: INFO
Title: Tune dnsPolicy and ndots for service discovery
Docs:
- https://kubernetes.io/docs/concepts/services-networking/dns-pod-service/
### Why
The default `ndots:5` makes every short external hostname try all search domains first, adding latency and noisy DNS traffic. Pods that mostly call the outside world pay this tax on every lookup.
### Fix
Keep `dnsPolicy: ClusterFirst` for in-cluster discovery; for egress-heavy Pods set `dnsConfig.options: [{name: ndots, value: "2"}]`. Verify with `nslookup` via Exec, and check CoreDNS logs for NXDOMAIN storms.

## practice pod-termination-grace
Kinds: Pod, Deployment, StatefulSet
Severity: WARN
Title: Give apps time to shut down gracefully
Docs:
- https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/#pod-termination
### Why
The default 30 second grace is too short for connection draining or queue flushing, so rolling updates drop in-flight requests. SIGTERM followed by too-early SIGKILL is the classic source of 502 spikes during deploys.
### Fix
Set `terminationGracePeriodSeconds` to cover drain time (60-120 for web, more for workers) and implement SIGTERM handling plus a `preStop` sleep matching the readiness delay. In JKubeTerm watch the old Pods go Terminating one by one during a Restart.

## practice pod-topology-spread
Kinds: Pod, Deployment, StatefulSet
Severity: INFO
Title: Spread Pods across zones with topologySpreadConstraints
Docs:
- https://kubernetes.io/docs/concepts/scheduling-eviction/topology-spread-constraints/
### Why
The scheduler happily packs all replicas onto one node or zone, so a single failure takes the whole service down. Affinity alone cannot express "spread evenly".
### Fix
Add `topologySpreadConstraints` on `topology.kubernetes.io/zone` (maxSkew 1) and on hostname for larger fleets. Verify spread in JKubeTerm Pods view by comparing node names in the YAML.

## practice pod-affinity
Kinds: Pod, Deployment, StatefulSet
Severity: INFO
Title: Co-locate or separate with affinity rules
Docs:
- https://kubernetes.io/docs/concepts/scheduling-eviction/assign-pod-node/
### Why
Cache-heavy sidecars perform badly when split across nodes, while replicas of the same app on one node share fate. Random placement gives you the worst of both.
### Fix
Use `podAffinity` to co-locate chatty pairs and `podAntiAffinity` (preferredDuringScheduling) to separate replicas. Keep rules soft unless correctness demands hard constraints.

## practice pod-seccomp
Kinds: Pod, Deployment, StatefulSet, DaemonSet, Job, CronJob
Severity: WARN
Title: Enforce seccompProfile RuntimeDefault
Docs:
- https://kubernetes.io/docs/tasks/configure-pod-container/security-context/
- https://kubernetes.io/docs/concepts/security/pod-security-standards/
### Why
Without a seccomp profile every container keeps some 300+ syscalls available, including exotic ones useful only for exploits. RuntimeDefault trims this to a sane allowlist with zero app changes in almost all cases.
### Fix
Set `securityContext.seccompProfile.type: RuntimeDefault` at Pod level so it covers all containers including init ones. Test in staging first; legacy apps needing exotic syscalls are the rare exception.

## practice pod-serviceaccount-token
Kinds: Pod, Deployment, StatefulSet, DaemonSet, Job, CronJob
Severity: INFO
Title: Disable ServiceAccount token automount when unused
Docs:
- https://kubernetes.io/docs/tasks/configure-pod-container/configure-service-account/
### Why
Every Pod gets an API token by default, so a compromised app container can immediately talk to the API server with the Pod's RBAC. Most app Pods never call the API at all.
### Fix
Set `automountServiceAccountToken: false` at Pod spec level (or per ServiceAccount) unless the app uses the API, Helm hooks or cloud SDKs needing it. In JKubeTerm check the Pod YAML for the projected volume disappearing.

## practice pod-imagepullsecrets
Kinds: Pod, Deployment, StatefulSet, DaemonSet, Job, CronJob
Severity: INFO
Title: Pull private images with imagePullSecrets, not public mirrors
Docs:
- https://kubernetes.io/docs/tasks/configure-pod-container/pull-image-private-registry/
### Why
ImagePullBackOff from expired or missing registry credentials is a top self-inflicted outage, and copying images to public namespaces leaks proprietary code.
### Fix
Create a `docker-registry` Secret per registry and reference it in `imagePullSecrets`; rotate before expiry and alert on pull errors in Events. Never bake credentials into the image.

## practice pod-lifecycle-hooks
Kinds: Pod, Deployment, StatefulSet
Severity: INFO
Title: Use preStop hooks for clean connection draining
Docs:
- https://kubernetes.io/docs/concepts/containers/container-lifecycle-hooks/
### Why
Even with a grace period, apps that ignore SIGTERM keep serving until SIGKILL, then drop connections mid-request. A preStop hook buys deterministic drain behavior.
### Fix
Add `lifecycle.preStop.exec.command: ["sh","-c","sleep 10"]` (or a real drain endpoint call) sized to your readiness period. Pair with terminationGracePeriodSeconds longer than hook plus drain time.

## practice pod-qos-guaranteed
Kinds: Pod, Deployment, StatefulSet
Severity: INFO
Title: Aim for Guaranteed QoS on critical workloads
Docs:
- https://kubernetes.io/docs/tasks/configure-pod-container/quality-service-pod/
### Why
Burstable and BestEffort Pods are evicted first under node pressure, exactly when you can least afford losing the critical path. QoS class decides who dies.
### Fix
Set requests equal to limits for CPU and memory on critical containers to reach Guaranteed. Check `status.qosClass` in the Pod YAML in JKubeTerm after applying.

## practice pod-priority-class
Kinds: Pod, Deployment, StatefulSet, DaemonSet
Severity: INFO
Title: Prioritize system and critical workloads explicitly
Docs:
- https://kubernetes.io/docs/concepts/scheduling-eviction/pod-priority-preemption/
### Why
Without priorities, the scheduler and descheduler treat a logging agent and the payment API as equals during preemption and eviction storms.
### Fix
Create PriorityClasses (e.g. high-priority: 1000000, batch-low: -1000, never negative for prod) and assign them; set `preemptionPolicy: Never` for best-effort batch so it never kills others.

## practice pod-debug-ephemeral
Kinds: Pod
Severity: INFO
Title: Debug running Pods with ephemeral containers, not SSH
Docs:
- https://kubernetes.io/docs/tasks/debug/debug-application/debug-running-pod/
### Why
Exec into a distroless container gives you no shell and no tools, and rebuilding a debug image perturbs the incident. Ephemeral debug containers attach diagnostics without restarting the Pod.
### Fix
Use `kubectl debug -it pod/name --image=busybox --target=container` to attach a troubleshooting container sharing the target's namespaces. JKubeTerm Exec stays single-executable by design; reach for kubectl debug for full shells.

## practice pod-status
Kinds: Pod
Severity: CRITICAL
Title: Treat CrashLoopBackOff and ImagePullBackOff as incidents
Docs:
- https://kubernetes.io/docs/tasks/debug/debug-application/debug-pods/
### Why
A Pod stuck in CrashLoop or ImagePullBackOff never serves traffic yet keeps consuming scheduler attention and log volume; recreating it changes nothing because the cause (bad image, bad command, missing secret) persists.
### Fix
Read all container logs plus previous-container logs (`kubectl logs -p`), then Events, then fix the image, command or mounted secret. In JKubeTerm start at Pods, open Pod logs for every container, then check the Events kind filtered by Pod name.

## practice pod-resources
Kinds: Pod
Severity: INFO
Title: Use Pod-level resources on Kubernetes 1.37 and newer
Docs:
- https://kubernetes.io/docs/concepts/workloads/pods/pod-level-resources/
### Why
Container-level accounting misattributes shared overhead, so HPA and scheduling decisions wobble on Pods with sidecars. Pod-level resources (GA in 1.37) let the scheduler and autoscaler see the Pod as one unit.
### Fix
On 1.37+ clusters set `spec.resources` at Pod level for sidecar-heavy Pods and compare `kubectl top pods` against the Pod-level usage API. JKubeTerm flags this opportunity automatically on matching server versions.

## practice pod-restart-policy
Kinds: Pod, Job, CronJob
Severity: INFO
Title: Set restartPolicy deliberately on bare Pods and Jobs
Docs:
- https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/#restart-policy
### Why
Bare Pods default to Always, which silently restarts one-shot scripts forever instead of reporting failure, while Jobs reject Always outright and never start.
### Fix
Use Always for long-running bare Pods, OnFailure for retried scripts, Never for run-once debugging. Job and CronJob templates accept only Never or OnFailure — the API rejects the rest.

## practice pod-host-aliases
Kinds: Pod, Deployment
Severity: INFO
Title: Prefer CoreDNS rewrites over hostAliases
Docs:
- https://kubernetes.io/docs/tasks/network/customize-hosts-file-for-pods/
### Why
hostAliases bake hostnames into every Pod spec, so a migration means editing dozens of manifests, and entries silently override real DNS including service names.
### Fix
Reserve hostAliases for air-gapped bootstrap cases; otherwise add CoreDNS rewrite rules or proper DNS records. Grep manifests for hostAliases during review.

## practice deploy-revision-history
Kinds: Deployment
Severity: INFO
Title: Keep enough revision history for rollbacks
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/deployment/#rolling-back-a-deployment
### Why
The default revisionHistoryLimit of 10 is often trimmed by GitOps tools, leaving no ReplicaSet to roll back to when a bad deploy is discovered hours later.
### Fix
Set `revisionHistoryLimit: 10` or higher explicitly and verify `kubectl rollout history` shows entries before you need them. JKubeTerm Restart never deletes history; manual ReplicaSet cleanup does.

## practice deploy-progress-deadline
Kinds: Deployment
Severity: WARN
Title: Fail stuck rollouts fast with progressDeadlineSeconds
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/deployment/#failed-deployment
### Why
Without a deadline a rollout with an unpullable image sits at 0 updated forever while alerts stay green, because nothing formally declares the Deployment failed.
### Fix
Set `progressDeadlineSeconds: 600` (lower for fast pipelines) so the Deployment reports ProgressDeadlineExceeded, then alert on that condition and auto-roll back.

## practice deploy-min-ready
Kinds: Deployment
Severity: INFO
Title: Hold new Pods with minReadySeconds before killing old ones
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/deployment/
### Why
A Pod that passes readiness once then crashes on the first real request still counts as a successful rollout step, cascading the bad version across the fleet at full speed.
### Fix
Set `minReadySeconds: 20-60` so each new Pod must stay ready before the rollout advances. Combine with real readiness probes, not TCP checks.

## practice deploy-surge-tuning
Kinds: Deployment
Severity: INFO
Title: Tune maxSurge and maxUnavailable to your capacity
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/deployment/#rolling-update-deployment
### Why
Defaults (25%/25%) surge capacity you may not have on small clusters, causing Pending during every deploy, or allow too much unavailability for single-replica services.
### Fix
On tight clusters use `maxSurge: 1, maxUnavailable: 0`; for fast stateless fleets allow larger surge. Single-replica services must use maxUnavailable 0 plus a PDB.

## practice deploy-hpa
Kinds: Deployment
Severity: INFO
Title: Let HPA own replica counts, not humans
Docs:
- https://kubernetes.io/docs/tasks/run-application/horizontal-pod-autoscale/
### Why
Manual Scale clicks fight the autoscaler: HPA immediately reverts hand-set counts, and static counts either waste money idle or melt under load.
### Fix
Define HPA with min/max plus CPU and custom-metric targets and scaling behavior (stabilization windows); scale manually only by editing the HPA. In JKubeTerm expect the replica count to move on its own — that is HPA working.

## practice deploy-sidecar-native
Kinds: Deployment
Severity: INFO
Title: Use native sidecars for proxies and agents
Docs:
- https://kubernetes.io/docs/concepts/workloads/pods/sidecar-containers/
### Why
Classic sidecars start in random order relative to the app and linger after it exits, breaking proxies that must be up first and draining that must finish last.
### Fix
On 1.29+ declare sidecars as init containers with `restartPolicy: Always` so they start before the app and stop after it. Keep their resources small and probes independent.

## practice deploy-anti-affinity
Kinds: Deployment, StatefulSet
Severity: WARN
Title: Keep replicas off the same node with anti-affinity
Docs:
- https://kubernetes.io/docs/concepts/scheduling-eviction/assign-pod-node/#affinity-and-anti-affinity
### Why
Two replicas on one node share power, kubelet, disk and fate; a single node failure becomes a full outage despite replicas: 2 looking safe on paper.
### Fix
Add requiredDuringScheduling podAntiAffinity on hostname for critical services (preferred for best-effort). Verify in JKubeTerm that Pod node names differ after a Restart.

## practice deploy-config-rollout
Kinds: Deployment
Severity: INFO
Title: Roll Pods automatically when ConfigMaps change
Docs:
- https://kubernetes.io/docs/concepts/configuration/configmap/
### Why
Updating a ConfigMap changes zero running Pods: env values need restarts and mounted files skew for a minute, so half the fleet silently runs stale config.
### Fix
Add a checksum annotation (`config-checksum: sha256:...`, tools like reloader do it automatically) to the Pod template so every config change triggers a rolling update. Or adopt a reloader controller.

## practice deploy-pause
Kinds: Deployment
Severity: INFO
Title: Pause runaway rollouts instead of deleting things
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/deployment/#pausing-and-resuming-a-deployment
### Why
Deleting Pods mid-rollout to "stop" it just spawns replacements and deepens the incident, while the bad template keeps rolling forward.
### Fix
Run `kubectl rollout pause deploy/name` to freeze the rollout, investigate Pods and Events, then resume or undo. Teach the runbook before the incident, not during it.

## practice deploy-rollback-runbook
Kinds: Deployment
Severity: WARN
Title: Keep a tested rollback runbook, not just undo hope
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/deployment/#rolling-back-a-deployment
### Why
`rollout undo` fails exactly when you need it most: pruned history, changed selectors, or migrated schemas that old code cannot read.
### Fix
Document per service: history depth, schema compatibility window, and the forward-fix vs undo decision. Rehearse undo on staging quarterly; keep deploys forward- and backward-compatible for one release.

## practice pod-readiness-gates
Kinds: Deployment
Severity: INFO
Title: Gate readiness on load-balancer attachment
Docs:
- https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/#pod-readiness-gate
### Why
A Pod can be Ready for kube-proxy while the cloud load balancer still hasn't attached it, so the first seconds of traffic 502 during every scale-up.
### Fix
Add readinessGates (e.g. cloud-provider LB attachment conditions) for LB-fronted services so rollout steps wait for real traffic readiness.

## practice stateful-retention
Kinds: StatefulSet
Severity: INFO
Title: Control PVC cleanup with retention policies on 1.35 and newer
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/statefulset/#persistentvolumeclaim-retention
### Why
The default keeps every PVC forever: scaled-down ordinals leave orphaned disks billing you monthly, while careless deletion scripts wipe data that should survive.
### Fix
Set `persistentVolumeClaimRetentionPolicy.whenDeleted/whenScaled` explicitly (Retain for prod data, Delete for scratch). JKubeTerm surfaces this opportunity on 1.35+ servers.

## practice sts-ordered-policy
Kinds: StatefulSet
Severity: INFO
Title: Choose OrderedReady or Parallel deliberately
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/statefulset/#pod-management-policies
### Why
OrderedReady serializes every scale and update through ordinals, so a 20-replica rollout takes ages; Parallel everywhere breaks apps needing strict bootstrap order like etcd or Kafka.
### Fix
Keep OrderedReady for quorum systems; use Parallel for independent shards. Document which one and why in the chart values.

## practice sts-partition-canary
Kinds: StatefulSet
Severity: INFO
Title: Canary StatefulSets with update partitions
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/statefulset/#rolling-updates
### Why
A full rolling update pushes a bad binary to every database replica at once, risking quorum loss with no healthy copy left to roll back to.
### Fix
Set `updateStrategy.rollingUpdate.partition` so only the top ordinals update first; verify, then lower the partition stepwise. Pair with per-ordinal health checks.

## practice sts-headless-service
Kinds: StatefulSet
Severity: WARN
Title: Every StatefulSet needs a headless governing Service
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/statefulset/
### Why
Without `serviceName` pointing at a `clusterIP: None` Service the StatefulSet is rejected or Pods lose stable DNS identities, breaking peer discovery for clustered apps.
### Fix
Create the headless Service first with matching selectors, reference it in `spec.serviceName`, and use `pod-0.svc.namespace.svc.cluster.local` DNS in app config.

## practice sts-stable-dns
Kinds: StatefulSet
Severity: INFO
Title: Build cluster config on stable ordinal DNS
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/statefulset/#stable-network-id
### Why
Hardcoding Pod IPs or relying on Service round-robin breaks quorum formation after every reschedule, because only ordinal DNS names survive Pod replacement.
### Fix
Configure seed lists and peer URLs with `$(podname).service` hostnames. Test by deleting the highest ordinal Pod and watching rejoin via stable DNS.

## practice sts-backup-snapshots
Kinds: StatefulSet
Severity: WARN
Title: Snapshot StatefulSet volumes on a schedule and test restores
Docs:
- https://kubernetes.io/docs/concepts/storage/volume-snapshots/
### Why
PVCs preserve data across Pod restarts but not across accidental deletes, retention-policy mistakes or AZ loss; untested backups are Schrödinger backups.
### Fix
Add VolumeSnapshot schedules (or Velero) per data StatefulSet and restore to a scratch namespace quarterly. Alert on failed snapshots, not just missing ones.

## practice sts-quorum-pdb
Kinds: StatefulSet
Severity: WARN
Title: Protect quorum systems with a majority PDB
Docs:
- https://kubernetes.io/docs/concepts/workloads/pods/disruptions/
### Why
A drain that evicts two of three etcd or Kafka voters at once kills the quorum and the whole cluster, even though each eviction looked innocent alone.
### Fix
Set PDB minAvailable to quorum size (e.g. 2 of 3) and rehearse drains. Node upgrades must proceed one node at a time for stateful namespaces.

## practice sts-graceful-shutdown
Kinds: StatefulSet
Severity: WARN
Title: Give stateful apps long graceful shutdowns
Docs:
- https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/#pod-termination
### Why
Databases flush memtables, transfer leadership and sync disks on SIGTERM; a 30 second default truncates this, risking unclean shutdowns and long recoveries.
### Fix
Set terminationGracePeriodSeconds to minutes for databases plus preStop hooks that step down leadership. Verify clean shutdown in Pod logs before SIGKILL.

## practice sts-init-migrations
Kinds: StatefulSet
Severity: INFO
Title: Run schema and bootstrap steps in init containers
Docs:
- https://kubernetes.io/docs/concepts/workloads/pods/init-containers/
### Why
Ordinals racing to bootstrap the same schema concurrently corrupt migrations or deadlock on locks, and Parallel policy makes the race certain.
### Fix
Gate bootstrap in an init container with locking (only ordinal 0 migrates, others wait). Keep migrations idempotent and forward-compatible.

## practice sts-anti-affinity
Kinds: StatefulSet
Severity: WARN
Title: Spread StatefulSet replicas across failure domains
Docs:
- https://kubernetes.io/docs/concepts/scheduling-eviction/assign-pod-node/#affinity-and-anti-affinity
### Why
Three database replicas on one rack or zone lose everything together, and local disks make the loss permanent instead of merely disruptive.
### Fix
Require anti-affinity on hostname and prefer spread across zones; combine with topologySpreadConstraints. Verify Pod node and zone placement in JKubeTerm after scaling.

## practice sts-resources-per-pod
Kinds: StatefulSet
Severity: INFO
Title: Size per-Pod requests for steady-state plus compaction
Docs:
- https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/
### Why
Databases idle low but spike during compaction, repair or rebalancing; requests sized to idle cause throttling and missed heartbeats exactly under load.
### Fix
Size requests to p95 usage including background tasks, limits higher with headroom. Monitor per-Pod usage in Grafana per ordinal, not averaged.

## practice sts-scale-ordered
Kinds: StatefulSet
Severity: INFO
Title: Scale StatefulSets gradually and watch each ordinal
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/statefulset/#deployment-and-scaling-guarantees
### Why
Bulk scaling floods the storage provisioner with simultaneous PVC claims and bootstrap storms, leaving half the ordinals Pending and the set degraded.
### Fix
Scale a few ordinals at a time, confirm each reaches Ready and syncs data before adding more. Alert on PVC Pending longer than ten minutes.

## practice daemonset-node-selector
Kinds: DaemonSet
Severity: INFO
Title: Scope DaemonSets with node selectors or affinity
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/daemonset/
### Why
A DaemonSet without scoping lands on every node including GPU, Windows or control-plane nodes where the agent crashes, wastes resources or breaks compliance.
### Fix
Add nodeSelector or nodeAffinity for the intended OS, arch and role labels; keep control-plane scheduling only for CNI-class agents with explicit tolerations.

## practice daemonset-resources
Kinds: DaemonSet
Severity: WARN
Title: Cap DaemonSet resource usage per node
Docs:
- https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/
### Why
An uncapped log or monitoring agent grows with node log volume until it starves application Pods of CPU and memory on every node simultaneously.
### Fix
Set conservative requests and firm limits per agent container; alert on per-node agent usage trending up week over week.

## practice daemonset-update-surge
Kinds: DaemonSet
Severity: INFO
Title: Roll DaemonSets with maxUnavailable, never all at once
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/daemonset/#updating-a-daemonset
### Why
The default RollingUpdate with maxUnavailable 1 is safe; raising it or using OnDelete without a rollout process leaves nodes on mixed agent versions indefinitely.
### Fix
Keep RollingUpdate maxUnavailable at 1 (or 10% on large fleets); for OnDelete, script the node-by-node Pod deletion and verify each node before moving on.

## practice daemonset-hostpath-minimal
Kinds: DaemonSet
Severity: WARN
Title: Mount host paths read-only and minimal
Docs:
- https://kubernetes.io/docs/concepts/storage/volumes/#hostpath
### Why
Agents commonly mount /var/log or /run, but a writable / or Docker socket mount turns any agent bug into full node compromise.
### Fix
Mount only the needed host paths, readOnly where possible, never the socket unless the agent is a container runtime component. Prefer projected kubelet endpoints over host mounts.

## practice daemonset-priority
Kinds: DaemonSet
Severity: INFO
Title: Give node-critical DaemonSets high priority
Docs:
- https://kubernetes.io/docs/concepts/scheduling-eviction/pod-priority-preemption/
### Why
During node pressure the kubelet evicts by priority; a CNI or logging agent with default priority can be killed exactly when the node most needs observability and networking.
### Fix
Assign system-node-critical or a high custom PriorityClass to CNI, CSI and logging agents; keep best-effort agents at default or lower.

## practice daemonset-health-probes
Kinds: DaemonSet
Severity: WARN
Title: Probe DaemonSet agents so broken nodes surface
Docs:
- https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/
### Why
An agent wedged on one node keeps its Pod Running forever with no signal, so dashboards silently miss that node's metrics and logs for days.
### Fix
Add liveness and readiness probes to every agent container; alert on DaemonSet desired vs ready divergence per node, not just totals.

## practice daemonset-version-skew
Kinds: DaemonSet
Severity: INFO
Title: Keep DaemonSet versions in step with kubelet
Docs:
- https://kubernetes.io/docs/setup/release/version-skew-policy/
### Why
CNI and CSI agents lagging several minors behind kubelet hit deprecated APIs and feature gates, causing subtle networking or mount failures after node upgrades.
### Fix
Upgrade DaemonSets as part of every node-upgrade runbook step; pin versions in Git and verify agent-to-kubelet skew stays within policy.

## practice daemonset-log-rotation
Kinds: DaemonSet
Severity: INFO
Title: Rotate and cap agent log volume
Docs:
- https://kubernetes.io/docs/concepts/cluster-administration/logging/
### Why
Verbose agents fill node disks, triggering DiskPressure evictions of application Pods — the monitor kills what it watches.
### Fix
Set container log rotation (kubelet flags) plus agent-side sampling and level controls; alert on node disk growth rate, not just thresholds.

## practice daemonset-windows-linux
Kinds: DaemonSet
Severity: INFO
Title: Split Windows and Linux DaemonSets explicitly
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/daemonset/
### Why
A single DaemonSet with Linux images schedules failing Pods on Windows nodes (and vice versa), polluting Events and masking real failures.
### Fix
Use nodeSelector kubernetes.io/os per DaemonSet variant; keep images and host paths OS-appropriate. Mixed clusters need this from day one.

## practice daemonset-drain-behavior
Kinds: DaemonSet
Severity: INFO
Title: Plan DaemonSet behavior during drains and upgrades
Docs:
- https://kubernetes.io/docs/tasks/administer-cluster/safely-drain-node/
### Why
`kubectl drain` ignores DaemonSets by default, so CNI agents keep running (good) but log shippers also keep running and can block the drain if misconfigured with finalizers.
### Fix
Document which agents must survive drains; avoid finalizers on DaemonSet Pods; use --ignore-daemonsets deliberately in runbooks with per-agent expectations.

## practice daemonset-orphan-cleanup
Kinds: DaemonSet
Severity: INFO
Title: Clean up orphaned DaemonSet Pods after label changes
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/daemonset/
### Why
Changing a DaemonSet selector or node labels orphans old Pods that no controller owns; they keep running old agents invisibly.
### Fix
After selector or label changes, list Pods without owners and delete orphans; prefer creating a new DaemonSet over editing selectors in place.

## practice daemonset-metrics-self
Kinds: DaemonSet
Severity: INFO
Title: Scrape DaemonSet agents themselves with Prometheus
Docs:
- https://kubernetes.io/docs/concepts/cluster-administration/monitoring/
### Why
Agents reporting on others while unmonitored themselves fail silently; node-exporter down on 10% of nodes looks like those nodes being quiet.
### Fix
Expose /metrics on agents, add ServiceMonitor or PodMonitor coverage, and alert on up==0 per DaemonSet per node.

## practice daemonset-security-context
Kinds: DaemonSet
Severity: WARN
Title: Harden DaemonSet security contexts despite host access needs
Docs:
- https://kubernetes.io/docs/tasks/configure-pod-container/security-context/
### Why
DaemonSets often run privileged for host access, and teams copy-paste privileged:true to every agent including ones that only read logs.
### Fix
Grant privileged/host access only to agents that prove the need (CNI/CSI); run log and metrics agents with runAsNonRoot, dropped capabilities and read-only mounts.

## practice daemonset-rolling-validation
Kinds: DaemonSet
Severity: INFO
Title: Validate each node after DaemonSet rollout steps
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/daemonset/#updating-a-daemonset
### Why
A bad agent version that breaks networking or mounts takes down nodes one by one as the rollout advances, with no automatic rollback signal.
### Fix
After each rollout step verify node Ready, agent metrics and a canary Pod on the updated node before continuing; keep the previous version one command away.

## practice svc-endpoints-check
Kinds: Service
Severity: WARN
Title: Alert on Services with zero Endpoints
Docs:
- https://kubernetes.io/docs/concepts/services-networking/service/
### Why
Empty Endpoints means total traffic blackhole, yet the Service, Deployment and Pods each look healthy in isolation. This is the most common "everything green, app down" state.
### Fix
Alert on `kube_endpoint_address_available == 0` per Service; in JKubeTerm drill from the Service to Pods behind it to see the selector mismatch instantly.

## practice svc-headless
Kinds: Service
Severity: INFO
Title: Use headless Services for direct Pod addressing
Docs:
- https://kubernetes.io/docs/concepts/services-networking/service/#headless-services
### Why
ClusterIP load-balances, which breaks gossip protocols, leader election and stateful clients that must reach specific peers by stable DNS.
### Fix
Set `clusterIP: None` for StatefulSet governing services and peer-discovery use cases; clients then resolve all Pod A records and pick ordinals directly.

## practice svc-session-affinity
Kinds: Service
Severity: INFO
Title: Use session affinity only as a last resort
Docs:
- https://kubernetes.io/docs/concepts/services-networking/service/#session-affinity
### Why
ClientIP stickiness breaks load distribution, defeats autoscaling math, and fails over badly since clients pin to dying Pods until timeout.
### Fix
Keep the default None; fix statefulness with shared sessions (Redis) or cookies at Ingress instead. If forced, set explicit timeoutSeconds and document why.

## practice svc-externalname
Kinds: Service
Severity: INFO
Title: Point at outside dependencies with ExternalName
Docs:
- https://kubernetes.io/docs/concepts/services-networking/service/#externalname
### Why
Hardcoding external hostnames in every Deployment couples deploys to DNS migrations and prevents per-environment overrides.
### Fix
Create ExternalName Services for managed databases and SaaS endpoints; apps use the in-cluster DNS name and ops repoint one object per environment.

## practice svc-dual-stack
Kinds: Service
Severity: INFO
Title: Plan dual-stack before you need IPv6
Docs:
- https://kubernetes.io/docs/concepts/services-networking/dual-stack/
### Why
Single-stack clusters painted into IPv4 corner the whole fleet when compliance or carriers demand IPv6; retrofitting touches CNI, kube-proxy and every LoadBalancer.
### Fix
On new clusters enable dual-stack CNI with ipFamilies; for existing ones document the migration window and test Pod-to-Service paths in both families.

## practice svc-lb-source-ranges
Kinds: Service
Severity: WARN
Title: Restrict LoadBalancer sources with loadBalancerSourceRanges
Docs:
- https://kubernetes.io/docs/tasks/access-application-cluster/configure-cloud-controller-manager/
### Why
A public LoadBalancer without source ranges exposes admin UIs, ArgoCD and Grafana to the whole internet, and scanners find them within hours.
### Fix
Set `loadBalancerSourceRanges` to office/VPN CIDRs for internal tools; keep truly public endpoints on Ingress with WAF and auth instead.

## practice svc-health-probes-lb
Kinds: Service
Severity: INFO
Title: Align cloud LB health checks with readiness
Docs:
- https://kubernetes.io/docs/concepts/services-networking/service/#health-check-nodeport
### Why
Cloud LBs with default TCP checks route to Pods that pass TCP but fail readiness, serving errors from half-started apps during every rollout.
### Fix
Set `healthCheckNodePort` explicitly or use externalTrafficPolicy Local with proper probes so LB membership follows real readiness.

## practice svc-external-traffic-policy
Kinds: Service
Severity: INFO
Title: Choose externalTrafficPolicy deliberately
Docs:
- https://kubernetes.io/docs/tasks/access-application-cluster/create-external-load-balancer/#preserving-the-client-source-ip
### Why
Cluster policy SNATs client IPs (breaking rate limiting and audit) while Local policy drops traffic on nodes without local Pods (breaking small clusters).
### Fix
Use Local when client IP matters and every node runs the Pods (DaemonSet-fronted); otherwise Cluster with PROXY protocol or X-Forwarded-For at Ingress.

## practice svc-topology-routing
Kinds: Service
Severity: INFO
Title: Keep traffic zone-local with topology-aware routing
Docs:
- https://kubernetes.io/docs/concepts/services-networking/service/#topology-aware-routing
### Why
Cross-zone traffic adds latency and cloud egress bills on every request, yet default routing sprays uniformly across zones.
### Fix
Enable topology-aware hints (or internalTrafficPolicy) for high-churn internal Services; verify endpoint distribution per zone before relying on it.

## practice svc-dns-ttl
Kinds: Service
Severity: INFO
Title: Design for Service DNS TTL and caching
Docs:
- https://kubernetes.io/docs/concepts/services-networking/dns-pod-service/
### Why
Clients caching DNS for minutes keep hitting dead Pod IPs after scaling events, causing intermittent failures no dashboard explains.
### Fix
Keep Service DNS TTLs short, prefer watching Endpoints/EndpointSlice in clients, and use headless Services where clients must react fast to membership changes.

## practice svc-ports-named
Kinds: Service
Severity: INFO
Title: Name every Service port and keep targetPorts symbolic
Docs:
- https://kubernetes.io/docs/concepts/services-networking/service/#defining-a-service
### Why
Numeric port soup across Service, Deployment and Ingress drifts apart silently; a container port change breaks routing with no validation error.
### Fix
Name ports (http, metrics, grpc) and reference targetPort by name in Services and Ingress backends so renames propagate by contract.

## practice svc-endpointslice
Kinds: Service
Severity: INFO
Title: Watch EndpointSlices on large Services
Docs:
- https://kubernetes.io/docs/concepts/services-networking/endpoint-slices/
### Why
Classic Endpoints objects hit etcd size limits past a few thousand addresses, stalling updates for the biggest Services during scale events.
### Fix
Ensure EndpointSliceMirroring is on (default in modern clusters) and monitor slice counts; split mega-Services before slices fragment.

## practice svc-monitor-annotations
Kinds: Service
Severity: INFO
Title: Standardize scrape annotations on Services
Docs:
- https://kubernetes.io/docs/concepts/cluster-administration/monitoring/
### Why
Every team inventing its own prometheus.io annotations means half the fleet unscraped and dashboards with mysterious gaps.
### Fix
Standardize `prometheus.io/scrape/port/path` annotations (or ServiceMonitors) in chart templates; in JKubeTerm the Object view surfaces missing scrape config per Service.

## practice svc-internal-lb
Kinds: Service
Severity: INFO
Title: Use internal load balancers for private traffic
Docs:
- https://kubernetes.io/docs/concepts/services-networking/service/#internal-load-balancer
### Why
Routing private east-west traffic through public LBs adds cost, latency and an internet-reachable attack surface for purely internal APIs.
### Fix
Annotate internal Services for internal LB class per cloud (e.g. `service.beta.kubernetes.io/aws-load-balancer-internal`); verify with dig that names resolve privately.

## practice svc-timeout-keepalive
Kinds: Service
Severity: INFO
Title: Align Service and app timeouts with client expectations
Docs:
- https://kubernetes.io/docs/concepts/services-networking/service/
### Why
Mismatched idle timeouts between kube-proxy conntrack, cloud LBs and app servers produce sporadic connection resets under exactly the load that matters.
### Fix
Document timeout budgets end to end (client, LB, Service, app) and load-test idle connections; prefer explicit timeouts over defaults at every layer.

## practice svc-ip-family-policy
Kinds: Service
Severity: INFO
Title: Set ipFamilyPolicy explicitly on dual-stack Services
Docs:
- https://kubernetes.io/docs/concepts/services-networking/dual-stack/
### Why
Default SingleStack silently drops one family after cluster migration, breaking half the clients with no EndpointSlice hint about the missing family.
### Fix
Set `ipFamilyPolicy: PreferDualStack` (or RequireDualStack) on Services that must serve both families; verify both ClusterIPs in the Service YAML.

## practice svc-no-headless-lb
Kinds: Service
Severity: WARN
Title: Never put a headless Service behind a cloud load balancer
Docs:
- https://kubernetes.io/docs/concepts/services-networking/service/#headless-services
### Why
Headless Services have no ClusterIP for the LB to target, so the cloud controller either rejects the Service or programs nonsense backends.
### Fix
Keep headless Services ClusterIP-less for discovery only; front user traffic with a separate ClusterIP Service or Ingress.

## practice svc-app-protocol
Kinds: Service
Severity: INFO
Title: Declare appProtocol for protocol-aware infrastructure
Docs:
- https://kubernetes.io/docs/concepts/services-networking/service/#application-protocol
### Why
Service meshes and LBs guess protocols from port numbers, misclassifying gRPC as plain HTTP and breaking retries, tracing and mTLS policies.
### Fix
Set `appProtocol: kubernetes.io/h2c` (or http, grpc, ws) on ports served by meshes and protocol-aware LBs; verify mesh telemetry picks it up.

## practice svc-sticky-ingress-instead
Kinds: Service
Severity: INFO
Title: Implement stickiness at Ingress, not Service
Docs:
- https://kubernetes.io/docs/concepts/services-networking/ingress-controllers/
### Why
Service-level ClientIP affinity is coarse (IP-based, timeout-limited) while modern apps need cookie affinity with failover semantics.
### Fix
Terminate stickiness in the Ingress controller (nginx sticky sessions, mesh consistent hashing) and keep Services affinity-free.

## practice svc-audit-unused
Kinds: Service
Severity: INFO
Title: Audit and delete unused Services quarterly
Docs:
- https://kubernetes.io/docs/concepts/services-networking/service/
### Why
Stale Services accumulate cloud LBs, static IPs and DNS records that bill monthly and widen the attack surface long after the app is gone.
### Fix
Quarterly: list Services without Endpoints or with zero traffic, confirm ownership via labels, delete with DNS and LB cleanup. Automate the report, not the delete.

## practice svc-short-names
Kinds: Service
Severity: INFO
Title: Keep Service names short, stable and environment-free
Docs:
- https://kubernetes.io/docs/concepts/overview/working-with-objects/names/
### Why
Long environment-baked names (`payments-prod-us-east-1-svc`) break DNS length limits, churn on every promotion, and force app reconfig per environment.
### Fix
Name Services `payments`, qualify by namespace (`payments.prod`); resolve environment via namespace, not name. Enforce DNS-1123 length in CI.

## practice cm-size-limits
Kinds: ConfigMap
Severity: WARN
Title: Keep ConfigMaps small and few per Pod
Docs:
- https://kubernetes.io/docs/concepts/configuration/configmap/
### Why
etcd caps objects around 1.5MB and the API rejects bigger ConfigMaps, while mounting dozens of ConfigMaps slows Pod startup and kubelet sync measurably.
### Fix
Keep ConfigMaps well under 1MB, split by consumer, mount only needed keys with `items:`. Large blobs belong in object storage or images, not etcd.

## practice cm-versioned-names
Kinds: ConfigMap
Severity: INFO
Title: Version ConfigMap names for atomic rollouts
Docs:
- https://kubernetes.io/docs/concepts/configuration/configmap/
### Why
Editing a shared ConfigMap rolls config gradually across Pods with up-to-a-minute skew, so old and new code run against mismatched config simultaneously.
### Fix
Create `app-config-v2` alongside v1, flip the Deployment reference in one rollout, then delete v1. Tools like Kustomize configMapGenerator hash names automatically.

## practice cm-env-vs-volume
Kinds: ConfigMap
Severity: INFO
Title: Choose env for flags, volumes for files
Docs:
- https://kubernetes.io/docs/tasks/configure-pod-container/configure-pod-configmap/
### Why
Env-injected config needs Pod restarts to change (invisible staleness), while file mounts update live but surprise apps that cache file contents at startup.
### Fix
Use env for small flags consumed at boot, volumes for files the app re-reads or watches. Document which pattern each key uses in the chart README.

## practice cm-binary-data
Kinds: ConfigMap
Severity: INFO
Title: Prefer binaryData for non-UTF8 blobs
Docs:
- https://kubernetes.io/docs/concepts/configuration/configmap/
### Why
Stuffing certificates or archives into string `data:` corrupts bytes through UTF-8 validation and YAML escaping, producing intermittent TLS or parse failures.
### Fix
Put binary blobs in `binaryData` (base64), keep text in `data`. In JKubeTerm the Object view counts binaryKeys separately — a nonzero count deserves a look.

## practice cm-optional-refs
Kinds: ConfigMap
Severity: INFO
Title: Mark cross-namespace or bootstrap refs optional deliberately
Docs:
- https://kubernetes.io/docs/tasks/configure-pod-container/configure-pod-configmap/
### Why
A missing non-optional ConfigMap blocks Pod startup with CreateContainerConfigError, which is correct for required config but fatal for best-effort overrides.
### Fix
Set `optional: true` only for genuinely optional overlays; keep required config strict so misconfigurations fail fast and loud in Events.

## practice cm-checksum-reload
Kinds: ConfigMap, Deployment, StatefulSet, DaemonSet
Severity: INFO
Title: Trigger rollouts on config change with checksums
Docs:
- https://kubernetes.io/docs/concepts/configuration/configmap/
### Why
ConfigMap edits alone restart zero Pods, so the change sits applied-but-inactive until someone happens to redeploy, creating config drift between Git and running Pods.
### Fix
Annotate Pod templates with the ConfigMap checksum (Helm `sha256sum`, Kustomize hashes, or reloader controllers) so every edit rolls the workload automatically.

## practice cm-no-plaintext-tls-keys
Kinds: ConfigMap
Severity: CRITICAL
Title: Never store private keys in ConfigMaps
Docs:
- https://kubernetes.io/docs/concepts/configuration/secret/
### Why
Private keys in ConfigMaps are readable by anyone with get on ConfigMaps cluster-wide (usually far broader than Secret access) and ship in backups, exports and the JKubeTerm Save YAML flow.
### Fix
Move all private keys to Secrets (TLS Secrets for certs) with RBAC scoped to consuming ServiceAccounts; scan ConfigMaps for `PRIVATE KEY` strings in CI.

## practice cm-namespace-scope
Kinds: ConfigMap
Severity: INFO
Title: Duplicate shared config per namespace, don't cross-mount
Docs:
- https://kubernetes.io/docs/concepts/configuration/configmap/
### Why
ConfigMaps are namespace-scoped; cross-namespace mounts need reflectors or CSI tricks that break the security boundary and surprise auditors.
### Fix
Replicate truly shared config per namespace via GitOps or a reflector controller with explicit RBAC; keep the source of truth in Git, not in copy scripts.

## practice cm-docs-ownership
Kinds: ConfigMap
Severity: INFO
Title: Document every key: owner, format, reload behavior
Docs:
- https://kubernetes.io/docs/concepts/configuration/configmap/
### Why
Undocumented keys accumulate: nobody knows who consumes `FEATURE_X`, whether it needs restarts, or what values are valid, so changes become guesswork.
### Fix
Annotate ConfigMaps with owner, per-key format and reload notes; link the owning chart. JKubeTerm attribute help explains keys generically — ownership lives in annotations.

## practice cm-validation
Kinds: ConfigMap
Severity: WARN
Title: Validate config at deploy time, not at 3 AM
Docs:
- https://kubernetes.io/docs/concepts/configuration/configmap/
### Why
A typo in nginx.conf or a bad JSON blob passes Apply and only explodes when Pods restart, turning a config edit into an outage hours later.
### Fix
Validate in CI (nginx -t, jq, schema checks) and add init-container validation that fails fast before app containers start. Block deploys on validation errors.

## practice cm-encoding-yaml
Kinds: ConfigMap
Severity: INFO
Title: Mind YAML block scalars for multiline config
Docs:
- https://kubernetes.io/docs/concepts/configuration/configmap/
### Why
Multiline configs with wrong indentation or tabs silently change meaning (`|` vs `>` newlines, tab rejection), producing configs that look right in YAML but parse wrong in the app.
### Fix
Use `|` literal blocks for configs, validate rendered output with `kubectl create configmap --dry-run -o yaml`, and diff before applying. JKubeTerm shows `\\n` escapes in previews — expand via full YAML.

## practice cm-sensitive-filenames
Kinds: ConfigMap
Severity: WARN
Title: Treat secret-looking filenames as review triggers
Docs:
- https://kubernetes.io/docs/concepts/configuration/secret/
### Why
Keys named `password`, `token` or `ca.crt` inside ConfigMaps usually mean credentials that missed their Secret, and reviewers skim past familiar filenames.
### Fix
JKubeTerm flags credential-looking keys as WARN findings; move matches to Secrets. Keep allowlist comments for false positives (example placeholders).

## practice cm-reloader-sidecar
Kinds: ConfigMap
Severity: INFO
Title: Reload app config without restarts where supported
Docs:
- https://kubernetes.io/docs/concepts/configuration/configmap/
### Why
Restarting stateful or connection-heavy apps for every config tweak causes churn and dropped connections, yet stale config is equally unacceptable.
### Fix
Where the app supports SIGHUP or watch APIs, add a reloader sidecar (or inotify logic) that signals on mounted-file changes instead of rolling Pods. Prefer this over restarts for hot paths.

## practice cm-conflict-kustomize
Kinds: ConfigMap
Severity: INFO
Title: Resolve generator collisions in Kustomize explicitly
Docs:
- https://kubernetes.io/docs/tasks/manage-kubernetes-objects/kustomization/
### Why
Two generators producing the same ConfigMap name with different content flip-flop on every build, and the winner depends on file order nobody controls.
### Fix
Name generators distinctly or use `behavior: replace`, and pin the hash suffix discipline per overlay. Diff generated output in CI before applying.

## practice cm-drill-ownership
Kinds: ConfigMap
Severity: INFO
Title: Trace config consumers with drill-down before editing
Docs:
- https://kubernetes.io/docs/concepts/configuration/configmap/
### Why
Editing a shared ConfigMap without knowing its consumers restarts (or breaks) unrelated workloads that mounted the same keys.
### Fix
In JKubeTerm drill down from the ConfigMap to using workloads first (used-by targets), then decide between in-place edit, versioned copy, or per-consumer split.

## practice cm-backup-git
Kinds: ConfigMap
Severity: INFO
Title: Keep ConfigMaps in Git even when created imperatively
Docs:
- https://kubernetes.io/docs/concepts/configuration/configmap/
### Why
`kubectl create configmap --from-file` one-liners vanish from history; the next cluster rebuild or namespace delete loses config nobody can reproduce.
### Fix
Export (`kubectl get -o yaml`, JKubeTerm Save YAML) into the GitOps repo immediately after imperative creation, then manage declaratively from then on.

## practice cm-large-file-split
Kinds: ConfigMap
Severity: INFO
Title: Split large configs by update cadence
Docs:
- https://kubernetes.io/docs/concepts/configuration/configmap/
### Why
One giant ConfigMap mixing stable certificates with daily feature flags forces full rollouts for trivial flag flips and risks touching TLS material accidentally.
### Fix
Split into stable (certs, schemas) and volatile (flags, templates) ConfigMaps with separate rollout policies. Version the stable one, checksum the volatile one.

## practice cm-annotation-churn
Kinds: ConfigMap
Severity: INFO
Title: Don't churn annotations that trigger rollouts
Docs:
- https://kubernetes.io/docs/concepts/overview/working-with-objects/annotations/
### Why
Checksum annotations that include volatile fields (timestamps, build IDs) roll Pods on every pipeline run even when config is identical, wasting capacity and risking incidents.
### Fix
Hash only the config content, not metadata; exclude timestamps from checksummed data. Verify rollout triggers only on real content diffs.

## practice job-ttl-cleanup
Kinds: Job
Severity: INFO
Title: Auto-clean finished Jobs with ttlSecondsAfterFinished
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/job/#clean-up-finished-jobs-automatically
### Why
Completed Jobs and their Pods accumulate forever, cluttering listings, slowing API queries and confusing drill-down with hundreds of stale entries.
### Fix
Set `ttlSecondsAfterFinished` (e.g. 3600 for debugging window, 300 for routine) on every Job and CronJob template. Keep failed Jobs longer than successful ones.

## practice job-deadline
Kinds: Job
Severity: WARN
Title: Bound every Job with activeDeadlineSeconds
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/job/#job-termination-and-cleanup
### Why
A hung migration holding a lock blocks all subsequent deploys indefinitely while looking merely slow, and retries pile more hung copies on top.
### Fix
Set `activeDeadlineSeconds` matching the worst sane runtime plus margin; alert on DeadlineExceeded. Pair with backoffLimit so failures surface fast.

## practice job-parallelism-indexed
Kinds: Job
Severity: INFO
Title: Parallelize with indexed completion mode
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/job/#completion-mode
### Why
Non-indexed parallel Jobs duplicate or skip work items because workers cannot tell which shard is theirs, causing double-processing or gaps in batch pipelines.
### Fix
Use `completionMode: Indexed` with `completions: N` so each Pod gets `JOB_COMPLETION_INDEX`; shard deterministically by index. Verify index env propagation in Pod logs.

## practice job-pod-failure-policy
Kinds: Job
Severity: INFO
Title: Fail fast on fatal errors with podFailurePolicy
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/job/#pod-failure-policy
### Why
Backoff retries treat OutOfMemory and config errors the same as transient network blips, burning hours retrying jobs that can never succeed.
### Fix
Add `podFailurePolicy` rules failing fast on OOMKilled, InvalidImageName and config exits while retrying only transient codes. Available on recent clusters — check server version first.

## practice job-serviceaccount-least
Kinds: Job
Severity: WARN
Title: Run Jobs with least-privilege ServiceAccounts
Docs:
- https://kubernetes.io/docs/tasks/configure-pod-container/configure-service-account/
### Why
Migrations and batch Jobs often run as default or admin accounts, so a compromised batch image inherits full namespace (or cluster) rights for the Job's lifetime.
### Fix
Create dedicated ServiceAccounts per Job class with only the verbs and resources the task needs; set automount false where the Job never calls the API.

## practice job-notify-completion
Kinds: Job
Severity: INFO
Title: Notify on Job completion and failure, not just failure
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/job/
### Why
Silent success means nobody notices when a nightly Job stops being scheduled at all — success alerts missing is itself the signal, but only if success normally alerts.
### Fix
Emit completion events to chat/metrics for critical Jobs (success and failure); alert on absence of success within the expected window, not only on failures.

## practice job-idempotency
Kinds: Job
Severity: WARN
Title: Make every Job idempotent and re-runnable
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/job/
### Why
Retries, duplicate CronJob ticks and operator replays run the same Job twice; non-idempotent migrations double-apply, double-charge or corrupt data.
### Fix
Design tasks as safe to re-run (upserts, guarded migrations, deduplicated side effects); test by running the Job twice against staging and diffing results.

## practice job-resource-quotas
Kinds: Job
Severity: INFO
Title: Size Job resources for bursts, quota the namespace
Docs:
- https://kubernetes.io/docs/concepts/policy/resource-quotas/
### Why
Parallel Jobs burst to hundreds of Pods and exhaust namespace quotas or node capacity, starving serving workloads sharing the cluster.
### Fix
Set per-Job requests/limits plus namespace ResourceQuotas; schedule heavy batches in off-peak windows or dedicated node pools with taints.

## practice job-log-retention
Kinds: Job
Severity: INFO
Title: Ship Job logs before TTL deletes them
Docs:
- https://kubernetes.io/docs/concepts/cluster-administration/logging/
### Why
TTL cleanup deletes the only copy of migration output exactly when auditors or incident review ask what the Job did last Tuesday.
### Fix
Ship Job Pod logs to centralized logging (or object storage) as part of the Job pipeline; keep `kubectl logs job/name` working only as a short-term convenience.

## practice job-manual-trigger
Kinds: Job
Severity: INFO
Title: Keep a manual Job trigger for every CronJob
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/cron-jobs/
### Why
CronJobs cannot be run ad-hoc without crafting manifests under pressure, so backfills and incident replays start with YAML archaeology.
### Fix
Document `kubectl create job --from=cronjob/name manual-run` (or an Argo Workflow equivalent) in the runbook; test the manual path quarterly.

## practice cron-timezone
Kinds: CronJob
Severity: WARN
Title: Pin timeZone explicitly on every CronJob
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/cron-jobs/#time-zones
### Why
Controller-manager timezone upgrades or node moves silently shift schedules by hours; daylight-saving transitions double-fire or skip business-critical batches.
### Fix
Set `spec.timeZone` (IANA name, e.g. Europe/Moscow) on every CronJob on 1.27+ clusters; verify next-run math across a DST boundary in staging.

## practice cron-deadlines
Kinds: CronJob
Severity: WARN
Title: Set startingDeadlineSeconds on every CronJob
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/cron-jobs/
### Why
Without a deadline a missed schedule (controller down, clock skew) silently never runs, and nobody notices the report that never arrived.
### Fix
Set `startingDeadlineSeconds` (e.g. 200) so missed starts count as failures and alert; pair with failedJobsHistoryLimit high enough to investigate.

## practice cron-concurrency
Kinds: CronJob
Severity: WARN
Title: Choose concurrencyPolicy deliberately, default is Allow
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/cron-jobs/
### Why
Allow overlaps long-running batches (double writes, lock fights) while Forbid silently skips ticks that look like successes in dashboards.
### Fix
Use Forbid for non-overlappable work, Replace for latest-wins polling; document the choice and alert on skipped (Forbid) or replaced ticks.

## practice cron-history-limits
Kinds: CronJob
Severity: INFO
Title: Keep enough Job history to debug, not to clutter
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/cron-jobs/
### Why
Defaults (3 success, 1 failure) delete the evidence before morning standup investigates an overnight failure, while unlimited history chokes listings.
### Fix
Set `successfulJobsHistoryLimit: 3-5` and `failedJobsHistoryLimit: 5-10` per criticality; export failure logs centrally regardless of limits.

## practice cron-backfill-safety
Kinds: CronJob
Severity: INFO
Title: Design CronJobs safe to backfill and replay
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/cron-jobs/
### Why
Catch-up runs after downtime replay weeks of ticks at once with overlapping writes, and manual replays for backfills corrupt non-idempotent pipelines.
### Fix
Make handlers idempotent with date-partitioned outputs; test backfill of N missed ticks in staging; document the manual trigger command.

## practice cron-monitor-missed
Kinds: CronJob
Severity: WARN
Title: Alert on missed and failed CronJob ticks
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/cron-jobs/
### Why
CronJob status shows last schedule time but nobody watches it; a suspended or broken CronJob fails by silence, discovered only when stakeholders complain.
### Fix
Alert on `kube_cronjob_status_last_successful_time` staleness and on failed Jobs per CronJob; dashboard next-schedule vs last-success gap.

## practice cron-job-template-review
Kinds: CronJob
Severity: INFO
Title: Review the embedded Job template as a real Job
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/cron-jobs/
### Why
CronJob templates skip Job-level review (backoff, deadline, restartPolicy) because "it's just a schedule wrapper", shipping broken Jobs that fail only at 3 AM.
### Fix
Apply the same Job checklist (backoffLimit, activeDeadlineSeconds, restartPolicy, resources, idempotency) to every jobTemplate; render and dry-run the template standalone.

## practice cron-suspend-discipline
Kinds: CronJob
Severity: INFO
Title: Treat suspend as a change-managed flag
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/cron-jobs/
### Why
Someone suspends a noisy CronJob "temporarily" and it stays suspended for months; resuming later fires a surprise backlog with production impact.
### Fix
Suspend via GitOps PR with expiry date and owner annotation, never imperatively; alert on any CronJob suspended longer than 24h.

## practice cron-resource-windows
Kinds: CronJob
Severity: INFO
Title: Stagger CronJobs out of peak windows
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/cron-jobs/
### Why
Ten heavy batches all at minute zero saturate nodes and storage simultaneously, causing cascading Pending and timeouts that look like an outage.
### Fix
Spread schedules across the hour (use hash-based minutes per job), set resource requests honestly, and prefer off-peak windows for heavy batch work.

## practice cron-manual-runbook
Kinds: CronJob
Severity: INFO
Title: Document manual runs and dry-runs for every CronJob
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/cron-jobs/
### Why
Incident response stalls when the only person knowing the manual trigger is asleep and the manifest needs reconstructing from memory.
### Fix
Keep a one-command manual run plus dry-run variant per CronJob in the runbook; link it from the CronJob annotations for discoverability in JKubeTerm.

## practice ingress-path-types
Kinds: Ingress
Severity: WARN
Title: Choose pathType deliberately: Prefix vs Exact vs ImplementationSpecific
Docs:
- https://kubernetes.io/docs/concepts/services-networking/ingress/#path-types
### Why
Prefix `/api` also matches `/apiv2` and `/apiary` by element rules, while ImplementationSpecific behaves differently per controller — both cause traffic to land on wrong backends after innocent-looking edits.
### Fix
Prefer Explicit Exact for precise routes and Prefix with trailing-slash discipline (`/api/`); avoid ImplementationSpecific unless the controller's regex semantics are pinned and documented.

## practice ingress-rewrite-safety
Kinds: Ingress
Severity: INFO
Title: Audit rewrite-target annotations before copying examples
Docs:
- https://kubernetes.io/docs/concepts/services-networking/ingress/
### Why
Blog-copy rewrite rules silently strip or duplicate path prefixes, so apps receive paths they never handle, producing 404 storms that look like app bugs.
### Fix
Test rewrites with curl against staging for every path variant; prefer app-native base-path config over annotation rewriting where possible.

## practice ingress-timeout-tuning
Kinds: Ingress
Severity: INFO
Title: Tune proxy timeouts to backend reality
Docs:
- https://kubernetes.io/docs/concepts/services-networking/ingress/
### Why
Default 60s proxy timeouts kill long uploads, exports and SSE streams exactly at the minute mark, with errors attributed to the app instead of the edge.
### Fix
Set proxy-connect/read/send timeouts per Ingress (annotations or controller ConfigMap) matching the slowest legitimate backend plus margin; document per-route exceptions.

## practice ingress-body-size
Kinds: Ingress
Severity: WARN
Title: Set explicit client body size limits
Docs:
- https://kubernetes.io/docs/concepts/services-networking/ingress/
### Why
Default 1MB nginx limits reject legitimate uploads with cryptic 413s, while unlimited bodies invite disk-fill DoS through the front door.
### Fix
Set `proxy-body-size` per route to the real maximum upload plus margin; keep a tight global default and loosen only documented upload paths.

## practice ingress-rate-limit
Kinds: Ingress
Severity: WARN
Title: Rate-limit public Ingresses at the edge
Docs:
- https://kubernetes.io/docs/concepts/services-networking/ingress/
### Why
 unauthenticated public endpoints without rate limits are scraped, brute-forced and DDoSed for free; app-level limits kick in too late and per-Pod.
### Fix
Enable controller rate limiting (connections plus requests per second) on public Ingresses with allowlists for health checks and office ranges; alert on limit trips.

## practice ingress-cors-explicit
Kinds: Ingress
Severity: WARN
Title: Define CORS explicitly, never wildcard with credentials
Docs:
- https://kubernetes.io/docs/concepts/services-networking/ingress/
### Why
Wildcard `Access-Control-Allow-Origin: *` combined with credentials leaks authenticated responses to any site, while missing CORS breaks legitimate frontends opaquely.
### Fix
Enumerate allowed origins, methods and headers per Ingress; never combine `*` with allow-credentials. Test preflight (OPTIONS) in CI, not just GET.

## practice ingress-health-endpoint
Kinds: Ingress
Severity: INFO
Title: Expose a dedicated edge health endpoint
Docs:
- https://kubernetes.io/docs/concepts/services-networking/ingress/
### Why
Cloud LBs probing `/` hit app logic, auth and databases, flapping on unrelated failures and coupling edge health to deep dependencies.
### Fix
Serve a cheap `/healthz` at the edge (controller default backend or tiny Service) that checks only edge readiness; point LB and monitoring there.

## practice ingress-default-backend
Kinds: Ingress
Severity: INFO
Title: Brand the default backend instead of 404 soup
Docs:
- https://kubernetes.io/docs/concepts/services-networking/ingress/#default-backend
### Why
Unmatched hosts fall through to controller default 404 pages that leak infrastructure details and confuse users with no correlation IDs.
### Fix
Deploy a branded default backend returning structured errors with request IDs; alert on its traffic share spiking (typo'd hosts or scan waves).

## practice ingress-waf-annotations
Kinds: Ingress
Severity: INFO
Title: Enable WAF and ModSecurity rules on public Ingresses
Docs:
- https://kubernetes.io/docs/concepts/services-networking/ingress/
### Why
Application CVEs (SQLi, RCE patterns) reach Pods unfiltered when the edge passes everything through, and patching apps lags exploit publication by weeks.
### Fix
Enable the controller's WAF/ModSecurity with OWASP core rules in detection-then-block mode per public Ingress; tune false positives per app before enforcing.

## practice ingress-canary-weights
Kinds: Ingress
Severity: INFO
Title: Canary with weighted Ingresses before full rollout
Docs:
- https://kubernetes.io/docs/concepts/services-networking/ingress/
### Why
All-or-nothing Ingress switches turn every deploy into a coin flip for 100% of traffic; rollback still impacts everyone who already saw the bad version.
### Fix
Use canary annotations (canary weight 5-10%) or Gateway API weights for new backends; promote by weight steps with error-budget gates between steps.

## practice ingress-sticky-sessions
Kinds: Ingress
Severity: INFO
Title: Terminate stickiness at Ingress with cookie affinity
Docs:
- https://kubernetes.io/docs/concepts/services-networking/ingress/
### Why
Stateful apps behind round-robin Ingress lose sessions on every scale event, while Service-level ClientIP affinity is coarse and breaks behind NAT.
### Fix
Enable Ingress cookie affinity (nginx affinity annotations) with explicit expiry for the stateful paths only; keep the rest stateless and affinity-free.

## practice ingress-http2-grpc
Kinds: Ingress
Severity: INFO
Title: Enable HTTP/2 and gRPC explicitly at the edge
Docs:
- https://kubernetes.io/docs/concepts/services-networking/ingress/
### Why
gRPC behind HTTP/1-only Ingress fails with opaque protocol errors, and HTTP/2 multiplexing gains stay unrealized when the edge negotiates down silently.
### Fix
Enable SSL redirect plus HTTP/2 on the controller; for gRPC set backend-protocol annotations and test with grpcurl through the Ingress, not direct to Service.

## practice ingress-ssl-redirect
Kinds: Ingress
Severity: WARN
Title: Force HTTPS redirect on every public Ingress
Docs:
- https://kubernetes.io/docs/concepts/services-networking/ingress/#tls
### Why
Serving HTTP and HTTPS side by side lets clients, webhooks and health checks drift onto plaintext, leaking tokens that TLS elsewhere was meant to protect.
### Fix
Enable force-ssl-redirect (with HSTS preload for owned domains) on all public Ingresses; exempt only ACME challenge paths handled by cert-manager solvers.

## practice ingress-cert-rotation
Kinds: Ingress
Severity: WARN
Title: Monitor certificate expiry and rotation, not just issuance
Docs:
- https://cert-manager.io/docs/
- https://kubernetes.io/docs/concepts/services-networking/ingress/#tls
### Why
Issuance succeeding once means nothing: failed renewals surface as midnight expiries months later, and staging-issuer leftovers serve untrusted certs silently.
### Fix
Alert on cert-manager Certificate Ready=False and on TLS secret expiry under 21 days; dashboard per-Ingress cert age; never ship staging issuers to prod Ingresses.

## practice ingress-host-collision
Kinds: Ingress
Severity: WARN
Title: Prevent host collisions across namespaces
Docs:
- https://kubernetes.io/docs/concepts/services-networking/ingress/
### Why
Two Ingresses claiming the same host in different namespaces race: the winner depends on controller merge order, and deploys flap traffic between backends.
### Fix
Govern hosts centrally (one owner per host via policy or Gateway listeners); validate uniqueness in CI across all namespaces before applying.

## practice ingress-allowlist-admin
Kinds: Ingress
Severity: WARN
Title: IP-allowlist admin paths at the edge
Docs:
- https://kubernetes.io/docs/concepts/services-networking/ingress/
### Why
Admin UIs (ArgoCD, Grafana, dashboards) reachable from anywhere are brute-forced within hours of exposure, regardless of password strength.
### Fix
Allowlist office/VPN CIDRs on admin Ingress paths (or separate admin Ingresses); keep public paths minimal and authenticated. Prefer private Ingress classes for tooling.

## practice ingress-access-logs
Kinds: Ingress
Severity: INFO
Title: Ship Ingress access logs with request IDs
Docs:
- https://kubernetes.io/docs/concepts/services-networking/ingress/
### Why
Without edge access logs every 502 is a mystery spanning client, edge, Service and app; correlating across four log systems by timestamp alone fails under load.
### Fix
Enable controller access logs with request IDs propagated to backends; retain hot searchable window plus cold archive; dashboard 5xx by Ingress, host and path.

## practice ingress-external-dns
Kinds: Ingress
Severity: INFO
Title: Manage DNS records with ExternalDNS, not by hand
Docs:
- https://kubernetes.io/docs/concepts/services-networking/ingress/
- https://github.com/kubernetes-sigs/external-dns
### Why
Manual DNS for every Ingress host drifts: deleted Ingresses leave stale records pointing at recycled LBs, and new hosts wait on ticket queues.
### Fix
Deploy ExternalDNS syncing Ingress hosts to the DNS provider with TXT ownership records; restrict to managed zones and review planned changes in dry-run mode.

## practice ingress-gateway-migration
Kinds: Ingress
Severity: INFO
Title: Plan migration to Gateway API for complex routing
Docs:
- https://gateway-api.sigs.k8s.io/
### Why
Ingress annotations hit expressiveness walls (header matching, traffic splitting, cross-namespace) that force ever more controller-specific hacks.
### Fix
For new complex routing adopt Gateway API (Gateway + HTTPRoute) alongside existing Ingresses; migrate route by route with traffic mirroring before cutover.

## practice ingress-backend-protocol
Kinds: Ingress
Severity: INFO
Title: Declare backend protocols explicitly per Service
Docs:
- https://kubernetes.io/docs/concepts/services-networking/ingress/
### Why
Guessed protocols (HTTP vs HTTPS vs gRPC vs FastCGI) cause TLS-to-plaintext mismatches and protocol downgrade errors that surface as random 502s per backend.
### Fix
Annotate backend-protocol per Ingress/Service pair matching what the Pod actually serves; verify with direct-to-Pod protocol checks before blaming the mesh.

## practice pvc-capacity-planning
Kinds: PersistentVolumeClaim
Severity: WARN
Title: Request realistic storage with growth headroom
Docs:
- https://kubernetes.io/docs/concepts/storage/persistent-volumes/
### Why
Undersized PVCs fill up silently until apps crash on ENOSPC, and many StorageClasses forbid or complicate online expansion, turning a small misestimate into a migration.
### Fix
Size to 12-month growth plus 30% headroom; prefer allowVolumeExpansion StorageClasses; alert at 70/85/95% with runbooked expansion steps tested in staging.

## practice pvc-expansion-testing
Kinds: PersistentVolumeClaim
Severity: INFO
Title: Test volume expansion before you need it
Docs:
- https://kubernetes.io/docs/concepts/storage/persistent-volumes/#expanding-persistent-volumes-claims
### Why
Expansion fails exactly during incidents: filesystem resize needs Pod restarts, some drivers allow only offline expansion, and quotas block the new size.
### Fix
Rehearse expansion per StorageClass in staging (edit requests.storage, restart Pods, verify filesystem); document online vs offline behavior per driver in the runbook.

## practice pvc-access-modes-audit
Kinds: PersistentVolumeClaim
Severity: INFO
Title: Audit accessModes against real mount counts
Docs:
- https://kubernetes.io/docs/concepts/storage/persistent-volumes/#access-modes
### Why
RWO claims mounted by two Deployments (or a DaemonSet) deadlock the second consumer in Multi-Attach with no clear error at Apply time.
### Fix
List actual mount counts per PVC quarterly; migrate shared-read needs to RWX/ROX drivers; enforce Recreate strategy for RWO single-writers in review.

## practice pvc-retention-backup
Kinds: PersistentVolumeClaim
Severity: WARN
Title: Back up PVC data independently of retention
Docs:
- https://kubernetes.io/docs/concepts/storage/volume-snapshots/
### Why
Retention policies and namespace deletes wipe PVCs by design; without separate backups the "working as intended" deletion is also unrecoverable data loss.
### Fix
Snapshot or Velero-backup every PVC holding non-reproducible data on a schedule; test restores to scratch namespaces; alert on backup age, not just backup success.

## practice pvc-iops-class
Kinds: PersistentVolumeClaim
Severity: INFO
Title: Match StorageClass performance to workload IOPS
Docs:
- https://kubernetes.io/docs/concepts/storage/storage-classes/
### Why
Databases on throughput-optimized or HDD-backed classes suffer tail-latency spikes misdiagnosed as app issues, while premium SSD for logs wastes budget.
### Fix
Define classes per tier (fast-ssd for DBs, standard for general, cold for archives) with documented IOPS; benchmark per class in the lab and label PVCs accordingly.

## practice pvc-fs-type
Kinds: PersistentVolumeClaim
Severity: INFO
Title: Set fstype explicitly per StorageClass
Docs:
- https://kubernetes.io/docs/concepts/storage/storage-classes/
### Why
Default filesystems vary by provisioner (ext4 vs xfs), and apps sensitive to reflink, discard or journaling behave differently with no manifest hint why.
### Fix
Pin `csi.storage.k8s.io/fstype` per class (xfs for databases, ext4 general); document the choice and mount options alongside the class definition.

## practice pvc-mount-options
Kinds: PersistentVolumeClaim
Severity: INFO
Title: Tune mountOptions (noatime, discard) per workload
Docs:
- https://kubernetes.io/docs/concepts/storage/persistent-volumes/
### Why
Default relatime writes on every read and missing discard leaves SSD garbage uncollected, costing IOPS and device lifespan on busy volumes.
### Fix
Set noatime for read-heavy, discard for SSD-backed classes; verify via /proc/mounts inside Pods after provisioning.

## practice pvc-quota-per-team
Kinds: PersistentVolumeClaim, Namespace
Severity: INFO
Title: Quota PVC counts and total storage per namespace
Docs:
- https://kubernetes.io/docs/concepts/policy/resource-quotas/
### Why
One team's unbounded PVC creation exhausts provisioner capacity or cloud quotas, blocking every other team's deploys with cryptic provision errors.
### Fix
Add ResourceQuota (persistentvolumeclaims count + requests.storage) per team namespace; alert at 80% with expansion request workflow.

## practice pvc-delete-protection
Kinds: PersistentVolumeClaim
Severity: WARN
Title: Protect production PVCs from accidental deletion
Docs:
- https://kubernetes.io/docs/concepts/storage/persistent-volumes/
### Why
`kubectl delete ns` or a GitOps prune removes PVCs as casually as ConfigMaps, and Retain policies still strand data in hard-to-reattach states.
### Fix
Guard prod PVCs with finalizers or policy agents (Kyverno/Ops), require manual confirmation runbooks for deletes, and snapshot before any planned removal.

## practice pvc-data-source
Kinds: PersistentVolumeClaim
Severity: INFO
Title: Clone and restore via dataSource, not manual copies
Docs:
- https://kubernetes.io/docs/concepts/storage/volume-pvc-datasource/
### Why
Manual `kubectl cp` restores are slow, lossy on permissions, and unrepeatable; snapshot-clone semantics exist precisely for fast consistent copies.
### Fix
Use `dataSource` (PVC or VolumeSnapshot) for clones and restores; document source retention separately from clone lifecycle.

## practice pvc-topology-labels
Kinds: PersistentVolumeClaim
Severity: INFO
Title: Constrain volumes with allowedTopologies
Docs:
- https://kubernetes.io/docs/concepts/storage/storage-classes/#allowed-topologies
### Why
Volumes provisioned in zone A with Pods scheduled in zone B either fail to attach or incur cross-zone latency and egress on every I/O.
### Fix
Set allowedTopologies per StorageClass matching workload zones; combine with Pod topologySpreadConstraints so compute follows storage.

## practice pvc-fsgroup-policy
Kinds: PersistentVolumeClaim
Severity: INFO
Title: Set fsGroup policy for shared writable volumes
Docs:
- https://kubernetes.io/docs/concepts/storage/storage-classes/
### Why
Multi-container Pods with different UIDs hit permission denied on shared volumes when fsGroup is unset, producing app errors that look like storage failures.
### Fix
Set `fsGroupPolicy: File` (or ReadWriteOnceWithFSType) plus Pod `fsGroup`; verify group ownership inside Pods after first mount.

## practice pvc-resize-quota-sync
Kinds: PersistentVolumeClaim
Severity: INFO
Title: Keep quotas in sync when expanding volumes
Docs:
- https://kubernetes.io/docs/concepts/policy/resource-quotas/
### Why
Expansion past quota fails with Forbidden errors misread as provisioner bugs, stalling incident response while disks fill.
### Fix
Raise the namespace storage quota before or with the PVC expansion request; automate quota bump in the expansion runbook.

## practice pvc-empty-storage-class
Kinds: PersistentVolumeClaim
Severity: WARN
Title: Never leave storageClassName empty unintentionally
Docs:
- https://kubernetes.io/docs/concepts/storage/persistent-volumes/#class-1
### Why
Empty storageClassName binds only to no-class PVs (or the default, depending on admission), producing Pending PVCs whose cause hides in admission plugin behavior.
### Fix
Always set storageClassName explicitly (or `""` deliberately for no-class binding with a comment); lint manifests for missing class fields.

## practice pvc-selector-binding
Kinds: PersistentVolumeClaim
Severity: INFO
Title: Bind to pre-provisioned PVs with selectors deliberately
Docs:
- https://kubernetes.io/docs/concepts/storage/persistent-volumes/
### Why
Label selectors on claims that almost match bind the wrong PV (wrong zone, wrong performance tier) with no warning at bind time.
### Fix
Use selectors only for hand-crafted PV pools with documented labels; otherwise rely on StorageClass dynamic provisioning and drop selectors.

## practice pvc-app-managed-cleanup
Kinds: PersistentVolumeClaim
Severity: INFO
Title: Let apps own scratch PVC lifecycle via owners
Docs:
- https://kubernetes.io/docs/concepts/overview/working-with-objects/owners-dependents/
### Why
Scratch PVCs outlive the Jobs that created them without ownerReferences, accumulating billable disks nobody dares delete.
### Fix
Set ownerReferences from scratch PVCs to their Jobs (or use TTL patterns); audit orphan PVCs monthly with creation-age reports.

## practice pvc-encryption-at-rest
Kinds: PersistentVolumeClaim
Severity: WARN
Title: Encrypt storage for sensitive data classes
Docs:
- https://kubernetes.io/docs/tasks/administer-cluster/encrypt-data/
- https://kubernetes.io/docs/concepts/storage/storage-classes/
### Why
Unencrypted volumes expose data to anyone with snapshot, host or cloud-console access, violating compliance for PII, secrets-adjacent and financial data.
### Fix
Use encrypted StorageClasses (cloud KMS or LUKS) for sensitive namespaces; verify encryption status per PV and rotate keys per policy.

## practice pvc-performance-baseline
Kinds: PersistentVolumeClaim
Severity: INFO
Title: Baseline volume performance before blaming the app
Docs:
- https://kubernetes.io/docs/concepts/storage/persistent-volumes/
### Why
Slow queries blamed on code are often slow disks, but without a baseline nobody can prove whether the volume or the query regressed.
### Fix
fio-baseline every StorageClass at provisioning and quarterly; record expected IOPS/latency in the class docs; compare incident volumes against baseline first.

## practice pvc-snapshot-schedule
Kinds: PersistentVolumeClaim
Severity: WARN
Title: Snapshot stateful PVCs on a schedule with retention
Docs:
- https://kubernetes.io/docs/concepts/storage/volume-snapshots/
### Why
Ad-hoc snapshots before risky changes miss the incident that happens on a quiet Tuesday; no schedule means no recovery point.
### Fix
Automate snapshot schedules (hourly/daily per criticality) with retention windows; monitor snapshot age and size growth, not just job success.

## practice pvc-multi-attach-guard
Kinds: PersistentVolumeClaim
Severity: WARN
Title: Guard against multi-attach with topology and strategy
Docs:
- https://kubernetes.io/docs/concepts/storage/persistent-volumes/#access-modes
### Why
Rolling updates briefly run two Pods against one RWO volume during handover; without Recreate strategy the new Pod pends and the rollout stalls mid-way.
### Fix
Pair RWO claims with Recreate strategy (or RWOP where supported); alert on Multi-Attach errors as rollout-blockers, not storage bugs.

## practice ev-severity-triage
Kinds: Event
Severity: INFO
Title: Triage by type Normal vs Warning, then by count
Docs:
- https://kubernetes.io/docs/reference/kubectl/generated/kubectl_events/
### Why
Treating every Event equally buries the single Warning with count 200 among hundreds of Normal Scheduled/Pulled notices that mean success.
### Fix
Filter Warning first, sort by count then lastTimestamp; Normal events are context, Warning events are leads. In JKubeTerm open Events, filter by object, scan Warnings top-down.

## practice ev-involved-filter
Kinds: Event
Severity: INFO
Title: Always scope Events to the involved object
Docs:
- https://kubernetes.io/docs/reference/kubectl/generated/kubectl_events/
### Why
Namespace-wide event streams mix dozens of objects; the BackOff explaining your outage sits ten screens below unrelated Scheduling successes.
### Fix
Filter `field-selector involvedObject.name=X` (JKubeTerm drill from any object lands pre-filtered); correlate the object's event timeline with its Pod logs.

## practice ev-no-alert-fatigue
Kinds: Event
Severity: WARN
Title: Alert on Warning Event patterns, never on raw streams
Docs:
- https://kubernetes.io/docs/reference/kubectl/generated/kubectl_events/
### Why
Raw Event alerting fires hundreds of alerts per rollout (every Pulled/Started), training the team to ignore the channel that later carries the real outage.
### Fix
Alert on rate-of-change of specific reasons (BackOff, FailedMount, FailedScheduling) via event exporters (eventrouter); never page on Normal events.

## practice ev-retention-window
Kinds: Event
Severity: INFO
Title: Ship Events centrally; etcd keeps one hour only
Docs:
- https://kubernetes.io/docs/reference/kubectl/generated/kubectl_events/
### Why
The API prunes Events after about an hour, so postmortems for overnight incidents find an empty timeline and rely on memories instead of evidence.
### Fix
Deploy an event exporter (eventrouter to Loki/ES) with at least 30-day retention; include involvedObject, reason, count and first/last timestamps in the schema.

## practice ev-count-means-loop
Kinds: Event
Severity: WARN
Title: Read count as a loop detector
Docs:
- https://kubernetes.io/docs/reference/kubectl/generated/kubectl_events/
### Why
Count 500 on BackOff means the same failure retried for hours (fix the cause), while count 2 on FailedScheduling means a transient blip; teams treat both as "some errors".
### Fix
Prioritize high-count Warnings as systemic loops; low-count fresh Warnings as new regressions. Graph count growth rate to distinguish stuck loops from flapping.

## practice ev-first-last-times
Kinds: Event
Severity: INFO
Title: Correlate firstTimestamp with deploys and changes
Docs:
- https://kubernetes.io/docs/reference/kubectl/generated/kubectl_events/
### Why
An error without a start time cannot be blamed on the 14:03 deploy or the 14:05 config edit; teams guess instead of correlating.
### Fix
Always note firstTimestamp vs deploy/config timestamps; lastTimestamp freshness tells whether the issue is ongoing or stale. JKubeTerm shows both in the Object view.

## practice ev-duplicate-suppression
Kinds: Event
Severity: INFO
Title: Understand aggregation before assuming silence
Docs:
- https://kubernetes.io/docs/reference/kubectl/generated/kubectl_events/
### Why
The API aggregates identical events (count++, updated lastTimestamp), so a quiet stream can hide an active loop that merely stopped emitting new rows.
### Fix
Watch count and lastTimestamp deltas, not row counts; alert on aggregation growth for Warning reasons even when no new rows appear.

## practice ev-failed-scheduling
Kinds: Event
Severity: WARN
Title: FailedScheduling always names the missing resource
Docs:
- https://kubernetes.io/docs/concepts/scheduling-eviction/
### Why
Pending Pods with no further investigation sit for days because "scheduling will sort it out"; the Event already states exactly which resource, taint or affinity blocks placement.
### Fix
Read the FailedScheduling message fully (insufficient cpu/memory, node selector mismatch, taints); fix capacity, tolerations or requests — never just wait longer.

## practice ev-failed-mount
Kinds: Event
Severity: WARN
Title: FailedMount points at volumes, secrets or images
Docs:
- https://kubernetes.io/docs/concepts/storage/volumes/
### Why
Mount failures (missing Secret key, unformatted disk, bad subPath) block container start entirely, yet teams debug the app image while the volume never attached.
### Fix
Check the named volume source first (Secret/ConfigMap keys exist? PVC bound? subPath valid?); describe the volume source object before touching the image.

## practice ev-imagepull-timeline
Kinds: Event
Severity: INFO
Title: Reconstruct pull failures from the Event timeline
Docs:
- https://kubernetes.io/docs/concepts/containers/images/
### Why
ImagePullBackOff shows only the latest state; the timeline (Pulling → Failed → BackOff) reveals whether the registry, the tag, the secret or the network is at fault.
### Fix
Read the sequence: auth errors implicate imagePullSecrets, not-found implicates tags, timeouts implicate network/registry. Fix that layer, not the Pod spec randomly.

## practice ev-probe-failures
Kinds: Event
Severity: WARN
Title: Unhealthy probe Events demand probe redesign, not threshold inflation
Docs:
- https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/
### Why
Raising failureThreshold to silence Unhealthy Events masks genuinely dead apps behind green probes, converting fast detectable failures into slow mysterious ones.
### Fix
Treat repeated Unhealthy as app or probe-path bugs: fix the endpoint, separate liveness from readiness, add startup probes for slow boots. Tune thresholds last.

## practice ev-oom-events
Kinds: Event
Severity: WARN
Title: OOMKilled in Events means limits work, sizing doesn't
Docs:
- https://kubernetes.io/docs/tasks/configure-pod-container/assign-memory-resource/
### Why
Teams read OOMKilled as "Kubernetes killed my app" and raise limits blindly, when the event actually proves the limit correctly contained a leak or spike.
### Fix
Correlate OOM Events with memory graphs to distinguish leaks (raise-then-fix-code) from spikes (raise limits, add HPA); never remove limits to "fix" OOMs.

## practice ev-evicted-context
Kinds: Event
Severity: WARN
Title: Evicted Pods tell you the node's story, read it
Docs:
- https://kubernetes.io/docs/tasks/administer-cluster/out-of-resource/
### Why
Evicted Events name the pressure (DiskPressure, MemoryPressure) and the victim QoS, but teams reschedule without addressing the node condition, repeating the eviction loop.
### Fix
Note the eviction reason, check node conditions and QoS class, fix capacity or requests before rescheduling; cordon pressured nodes first.

## practice ev-killing-grace
Kinds: Event
Severity: INFO
Title: Killing Events with long grace reveal drain problems
Docs:
- https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/#pod-termination
### Why
Killing Events that linger for minutes indicate missing SIGTERM handlers or too-short grace periods, foreshadowing 502 spikes in the next rollout.
### Fix
Compare Killing duration against terminationGracePeriodSeconds; add preStop hooks and handlers for slow drainers; alert on Killing older than grace plus margin.

## practice ev-owner-chain
Kinds: Event
Severity: INFO
Title: Follow Events up the owner chain
Docs:
- https://kubernetes.io/docs/concepts/overview/working-with-objects/owners-dependents/
### Why
Pod-level Events describe symptoms (BackOff) while the cause (bad template, missing secret) lives on the Deployment or Job; fixing Pods treats symptoms.
### Fix
From a Pod Event drill to the owner (ReplicaSet, Job, Deployment) and read its Events too; fix at the highest level that owns the faulty field.

## practice ev-success-baseline
Kinds: Event
Severity: INFO
Title: Learn the Normal baseline to spot abnormal fast
Docs:
- https://kubernetes.io/docs/reference/kubectl/generated/kubectl_events/
### Why
Without knowing the healthy sequence (Scheduled → Pulling → Pulled → Created → Started) every incident starts from zero pattern-matching instead of instant deviation spotting.
### Fix
Study Normal sequences for your workloads in calm times; during incidents scan for missing steps (no Pulled = registry; no Scheduled = scheduler) before reading messages.

## practice ev-exporter-coverage
Kinds: Event
Severity: INFO
Title: Cover all namespaces with the event exporter
Docs:
- https://kubernetes.io/docs/reference/kubectl/generated/kubectl_events/
### Why
Exporters scoped to default miss kube-system, monitoring and argocd incidents entirely, creating blind spots exactly where platform failures originate.
### Fix
Deploy cluster-scoped export with all-namespaces RBAC; verify coverage by generating a test Warning per namespace quarterly.

## practice ev-note-field
Kinds: Event
Severity: INFO
Title: Read the full message, not just the reason
Docs:
- https://kubernetes.io/docs/reference/kubectl/generated/kubectl_events/
### Why
Reason codes (FailedMount, Unhealthy) categorize; the message names the volume, the path, the HTTP code and the threshold that actually failed. Skimming reasons wastes the detail.
### Fix
Expand truncated messages (JKubeTerm Object view shows full text); copy the exact failing object names into the investigation rather than paraphrasing.

## practice ev-actionable-runbooks
Kinds: Event
Severity: INFO
Title: Link every alerted reason to a runbook
Docs:
- https://kubernetes.io/docs/reference/kubectl/generated/kubectl_events/
### Why
A paged BackOff without a linked runbook starts with search-engine roulette under time pressure, and every responder invents different first steps.
### Fix
Maintain reason → runbook mapping for the top 10 reasons (BackOff, FailedMount, FailedScheduling, Unhealthy, OOMKilled...); link from alerts directly.

## practice ev-stale-sweep
Kinds: Event
Severity: INFO
Title: Sweep stale Warnings after incidents close
Docs:
- https://kubernetes.io/docs/reference/kubectl/generated/kubectl_events/
### Why
Resolved incidents leave Warning Events (and alert silences) behind that desensitize the next on-call to genuinely fresh failures with identical text.
### Fix
Close the loop: verify Warning rates return to baseline post-incident, lift silences, and note resolved counts in the postmortem timeline.

## practice node-capacity-plan
Kinds: Node
Severity: WARN
Title: Plan node capacity with headroom for surge and failure
Docs:
- https://kubernetes.io/docs/concepts/architecture/nodes/
### Why
Nodes at 95% allocatable have no room for rolling-update surge, DaemonSet overhead or a failed peer's Pods, turning routine drains into Pending cascades.
### Fix
Keep allocatable headroom (target under 75% requested on steady state); size node pools for N+1 failure; alert on projected exhaustion 30 days out.

## practice node-os-upgrades
Kinds: Node
Severity: WARN
Title: Upgrade node OS and kubelet in lockstep tested order
Docs:
- https://kubernetes.io/docs/tasks/administer-cluster/kubeadm/kubeadm-upgrade/
### Why
Ad-hoc OS patching skews kubelet, containerd and kernel versions across nodes, producing works-on-node-A-only bugs that defy reproduction.
### Fix
Upgrade via managed node groups or kubeadm in dev → staging → prod waves with version skew checks; pin and record OS image per node pool in Git.

## practice node-containerd-gc
Kinds: Node
Severity: INFO
Title: Tune image and container garbage collection thresholds
Docs:
- https://kubernetes.io/docs/concepts/architecture/garbage-collection/
### Why
Default image GC thresholds fill disks with stale layers on high-churn nodes, triggering DiskPressure evictions of application Pods to protect images nobody needs.
### Fix
Set imageMinimumGCAge and thresholds per node class; monitor image filesystem usage separately from container writable layers; alert before GC storms.

## practice node-kubelet-config
Kinds: Node
Severity: INFO
Title: Manage kubelet config centrally, not per-node flags
Docs:
- https://kubernetes.io/docs/tasks/administer-cluster/kubelet-config-file/
### Why
Hand-tuned kubelet flags drift across nodes (different eviction thresholds, log rotation, authz modes), making behavior node-dependent and upgrades risky.
### Fix
Use kubelet config files versioned in Git (or managed node groups); diff running vs desired config quarterly; never SSH-tune single nodes.

## practice node-cordon-discipline
Kinds: Node
Severity: INFO
Title: Cordon before maintenance, drain with budgets in mind
Docs:
- https://kubernetes.io/docs/tasks/administer-cluster/safely-drain-node/
### Why
Rebooting uncordoned nodes kills Pods without PDB protection or graceful termination, converting planned maintenance into unplanned outages.
### Fix
Always cordon, then drain with --ignore-daemonsets and PDB awareness; watch eviction progress per workload; uncordon only after node Ready plus agent checks.

## practice node-labels-hygiene
Kinds: Node
Severity: INFO
Title: Govern node labels like an API
Docs:
- https://kubernetes.io/docs/concepts/overview/working-with-objects/labels/
### Why
Ad-hoc node labels (`ssd=true`, `gpu2`) proliferate spellings that selectors miss, so workloads land on wrong hardware or nowhere with cryptic affinity errors.
### Fix
Define the allowed label vocabulary (topology, hardware, pool) in docs; validate in CI; deprecate renames with dual-label transition periods.

## practice node-problem-detector
Kinds: Node
Severity: INFO
Title: Run node-problem-detector for kernel and hardware faults
Docs:
- https://github.com/kubernetes/node-problem-detector
### Why
Kernel oopses, disk errors and NTP drift stay invisible to Kubernetes until Pods mysteriously fail; node conditions show only kubelet-level health.
### Fix
Deploy node-problem-detector with log monitors mapping to node conditions plus taints; alert on KernelDeadlock and disk errors; cordon automatically on hardware faults.

## practice node-auto-repair
Kinds: Node
Severity: INFO
Title: Auto-repair or replace unhealthy nodes
Docs:
- https://kubernetes.io/docs/concepts/architecture/nodes/
### Why
Manually nursing NotReady nodes for days leaves workloads degraded while engineers context-switch; humans are slow at the obvious.
### Fix
Enable managed auto-repair (or draino plus autoscaler) with safe drain semantics; alert on repair loops (same node flapping) rather than single repairs.

## practice node-resource-reservations
Kinds: Node
Severity: WARN
Title: Reserve kubelet, system and eviction headroom explicitly
Docs:
- https://kubernetes.io/docs/tasks/administer-cluster/reserve-compute-resources/
### Why
Without kube/system reservations and hard eviction thresholds, application Pods consume 100% of the node and starve kubelet, containerd and the OS into wedging the node.
### Fix
Set kube-reserved, system-reserved and eviction-hard (memory.available, nodefs.available) per node size class; verify via /system.slice accounting, not guesses.

## practice node-ntp-sync
Kinds: Node
Severity: WARN
Title: Keep node clocks synchronized or everything lies
Docs:
- https://kubernetes.io/docs/concepts/architecture/nodes/
### Why
Clock skew breaks TLS validation, token expiry, CronJob timing and log correlation simultaneously, producing multi-symptom mysteries with one root cause.
### Fix
Run chrony/ntpd on every node with monitoring on offset; alert above 500ms; include clock check in node bootstrap validation and post-maintenance checks.

## practice node-kernel-tuning
Kinds: Node
Severity: INFO
Title: Tune sysctls for connection-heavy workloads deliberately
Docs:
- https://kubernetes.io/docs/tasks/administer-cluster/sysctl-cluster/
### Why
Default conntrack, somaxconn and file limits saturate under load-balancer or mesh traffic, dropping connections with kernel-level errors apps never see.
### Fix
Set namespaced sysctls per Pod where possible, node sysctls via kubelet config where required; load-test to find ceilings; document every non-default sysctl with rationale.

## practice node-disk-separation
Kinds: Node
Severity: INFO
Title: Separate OS, container and etcd disks on control plane
Docs:
- https://etcd.io/docs/latest/op-guide/hardware/
### Why
etcd sharing a disk with container image churn suffers fsync latency spikes that destabilize the whole control plane under exactly the load peaks that matter.
### Fix
Give etcd dedicated low-latency disks (SSD/NVMe) on control-plane nodes; separate container storage from OS root; monitor disk latency percentiles, not just usage.

## practice node-seccomp-default
Kinds: Node
Severity: INFO
Title: Enable seccomp default at kubelet level
Docs:
- https://kubernetes.io/docs/tasks/configure-pod-container/security-context/
### Why
Per-Pod seccomp relies on every author remembering; one forgotten manifest widens that Pod's syscall surface permanently.
### Fix
Enable the kubelet seccompDefault (stable on modern clusters) so RuntimeDefault applies unless explicitly overridden; audit overrides quarterly.

## practice node-audit-logging
Kinds: Node
Severity: INFO
Title: Collect kubelet and auth logs per node centrally
Docs:
- https://kubernetes.io/docs/tasks/debug/debug-cluster/audit/
### Why
Node-local kubelet logs vanish on node replacement, taking the evidence for wedge, OOM and mount failures with them.
### Fix
Ship kubelet, containerd and auth logs per node to central storage with node-name indexing; retain through at least one upgrade cycle for comparisons.

## practice node-gpu-taints
Kinds: Node
Severity: INFO
Title: Taint specialized hardware so only intended Pods land
Docs:
- https://kubernetes.io/docs/concepts/scheduling-eviction/taint-and-toleration/
### Why
General workloads scheduled on GPU or high-memory nodes waste expensive capacity and get evicted the moment real GPU jobs arrive.
### Fix
Taint GPU/ARM/spot-special nodes with matching tolerations only on intended workloads; verify with dry-run scheduling before trusting defaults.

## practice node-drain-timeouts
Kinds: Node
Severity: INFO
Title: Bound drain times with pod-eviction timeouts
Docs:
- https://kubernetes.io/docs/tasks/administer-cluster/safely-drain-node/
### Why
A single PDB-blocked or finalizer-stuck Pod halts node maintenance forever while the upgrade window burns, with no escalation path.
### Fix
Set drain timeouts with --timeout plus alerting on stuck evictions; pre-check PDBs and finalizers before maintenance windows; document force-escalation criteria.

## practice node-image-prepull
Kinds: Node
Severity: INFO
Title: Pre-pull critical images to beat cold starts
Docs:
- https://kubernetes.io/docs/concepts/containers/images/
### Why
First schedule on a fresh node pulls gigabytes while users wait, and registry blips during scale-up cascade into Pending storms.
### Fix
Pre-pull critical images via DaemonSet (pause plus app images) or node image cache warming on autoscaler scale-up hooks; monitor pull durations per image.

## practice node-spot-interruption
Kinds: Node
Severity: WARN
Title: Handle spot and preemptible interruptions gracefully
Docs:
- https://kubernetes.io/docs/concepts/scheduling-eviction/node-pressure-eviction/
### Why
Two-minute interruption notices ignored by apps cause hard kills mid-transaction on the cheapest capacity exactly when savings matter most.
### Fix
Handle termination notices (AWS node-termination-handler, PDBs, graceful periods under 120s); run only fault-tolerant workloads on spot; keep on-demand core for stateful paths.

## practice node-version-skew-nodes
Kinds: Node
Severity: WARN
Title: Keep kubelet versions within skew policy of the API
Docs:
- https://kubernetes.io/docs/setup/release/version-skew-policy/
### Why
Kubelets too far behind the API server lose feature gates and hit deprecated API removals, failing in ways that look like app bugs per node.
### Fix
Upgrade kubelets within two minors behind the control plane; block node joins outside skew via admission or automation gates; dashboard skew per node pool.

## practice ns-name-conventions
Kinds: Namespace
Severity: INFO
Title: Name namespaces for team plus environment, never mutable concepts
Docs:
- https://kubernetes.io/docs/concepts/overview/working-with-objects/namespaces/
### Why
Names like `test-final-v2` or `john-dev` become permanent API surface that DNS, RBAC and NetworkPolicies depend on; renaming later means migrating everything.
### Fix
Use `team-env` (`payments-prod`), keep `default` empty of real workloads, reserve `kube-*` prefixes; enforce the pattern with policy agents at creation.

## practice ns-no-default-workloads
Kinds: Namespace
Severity: WARN
Title: Keep the default namespace empty of real workloads
Docs:
- https://kubernetes.io/docs/concepts/overview/working-with-objects/namespaces/
### Why
Everything forgotten lands in default, mixing blast radii, quotas and policies into one ungovernable soup where drill-down and RBAC scoping break down.
### Fix
Deploy nothing real to default (use it for smoke tests only); alert on any Deployment/Service outside known namespaces; default-deny NetworkPolicy there.

## practice ns-rbac-per-team
Kinds: Namespace
Severity: WARN
Title: Scope team RBAC to namespaces, never cluster-wide
Docs:
- https://kubernetes.io/docs/reference/access-authn-authz/rbac/
### Why
ClusterRoleBindings for teams grant accidental control over kube-system, monitoring and other teams' namespaces; one bad apply cascades everywhere.
### Fix
Bind team Roles within their namespaces only; cluster access via break-glass accounts with expiry and audit; review bindings quarterly with `kubectl auth can-i` matrices.

## practice ns-network-default-deny
Kinds: Namespace
Severity: WARN
Title: Default-deny network traffic in every application namespace
Docs:
- https://kubernetes.io/docs/concepts/services-networking/network-policies/
### Why
Flat pod networking lets any compromised Pod reach databases, metadata endpoints and other teams' APIs; breaches spread laterally by default.
### Fix
Apply default-deny Ingress+Egress per namespace (with DNS egress allowance), then whitelist required flows explicitly; test with connectivity probes before enforcing.

## practice ns-delete-protection
Kinds: Namespace
Severity: CRITICAL
Title: Guard production namespaces against deletion and prune
Docs:
- https://kubernetes.io/docs/concepts/overview/working-with-objects/namespaces/
### Why
One `kubectl delete ns` (or GitOps prune of the namespace object) wipes every workload, PVC reference and policy in it within seconds, with no per-object confirmation.
### Fix
Protect prod namespaces with policy agents blocking deletes, require manual runbook confirmation, snapshot before planned removal; never let GitOps prune namespace objects automatically.

## practice ns-terminating-debug
Kinds: Namespace
Severity: INFO
Title: Know how to unstick Terminating namespaces
Docs:
- https://kubernetes.io/docs/concepts/overview/working-with-objects/namespaces/
### Why
Namespaces hang Terminating on stuck finalizers (often deleted controllers' leftovers), blocking redeploys and quota release while teams wait helplessly.
### Fix
Find the blocking finalizer (`kubectl get ns -o yaml`), remove the responsible resource or patch the finalizer list deliberately after snapshotting; fix the controller that left it.

## practice ns-finalizers-hygiene
Kinds: Namespace
Severity: INFO
Title: Audit finalizers that block namespace teardown
Docs:
- https://kubernetes.io/docs/concepts/overview/working-with-objects/finalizers/
### Why
Third-party operators add finalizers that never complete when the operator itself is deleted first, permanently wedging the namespace.
### Fix
List finalizers before deleting operators (delete workloads first, operators last); document required deletion order per operator in runbooks.

## practice ns-cost-allocation
Kinds: Namespace
Severity: INFO
Title: Allocate cost and ownership per namespace
Docs:
- https://kubernetes.io/docs/concepts/overview/working-with-objects/namespaces/
### Why
Shared clusters without cost attribution let runaway namespaces burn budget with no owner paged, and optimization lacks accountability.
### Fix
Label namespaces with team/cost-center, export per-namespace usage (OpenCost or cloud billing), review top consumers monthly with owners present.

## practice ns-env-separation
Kinds: Namespace
Severity: INFO
Title: Separate environments by cluster or strict namespace policy
Docs:
- https://kubernetes.io/docs/concepts/overview/working-with-objects/namespaces/
### Why
Prod and dev sharing namespaces (or loosely separated ones) leak prod credentials into dev Pods and let load tests crush production Services.
### Fix
Prefer separate clusters for prod vs non-prod; if sharing, enforce with quotas, NetworkPolicies, distinct ServiceAccounts and no shared Secrets; test isolation, don't assume it.

## practice ns-pss-enforce
Kinds: Namespace
Severity: WARN
Title: Enforce Pod Security Standards per namespace tier
Docs:
- https://kubernetes.io/docs/concepts/security/pod-security-standards/
### Why
Without enforce labels, privileged Pods land in team namespaces routinely and nobody notices until the audit (or the incident).
### Fix
Label namespaces `pod-security.kubernetes.io/enforce: restricted` (baseline minimum), audit mode first for legacy, exemptions only documented with expiry.

## practice ns-wildcard-tls-shared
Kinds: Namespace
Severity: INFO
Title: Share wildcard TLS via per-namespace copies, not cross-mounts
Docs:
- https://kubernetes.io/docs/concepts/services-networking/ingress/#tls
### Why
Cross-namespace secret mounts break the namespace boundary and complicate rotation; one rotation miss leaves some Ingresses serving expired certs.
### Fix
Replicate wildcard certs per namespace via GitOps or reflector with explicit RBAC; rotate centrally and verify every copy's expiry in one dashboard.

## practice ns-drill-inventory
Kinds: Namespace
Severity: INFO
Title: Inventory namespaces before cluster operations
Docs:
- https://kubernetes.io/docs/concepts/overview/working-with-objects/namespaces/
### Why
Upgrades and drains planned against "our three namespaces" miss the shadow IT namespaces created months ago, whose workloads break unannounced.
### Fix
List all namespaces (including Terminating) before every maintenance; tag system vs team vs ephemeral; confirm owners for any unknown namespace first.

## practice ns-ttl-ephemeral
Kinds: Namespace
Severity: INFO
Title: Expire ephemeral namespaces automatically
Docs:
- https://kubernetes.io/docs/concepts/overview/working-with-objects/namespaces/
### Why
PR preview and experiment namespaces accumulate forever, consuming quotas, IPs and attention long after their branches merged.
### Fix
TTL controllers or nightly jobs delete namespaces past their annotated expiry; require `expires-at` annotation on non-prod namespaces; notify owners before deletion.

## practice ns-monitoring-per-ns
Kinds: Namespace
Severity: INFO
Title: Dashboard health per namespace, not just cluster-wide
Docs:
- https://kubernetes.io/docs/concepts/cluster-administration/monitoring/
### Why
Cluster-green dashboards hide a fully red team namespace; owners learn about their outage from users instead of their own dashboard.
### Fix
Provision per-namespace Grafana folders (deploy health, error budget, quota usage); page team channels on their namespace signals, not the global average.

## practice ns-backup-scope
Kinds: Namespace
Severity: WARN
Title: Scope backups per namespace with tested restores
Docs:
- https://velero.io/docs/latest/
### Why
Cluster-wide backup dumps look complete but restore the wrong namespace versions together, mixing prod data with staging config on recovery day.
### Fix
Velero schedules per namespace (or label-selected sets) with independent retention; rehearse single-namespace restore to scratch quarterly.

## practice ns-policy-agent
Kinds: Namespace
Severity: INFO
Title: Enforce namespace standards with policy agents
Docs:
- https://kubernetes.io/docs/concepts/policy/
### Why
Documented standards without enforcement decay within weeks: quotas missing, PSS unenforced, labels absent — discovered only during incidents.
### Fix
Deploy Kyverno or Gatekeeper policies requiring quotas, PSS labels, owner annotations and naming patterns on namespace creation; block, don't just warn.

## practice ns-secret-isolation
Kinds: Namespace
Severity: WARN
Title: Never share Secrets across namespaces by copying values
Docs:
- https://kubernetes.io/docs/concepts/configuration/secret/
### Why
Copied secret values drift (rotation updates one copy), and grep cannot find all copies when the credential leaks and must be revoked everywhere at once.
### Fix
Use ExternalSecrets per namespace from one vault path, or scoped reflectors with audit; never paste values between namespaces manually.

## practice ns-egress-control
Kinds: Namespace
Severity: INFO
Title: Control egress per namespace with allowlists
Docs:
- https://kubernetes.io/docs/concepts/services-networking/network-policies/
### Why
Unrestricted egress lets any compromised Pod exfiltrate to anywhere and reach cloud metadata endpoints for credential theft.
### Fix
Default-deny egress plus allowlisted external CIDRs/ports per namespace; always allow kube-dns; block metadata endpoints except where explicitly needed.

## practice ns-onboarding-checklist
Kinds: Namespace
Severity: INFO
Title: Onboard namespaces with a checklist, not folklore
Docs:
- https://kubernetes.io/docs/concepts/overview/working-with-objects/namespaces/
### Why
New team namespaces miss quotas, policies, monitoring and backups when created ad-hoc, and gaps surface only during their first incident.
### Fix
Templated onboarding: namespace, quotas, LimitRange, PSS labels, NetworkPolicies, RBAC, dashboards, backup schedule, owner annotation — all from one reviewed PR template.

## practice ns-offboarding-cleanup
Kinds: Namespace
Severity: INFO
Title: Offboard namespaces completely when teams leave
Docs:
- https://kubernetes.io/docs/concepts/overview/working-with-objects/namespaces/
### Why
Departed teams' namespaces keep running (and billing) forgotten CronJobs and Ingresses with stale DNS pointing at recycled IPs.
### Fix
Offboarding runbook: snapshot data, delete DNS/LB artifacts, remove namespace, verify quota release and cost drop; confirm within one billing cycle.

## practice pv-local-volumes
Kinds: PersistentVolume
Severity: WARN
Title: Treat local volumes as cattle with node affinity, not pets
Docs:
- https://kubernetes.io/docs/concepts/storage/volumes/#local
### Why
Local PVs die with their node, yet teams use them for "fast prod DBs" without replication, turning every node failure into permanent data loss.
### Fix
Use local volumes only with nodeAffinity plus app-level replication (or ephemeral scratch); document single-node fate explicitly; prefer CSI for anything irreplaceable.

## practice pv-topology-zones
Kinds: PersistentVolume
Severity: INFO
Title: Pin volumes to zones matching their consumers
Docs:
- https://kubernetes.io/docs/concepts/storage/storage-classes/#allowed-topologies
### Why
Zone-unaware static PVs attach-fail when Pods schedule elsewhere, or silently incur cross-zone I/O costs and latency on every operation.
### Fix
Set nodeAffinity zones on static PVs matching workload topology; prefer dynamic provisioning with allowedTopologies over hand-placed PVs.

## practice pv-capacity-actual
Kinds: PersistentVolume
Severity: INFO
Title: Verify actual usable capacity, not just requested
Docs:
- https://kubernetes.io/docs/concepts/storage/persistent-volumes/
### Why
Filesystem overhead, thin-provisioning and driver reservations mean a "100Gi" volume offers less, and apps crash on ENOSPC while dashboards show free space.
### Fix
Measure usable bytes inside Pods after provisioning (df vs request); record overhead per driver; size requests to usable, not nominal, capacity.

## practice pv-io-isolation
Kinds: PersistentVolume
Severity: INFO
Title: Isolate noisy I/O neighbors per disk class
Docs:
- https://kubernetes.io/docs/concepts/storage/persistent-volumes/
### Why
Sharing one disk (or one cloud volume type) between etcd, databases and log scrapers makes tail latency everyone's problem with no per-tenant accounting.
### Fix
Separate latency-sensitive (etcd, DB WAL) from bulk (logs, backups) onto different PVs/classes/disks; monitor per-volume latency, not node averages.

## practice pv-snapshot-before-upgrade
Kinds: PersistentVolume
Severity: WARN
Title: Snapshot PVs before storage upgrades and migrations
Docs:
- https://kubernetes.io/docs/concepts/storage/volume-snapshots/
### Why
CSI driver upgrades and StorageClass migrations corrupt or orphan volumes rarely but catastrophically, with no undo except a pre-change snapshot.
### Fix
Snapshot every production PV before driver, Kubernetes or class changes; verify snapshot ReadyToUse; keep until the new stack proves stable for a week.

## practice pv-orphan-reclaim
Kinds: PersistentVolume
Severity: INFO
Title: Reclaim Released PVs deliberately, never by accident
Docs:
- https://kubernetes.io/docs/concepts/storage/persistent-volumes/#reclaiming
### Why
Released PVs pile up holding data nobody owns (billing, confusion) or get manually recycled with stale data attached to new claims (leak across tenants).
### Fix
Triage Released PVs monthly: scrub-and-reuse for scratch (documented wipe), retain-and-archive for valuable, delete only after backup verification.

## practice pv-access-readwriteoncepod
Kinds: PersistentVolume
Severity: INFO
Title: Use ReadWriteOncePod for strict single-Pod writers
Docs:
- https://kubernetes.io/docs/concepts/storage/persistent-volumes/#access-modes
### Why
Classic RWO still allows two Pods on the same node to race a volume during rolling handovers, corrupting writers that assume exclusivity.
### Fix
On supported drivers use ReadWriteOncePod for singleton writers (migrations, singletons); verify driver support before relying on it in prod.

## practice pv-mount-propagation
Kinds: PersistentVolume
Severity: INFO
Title: Set mountPropagation only where container runtimes need it
Docs:
- https://kubernetes.io/docs/concepts/storage/volumes/
### Why
Bidirectional propagation lets containers mount onto the host, escaping containment via mount namespace manipulation — rarely needed outside CSI and runtime agents.
### Fix
Default to None; use HostToContainer for log readers, Bidirectional only for CSI drivers that document the requirement; review every bidirectional mount quarterly.

## practice pv-fs-resize-manual
Kinds: PersistentVolume
Severity: INFO
Title: Grow filesystems manually when drivers don't
Docs:
- https://kubernetes.io/docs/concepts/storage/persistent-volumes/#expanding-persistent-volumes-claims
### Why
Some drivers expand the block device but not the filesystem, so Pods see the old size despite the PVC showing grown — confusion blamed on Kubernetes.
### Fix
After expansion verify inside the Pod (df); run filesystem resize (or restart for online-capable drivers) per driver docs; record per-class behavior in the runbook.

## practice pv-nfs-hard-mounts
Kinds: PersistentVolume
Severity: WARN
Title: Mount NFS with hard, intr and timeouts tuned
Docs:
- https://kubernetes.io/docs/concepts/storage/volumes/#nfs
### Why
Soft NFS mounts with short timeouts return I/O errors to apps on transient blips, corrupting writes that hard mounts would have retried transparently.
### Fix
Use hard mounts with tuned timeo/retrans for stateful NFS; soft only for best-effort caches with app-level retry. Test server-failure behavior, not just happy path.

## practice pv-csi-driver-pinned
Kinds: PersistentVolume
Severity: INFO
Title: Pin CSI driver versions with upgrade runbooks
Docs:
- https://kubernetes.io/docs/concepts/storage/volumes/#csi
### Why
Auto-upgraded CSI drivers change mount behavior, topology handling and snapshot APIs under running volumes, breaking attaches after innocent-looking upgrades.
### Fix
Pin driver versions in Git, upgrade per vendor runbook with snapshot-before-upgrade (see above), canary on non-prod pools first.

## practice pv-volumeattributesclass
Kinds: PersistentVolume
Severity: INFO
Title: Use VolumeAttributesClass for tunable IOPS on modern clusters
Docs:
- https://kubernetes.io/docs/concepts/storage/volume-attributes-classes/
### Why
Recreating volumes to change IOPS tiers means data migration downtime for what should be a parameter tweak.
### Fix
On 1.31+ clusters define VolumeAttributesClasses per tier and switch PVCs between them without recreation; verify driver support first.

## practice pv-selinux-labels
Kinds: PersistentVolume
Severity: INFO
Title: Set SELinux mount options for shared volumes on enforcing nodes
Docs:
- https://kubernetes.io/docs/concepts/storage/volumes/
### Why
On SELinux-enforcing nodes, Pods hit permission denied on correctly-provisioned volumes because labels don't allow container_t writes — misdiagnosed as storage bugs.
### Fix
Set seLinuxOptions per StorageClass or Pod securityContext (type container_file_t or narrower); verify mounts on enforcing nodes in CI, not just permissive dev.

## practice pv-quota-driver-limits
Kinds: PersistentVolume
Severity: INFO
Title: Respect cloud volume count and size limits per node
Docs:
- https://kubernetes.io/docs/concepts/storage/storage-limits/
### Why
Cloud attach limits (e.g. 39 EBS per node) turn dense Pods Pending with opaque attach errors once the count, not capacity, is exhausted.
### Fix
Know per-cloud per-node limits; spread Pods across nodes or use fewer larger volumes; alert on attach-limit proximity, not just byte usage.

## practice pv-prewarm-performance
Kinds: PersistentVolume
Severity: INFO
Title: Prewarm EBS-style volumes before production cutover
Docs:
- https://kubernetes.io/docs/concepts/storage/persistent-volumes/
### Why
Fresh cloud volumes deliver a fraction of rated IOPS until initialized, so fresh restores and new DBs perform terribly exactly during the critical cutover window.
### Fix
Prewarm (fio read pass or vendor tooling) after restores and fresh provisioning; benchmark before cutover, not after complaints.

## practice pv-cross-ns-reuse
Kinds: PersistentVolume
Severity: WARN
Title: Never reuse a PV across namespaces without full wipe
Docs:
- https://kubernetes.io/docs/concepts/storage/persistent-volumes/
### Why
Rebinding a Released PV to a new namespace without scrubbing leaks the previous tenant's data to the next claimant — a silent cross-tenant breach.
### Fix
Wipe (or destroy and reprovision) between tenants; treat Released PVs as tainted until scrubbed and logged; prefer dynamic provisioning over manual rebinding.

## practice pv-monitoring-fill-rate
Kinds: PersistentVolume
Severity: WARN
Title: Alert on fill rate, not just fill percentage
Docs:
- https://kubernetes.io/docs/concepts/storage/persistent-volumes/
### Why
A volume at 60% filling 10% per hour dies before morning; static 85% alerts fire too late for fast growers and too early (noise) for slow ones.
### Fix
Alert on predicted exhaustion (linear forecast under N days) per volume class; pair with growth-rate dashboards per namespace.

## practice pv-driver-capacity-reporting
Kinds: PersistentVolume
Severity: INFO
Title: Enable CSI capacity reporting for smarter scheduling
Docs:
- https://kubernetes.io/docs/concepts/storage/storage-capacity/
### Why
Without CSIStorageCapacity the scheduler places Pods needing volumes on nodes whose local topology has no capacity left, causing late Pending after scheduling looked fine.
### Fix
Enable capacity reporting on CSI drivers; verify CSIStorageCapacity objects per topology segment; treat missing capacity objects as a driver health signal.

## practice pv-ephemeral-vs-persistent
Kinds: PersistentVolume
Severity: INFO
Title: Choose ephemeral volumes for scratch, PVs for state
Docs:
- https://kubernetes.io/docs/concepts/storage/ephemeral-volumes/
### Why
Scratch data on PVs wastes provisioned capacity, snapshot budgets and backup windows; state on emptyDir evaporates on reschedule with no recovery.
### Fix
Use emptyDir or generic ephemeral volumes for caches and scratch; reserve PVs for data that must survive Pod rescheduling. Review volume types quarterly.

## practice pv-documentation-per-class
Kinds: PersistentVolume
Severity: INFO
Title: Document every StorageClass as a product contract
Docs:
- https://kubernetes.io/docs/concepts/storage/storage-classes/
### Why
Undocumented classes force teams to guess performance, encryption, backup and topology behavior per volume, repeating the research every incident.
### Fix
Publish per-class cards: provisioner, performance, encryption, backup policy, topology, expansion behavior, reclaim default. Link from JKubeTerm via class annotations.

## practice readiness-probe
Kinds: Pod, Deployment, StatefulSet, DaemonSet
Severity: WARN
Title: Every container needs a readinessProbe (alias)
Docs:
- https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/
### Why
Compatibility alias for the ClusterAdvisor trigger `readiness-probe`; canonical guidance lives in `pod-probe-readiness` in this wiki.
### Fix
See `pod-probe-readiness` above for the full JKubeTerm workflow.

## practice liveness-probe
Kinds: Pod, Deployment, StatefulSet, DaemonSet
Severity: INFO
Title: Add a livenessProbe distinct from readiness (alias)
Docs:
- https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/
### Why
Compatibility alias for the ClusterAdvisor trigger `liveness-probe`; canonical guidance lives in `pod-probe-liveness` in this wiki.
### Fix
See `pod-probe-liveness` above for the full JKubeTerm workflow.

## practice resource-requests
Kinds: Pod, Deployment, StatefulSet, DaemonSet, Job, CronJob
Severity: WARN
Title: Set resource requests on every container (alias)
Docs:
- https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/
### Why
Compatibility alias for the ClusterAdvisor trigger `resource-requests`; canonical guidance lives in `pod-resources-requests` in this wiki.
### Fix
See `pod-resources-requests` above for the full JKubeTerm workflow.

## practice resource-limits
Kinds: Pod, Deployment, StatefulSet, DaemonSet, Job, CronJob
Severity: INFO
Title: Set resource limits to contain leaks (alias)
Docs:
- https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/
### Why
Compatibility alias for the ClusterAdvisor trigger `resource-limits`; canonical guidance lives in `pod-resources-limits` in this wiki.
### Fix
See `pod-resources-limits` above for the full JKubeTerm workflow.

## practice run-as-non-root
Kinds: Pod, Deployment, StatefulSet, DaemonSet, Job, CronJob
Severity: WARN
Title: Run containers as non-root (alias)
Docs:
- https://kubernetes.io/docs/tasks/configure-pod-container/security-context/
### Why
Compatibility alias for the ClusterAdvisor trigger `run-as-non-root`; canonical guidance lives in `pod-run-as-nonroot` in this wiki.
### Fix
See `pod-run-as-nonroot` above for the full JKubeTerm workflow.

## practice no-privilege-escalation
Kinds: Pod, Deployment, StatefulSet, DaemonSet, Job, CronJob
Severity: INFO
Title: Disable privilege escalation explicitly (alias)
Docs:
- https://kubernetes.io/docs/tasks/configure-pod-container/security-context/
### Why
Compatibility alias for the ClusterAdvisor trigger `no-privilege-escalation`; canonical guidance lives in `pod-no-escalation` in this wiki.
### Fix
See `pod-no-escalation` above for the full JKubeTerm workflow.

## practice host-namespaces
Kinds: Pod, Deployment, StatefulSet, DaemonSet
Severity: CRITICAL
Title: Never share host network, PID or IPC (alias)
Docs:
- https://kubernetes.io/docs/concepts/security/pod-security-standards/
### Why
Compatibility alias for the ClusterAdvisor trigger `host-namespaces`; canonical guidance lives in `pod-host-namespaces` in this wiki.
### Fix
See `pod-host-namespaces` above for the full JKubeTerm workflow.

## practice pinned-image
Kinds: Pod, Deployment, StatefulSet, DaemonSet, Job, CronJob
Severity: WARN
Title: Pin image tags, never float on latest (alias)
Docs:
- https://kubernetes.io/docs/concepts/containers/images/#image-names
### Why
Compatibility alias for the ClusterAdvisor trigger `pinned-image`; canonical guidance lives in `pod-pinned-image` in this wiki.
### Fix
See `pod-pinned-image` above for the full JKubeTerm workflow.

## practice image-pull-policy
Kinds: Pod, Deployment, StatefulSet, DaemonSet, Job, CronJob
Severity: INFO
Title: Match imagePullPolicy to the tagging strategy (alias)
Docs:
- https://kubernetes.io/docs/concepts/containers/images/#updating-images
### Why
Compatibility alias for the ClusterAdvisor trigger `image-pull-policy`; canonical guidance lives in `pod-image-pull-policy` in this wiki.
### Fix
See `pod-image-pull-policy` above for the full JKubeTerm workflow.

## practice replica-count
Kinds: Deployment
Severity: WARN
Title: Run at least two replicas for availability (alias)
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/deployment/
### Why
Compatibility alias for the ClusterAdvisor trigger `replica-count`; canonical guidance lives in `deploy-replica-count` in this wiki.
### Fix
See `deploy-replica-count` above for the full JKubeTerm workflow.

## practice rolling-update
Kinds: Deployment
Severity: INFO
Title: Prefer RollingUpdate over Recreate (alias)
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/deployment/#strategy
### Why
Compatibility alias for the ClusterAdvisor trigger `rolling-update`; canonical guidance lives in `deploy-rolling-update` in this wiki.
### Fix
See `deploy-rolling-update` above for the full JKubeTerm workflow.

## practice rollout-complete
Kinds: Deployment
Severity: WARN
Title: A stuck rollout is an incident, not patience (alias)
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/deployment/#checking-rollout-status
### Why
Compatibility alias for the ClusterAdvisor trigger `rollout-complete`; canonical guidance lives in `deploy-rollout-complete` in this wiki.
### Fix
See `deploy-rollout-complete` above for the full JKubeTerm workflow.

## practice labels-standard
Kinds: Deployment, StatefulSet
Severity: INFO
Title: Use recommended app.kubernetes.io labels (alias)
Docs:
- https://kubernetes.io/docs/concepts/overview/working-with-objects/common-labels/
### Why
Compatibility alias for the ClusterAdvisor trigger `labels-standard`; canonical guidance lives in `deploy-labels-standard` in this wiki.
### Fix
See `deploy-labels-standard` above for the full JKubeTerm workflow.

## practice daemonset-tolerations
Kinds: DaemonSet
Severity: INFO
Title: DaemonSets need tolerations for tainted nodes (alias)
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/daemonset/
### Why
Compatibility alias for the ClusterAdvisor trigger `daemonset-tolerations`; canonical guidance lives in `ds-tolerations` in this wiki.
### Fix
See `ds-tolerations` above for the full JKubeTerm workflow.

## practice service-selector
Kinds: Service
Severity: WARN
Title: Every Service needs a matching selector (alias)
Docs:
- https://kubernetes.io/docs/concepts/services-networking/service/
### Why
Compatibility alias for the ClusterAdvisor trigger `service-selector`; canonical guidance lives in `svc-selector` in this wiki.
### Fix
See `svc-selector` above for the full JKubeTerm workflow.

## practice service-type
Kinds: Service
Severity: INFO
Title: Expose via ClusterIP plus Ingress, not NodePort (alias)
Docs:
- https://kubernetes.io/docs/concepts/services-networking/service/#publishing-services-service-types
### Why
Compatibility alias for the ClusterAdvisor trigger `service-type`; canonical guidance lives in `svc-type-exposure` in this wiki.
### Fix
See `svc-type-exposure` above for the full JKubeTerm workflow.

## practice config-no-secrets
Kinds: ConfigMap
Severity: WARN
Title: Never put secrets into ConfigMaps (alias)
Docs:
- https://kubernetes.io/docs/concepts/configuration/secret/
### Why
Compatibility alias for the ClusterAdvisor trigger `config-no-secrets`; canonical guidance lives in `cm-no-secrets` in this wiki.
### Fix
See `cm-no-secrets` above for the full JKubeTerm workflow.

## practice config-immutable
Kinds: ConfigMap
Severity: INFO
Title: Mark stable ConfigMaps immutable (alias)
Docs:
- https://kubernetes.io/docs/concepts/configuration/configmap/#immutable-configmaps
### Why
Compatibility alias for the ClusterAdvisor trigger `config-immutable`; canonical guidance lives in `cm-immutable` in this wiki.
### Fix
See `cm-immutable` above for the full JKubeTerm workflow.

## practice job-backoff
Kinds: Job
Severity: INFO
Title: Bound Job retries with backoffLimit (alias)
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/job/
### Why
Compatibility alias for the ClusterAdvisor trigger `job-backoff`; canonical guidance lives in `job-backoff-limit` in this wiki.
### Fix
See `job-backoff-limit` above for the full JKubeTerm workflow.

## practice job-restart-policy
Kinds: Job
Severity: WARN
Title: Job Pods must use restartPolicy Never or OnFailure (alias)
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/job/#pod-template
### Why
Compatibility alias for the ClusterAdvisor trigger `job-restart-policy`; canonical guidance lives in `job-restart-never` in this wiki.
### Fix
See `job-restart-never` above for the full JKubeTerm workflow.

## practice ingress-tls
Kinds: Ingress
Severity: WARN
Title: Terminate TLS on every Ingress (alias)
Docs:
- https://kubernetes.io/docs/concepts/services-networking/ingress/#tls
### Why
Compatibility alias for the ClusterAdvisor trigger `ingress-tls`; canonical guidance lives in `ing-tls` in this wiki.
### Fix
See `ing-tls` above for the full JKubeTerm workflow.

## practice ingress-class
Kinds: Ingress
Severity: INFO
Title: Always set ingressClassName (alias)
Docs:
- https://kubernetes.io/docs/concepts/services-networking/ingress/#ingress-class
### Why
Compatibility alias for the ClusterAdvisor trigger `ingress-class`; canonical guidance lives in `ing-class` in this wiki.
### Fix
See `ing-class` above for the full JKubeTerm workflow.

## practice cron-suspend
Kinds: CronJob
Severity: INFO
Title: A suspended CronJob schedules nothing (alias)
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/cron-jobs/
### Why
Compatibility alias for the ClusterAdvisor trigger `cron-suspend`; canonical guidance lives in `cronjob-suspend` in this wiki.
### Fix
See `cronjob-suspend` above for the full JKubeTerm workflow.

## practice cron-schedule
Kinds: CronJob
Severity: WARN
Title: Cron schedules must be valid 5-field expressions (alias)
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/cron-jobs/#cron-schedule-syntax
### Why
Compatibility alias for the ClusterAdvisor trigger `cron-schedule`; canonical guidance lives in `cronjob-schedule-valid` in this wiki.
### Fix
See `cronjob-schedule-valid` above for the full JKubeTerm workflow.

## practice pvc-storage-class
Kinds: PersistentVolumeClaim
Severity: WARN
Title: Every PVC needs a StorageClass or a bound PV (alias)
Docs:
- https://kubernetes.io/docs/concepts/storage/persistent-volumes/
### Why
Compatibility alias for the ClusterAdvisor trigger `pvc-storage-class`; canonical guidance lives in `pvc-class-or-pv` in this wiki.
### Fix
See `pvc-class-or-pv` above for the full JKubeTerm workflow.

## practice pvc-rwo-replicas
Kinds: PersistentVolumeClaim, Deployment, StatefulSet
Severity: WARN
Title: ReadWriteOnce volumes allow a single writer (alias)
Docs:
- https://kubernetes.io/docs/concepts/storage/persistent-volumes/#access-modes
### Why
Compatibility alias for the ClusterAdvisor trigger `pvc-rwo-replicas`; canonical guidance lives in `pvc-rwo-single-writer` in this wiki.
### Fix
See `pvc-rwo-single-writer` above for the full JKubeTerm workflow.

## practice pv-reclaim
Kinds: PersistentVolume
Severity: INFO
Title: Choose the reclaim policy deliberately (alias)
Docs:
- https://kubernetes.io/docs/concepts/storage/persistent-volumes/#reclaiming
### Why
Compatibility alias for the ClusterAdvisor trigger `pv-reclaim`; canonical guidance lives in `pv-reclaim-policy` in this wiki.
### Fix
See `pv-reclaim-policy` above for the full JKubeTerm workflow.

## practice node-pressure
Kinds: Node
Severity: WARN
Title: React to DiskPressure and MemoryPressure, not just Ready (alias)
Docs:
- https://kubernetes.io/docs/concepts/architecture/nodes/#condition
### Why
Compatibility alias for the ClusterAdvisor trigger `node-pressure`; canonical guidance lives in `node-pressure-eviction` in this wiki.
### Fix
See `node-pressure-eviction` above for the full JKubeTerm workflow.

## practice namespace-quotas
Kinds: Namespace
Severity: INFO
Title: Bound every team namespace with quotas and limits (alias)
Docs:
- https://kubernetes.io/docs/concepts/policy/resource-quotas/
### Why
Compatibility alias for the ClusterAdvisor trigger `namespace-quotas`; canonical guidance lives in `ns-quotas` in this wiki.
### Fix
See `ns-quotas` above for the full JKubeTerm workflow.

## practice event-hygiene
Kinds: Event
Severity: INFO
Title: Read Events newest-first, then describe the object (alias)
Docs:
- https://kubernetes.io/docs/reference/kubectl/generated/kubectl_events/
### Why
Compatibility alias for the ClusterAdvisor trigger `event-hygiene`; canonical guidance lives in `ev-hygiene-triage` in this wiki.
### Fix
See `ev-hygiene-triage` above for the full JKubeTerm workflow.

## practice stateful-storage
Kinds: StatefulSet
Severity: INFO
Title: StatefulSets need volumeClaimTemplates (alias)
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/statefulset/
### Why
Compatibility alias for the ClusterAdvisor trigger `stateful-storage`; canonical guidance lives in `sts-claim-templates` in this wiki.
### Fix
See `sts-claim-templates` above for the full JKubeTerm workflow.

## practice pod-status
Kinds: Pod
Severity: CRITICAL
Title: Treat CrashLoopBackOff and ImagePullBackOff as incidents (alias)
Docs:
- https://kubernetes.io/docs/tasks/debug/debug-application/debug-pods/
### Why
Compatibility alias for the ClusterAdvisor trigger `pod-status`; canonical guidance lives in `pod-crashloop-incident` in this wiki.
### Fix
See `pod-crashloop-incident` above for the full JKubeTerm workflow.

## practice pod-resources
Kinds: Pod
Severity: INFO
Title: Use Pod-level resources on Kubernetes 1.37 and newer (alias)
Docs:
- https://kubernetes.io/docs/concepts/workloads/pods/pod-level-resources/
### Why
Compatibility alias for the ClusterAdvisor trigger `pod-resources`; canonical guidance lives in `pod-level-resources` in this wiki.
### Fix
See `pod-level-resources` above for the full JKubeTerm workflow.

## practice stateful-retention
Kinds: StatefulSet
Severity: INFO
Title: Control PVC cleanup with retention policies on 1.35 and newer (alias)
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/statefulset/#persistentvolumeclaim-retention
### Why
Compatibility alias for the ClusterAdvisor trigger `stateful-retention`; canonical guidance lives in `stateful-retention` in this wiki.
### Fix
See `sts-retention-policy` above for the full JKubeTerm workflow.

## practice job-success-policy
Kinds: Job
Severity: INFO
Title: Job successPolicy can short-circuit completions on 1.36 and newer (alias)
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/job/
### Why
Compatibility alias for the ClusterAdvisor trigger `job-success-policy`; canonical guidance lives in `job-success-policy` in this wiki.
### Fix
See `job-success-shortcircuit` above for the full JKubeTerm workflow.

## practice pod-env-configmap-refs
Kinds: Pod
Severity: WARN
Title: Reference ConfigMaps by key with optional flags
Docs:
- https://kubernetes.io/docs/tasks/configure-pod-container/configure-pod-configmap/
### Why
Pods crash when a referenced ConfigMap key is renamed; the error names the missing key but teams blame the image.
### Fix
Use valueFrom.configMapKeyRef with explicit key plus optional:true only for true overrides; keep required config strict.

## practice pod-share-process-namespace
Kinds: Pod
Severity: INFO
Title: Share PID namespace only for debugging sidecars
Docs:
- https://kubernetes.io/docs/tasks/configure-pod-container/share-process-namespace/
### Why
shareProcessNamespace exposes every process to every container, widening a single-container breach to the whole Pod.
### Fix
Enable only for paired debug agents; keep it false for app Pods.

## practice pod-os-field
Kinds: Pod
Severity: INFO
Title: Pin Pod OS for mixed clusters
Docs:
- https://kubernetes.io/docs/concepts/workloads/pods/#os
### Why
Linux images scheduled onto Windows nodes fail with opaque pull errors that look like registry outages.
### Fix
Set spec.os.name explicitly in mixed clusters; pair with nodeSelector kubernetes.io/os.

## practice svc-clusterip-none-headless
Kinds: Service
Severity: INFO
Title: Use None ClusterIP only for headless discovery
Docs:
- https://kubernetes.io/docs/concepts/services-networking/service/#headless-services
### Why
Setting clusterIP None for a serving Service removes load balancing silently; clients get raw A records unexpectedly.
### Fix
Reserve None for StatefulSet governing services; use real ClusterIPs for serving.

## practice svc-publish-not-ready
Kinds: Service
Severity: INFO
Title: Publish not-ready addresses only for peer discovery
Docs:
- https://kubernetes.io/docs/concepts/services-networking/service/
### Why
Enabling publishNotReadyAddresses on serving Services routes traffic to starting Pods, causing 502 during scale-up.
### Fix
Enable only for gossip/peer-discovery headless Services; keep serving Services readiness-gated.

## practice svc-healthcheck-nodeport
Kinds: Service
Severity: INFO
Title: Pin healthCheckNodePort for stable LB checks
Docs:
- https://kubernetes.io/docs/tasks/access-application-cluster/create-external-load-balancer/#health-check-nodeport
### Why
Auto-assigned health ports change on Service edits, breaking firewall rules and LB configs silently.
### Fix
Pin healthCheckNodePort explicitly and record it alongside firewall/DNS docs.

## practice svc-allocate-lb-nodeports
Kinds: Service
Severity: INFO
Title: Decide allocateLoadBalancerNodePorts deliberately
Docs:
- https://kubernetes.io/docs/concepts/services-networking/service/#load-balancer-nodeport-allocation
### Why
Default NodePort allocation per LB Service exhausts the nodeport range on LB-heavy clusters.
### Fix
Disable NodePort allocation when the cloud LB talks directly to Pods; keep otherwise.

## practice svc-traffic-distribution
Kinds: Service
Severity: INFO
Title: Prefer trafficDistribution over legacy hints
Docs:
- https://kubernetes.io/docs/concepts/services-networking/service/#topology-aware-routing
### Why
Legacy topology hints annotated inconsistently across Services produce unpredictable zone skew.
### Fix
Set spec.trafficDistribution PreferClose on supported clusters for zone-local routing; verify per-zone endpoints.

## practice svc-lb-class
Kinds: Service
Severity: INFO
Title: Pin loadBalancerClass for multi-LB clusters
Docs:
- https://kubernetes.io/docs/concepts/services-networking/service/#load-balancer-class
### Why
Two LB controllers fight over unclassified Services, flipping EXTERNAL-IP between implementations.
### Fix
Set loadBalancerClass per Service matching the intended controller; default only one controller per cluster.

## practice svc-session-sticky-timeout
Kinds: Service
Severity: INFO
Title: Bound session-sticky timeouts explicitly
Docs:
- https://kubernetes.io/docs/concepts/services-networking/service/#session-affinity
### Why
Default 3h stickiness pins clients to long-dead Pods behind NATs through rollouts.
### Fix
Set sessionAffinityConfig clientIP timeoutSeconds short (e.g. 300) when stickiness is unavoidable.

## practice cm-key-count
Kinds: ConfigMap
Severity: INFO
Title: Keep key counts reviewable per ConfigMap
Docs:
- https://kubernetes.io/docs/concepts/configuration/configmap/
### Why
Hundred-key ConfigMaps mix owners and reload semantics; one edit risks unrelated consumers.
### Fix
Split by consumer with under ~20 keys each; list consumers in annotations.

## practice cm-no-binary-in-data
Kinds: ConfigMap
Severity: INFO
Title: Keep binary blobs out of string data
Docs:
- https://kubernetes.io/docs/concepts/configuration/configmap/
### Why
UTF-8 validation rejects or mangles binary pasted into data, failing Apply with cryptic errors.
### Fix
Use binaryData for non-UTF8; validate with dry-run before applying.

## practice cm-watch-vs-restart
Kinds: ConfigMap
Severity: INFO
Title: Decide watch vs restart per consumer
Docs:
- https://kubernetes.io/docs/concepts/configuration/configmap/
### Why
Assuming all mounts hot-reload (or all need restarts) causes stale config or needless churn fleet-wide.
### Fix
Document per-key reload behavior; wire watches or checksums accordingly per workload.

## practice cm-template-delimiters
Kinds: ConfigMap
Severity: INFO
Title: Escape template delimiters in config bodies
Docs:
- https://kubernetes.io/docs/concepts/configuration/configmap/
### Why
Helm/Go delimiters inside nginx or app configs render as empty strings, shipping broken configs that parse fine as YAML.
### Fix
Escape delimiters or use alternative delimiters for config bodies containing template syntax.

## practice cm-dry-run-diff
Kinds: ConfigMap
Severity: INFO
Title: Dry-run and diff ConfigMaps before applying
Docs:
- https://kubernetes.io/docs/concepts/configuration/configmap/
### Why
Blind applies overwrite working config with typos; rollback needs the previous content nobody saved.
### Fix
Always dry-run -o yaml plus diff against live before apply; keep previous versions in Git.

## practice cm-owner-annotation
Kinds: ConfigMap
Severity: INFO
Title: Annotate owner and reload contract
Docs:
- https://kubernetes.io/docs/concepts/overview/working-with-objects/annotations/
### Why
Ownerless ConfigMaps fear edits; nobody knows the blast radius or reload path.
### Fix
Annotate owner team, consumers and reload behavior; link the owning chart.

## practice cm-no-env-dump
Kinds: ConfigMap
Severity: WARN
Title: Never dump whole ConfigMaps into env
Docs:
- https://kubernetes.io/docs/tasks/configure-pod-container/configure-pod-configmap/
### Why
envFrom of giant ConfigMaps leaks unrelated keys into every process environment, expanding breach blast radius.
### Fix
Import only needed keys via valueFrom; keep env surface minimal and reviewed.

## practice cm-line-endings
Kinds: ConfigMap
Severity: INFO
Title: Normalize line endings in file-backed keys
Docs:
- https://kubernetes.io/docs/concepts/configuration/configmap/
### Why
CRLF from Windows editors breaks Unix parsers and checksums, causing phantom diffs and parse errors.
### Fix
Enforce LF via .gitattributes and CI checks on config repos.

## practice cm-immutable-migration
Kinds: ConfigMap
Severity: INFO
Title: Migrate to immutable with dual-name rollout
Docs:
- https://kubernetes.io/docs/concepts/configuration/configmap/#immutable-configmaps
### Why
Flipping immutable in place is rejected; teams force-delete and cause outages.
### Fix
Create the -v2 immutable copy, flip references, verify, then delete v1 — never edit in place.

## practice cm-secret-scan-ci
Kinds: ConfigMap
Severity: WARN
Title: Scan ConfigMaps for secret patterns in CI
Docs:
- https://kubernetes.io/docs/concepts/configuration/secret/
### Why
Credentials slip into ConfigMaps through copy-paste and env dumps, living in plain text across backups.
### Fix
Run secret scanners (gitleaks/trufflehog) on manifests; block merges on matches except allowlisted examples.

## practice job-ttl-failed-longer
Kinds: Job
Severity: INFO
Title: Keep failed Jobs longer than successful ones
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/job/#clean-up-finished-jobs-automatically
### Why
Equal TTL deletes failure evidence before morning triage while successful clutter lingers.
### Fix
Set longer TTL for failures via separate cleanup policies or manual holds on failure labels.

## practice job-labels-run-id
Kinds: Job
Severity: INFO
Title: Label every Job run with run-id and trigger
Docs:
- https://kubernetes.io/docs/concepts/overview/working-with-objects/labels/
### Why
Identical Job names across reruns confuse logs, drill-down and audit about which run did what.
### Fix
Inject run-id plus trigger (manual/cron/backfill) labels per run; filter by them in JKubeTerm.

## practice job-suspend-flag
Kinds: Job
Severity: INFO
Title: Use suspend for dry-run validation
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/job/#suspending-a-job
### Why
Untested Jobs fire immediately on apply in production namespaces during reviews.
### Fix
Create suspended, review rendered spec, then unsuspend; wire this into GitOps PR checks.

## practice job-priority-low
Kinds: Job
Severity: INFO
Title: Run batch Jobs below serving priority
Docs:
- https://kubernetes.io/docs/concepts/scheduling-eviction/pod-priority-preemption/
### Why
Batch bursts preempt serving Pods when priorities tie or batch outranks accidentally.
### Fix
Assign lower PriorityClass plus preemptionPolicy Never to batch Jobs.

## practice job-node-selector-batch
Kinds: Job
Severity: INFO
Title: Fence batch Jobs onto batch node pools
Docs:
- https://kubernetes.io/docs/concepts/scheduling-eviction/assign-pod-node/
### Why
Batch floods on serving nodes starve latency-sensitive Pods of CPU and disk.
### Fix
Use nodeSelector/affinity to batch pools with taints; keep serving pools clean.

## practice job-monitor-duration-slo
Kinds: Job
Severity: INFO
Title: SLO Job durations and alert on drift
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/job/
### Why
Gradually slowing Jobs (data growth) cross batch windows silently until they overlap the next tick.
### Fix
Record duration histograms per Job; alert on p95 drift week over week.

## practice cronjob-starting-deadline-default
Kinds: CronJob
Severity: WARN
Title: Never leave startingDeadlineSeconds unset
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/cron-jobs/
### Why
Unset deadlines silently skip late starts; the missing run looks like success.
### Fix
Set explicit deadlines (e.g. 200s) on every CronJob; alert on missed starts.

## practice cronjob-last-schedule-dashboard
Kinds: CronJob
Severity: INFO
Title: Dashboard last-schedule vs now gap
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/cron-jobs/
### Why
Nobody watches CronJob status pages; silent suspensions persist for weeks.
### Fix
Graph time-since-last-success per CronJob; page when gap exceeds 2x schedule.

## practice cronjob-rbac-least
Kinds: CronJob
Severity: WARN
Title: Scope CronJob ServiceAccounts minimally
Docs:
- https://kubernetes.io/docs/tasks/configure-pod-container/configure-service-account/
### Why
Nightly jobs with admin rights are juicy targets running on predictable schedules.
### Fix
Dedicated least-privilege SAs per CronJob; audit verbs quarterly.

## practice cronjob-resource-quotas-nightly
Kinds: CronJob
Severity: INFO
Title: Quota nightly batch windows
Docs:
- https://kubernetes.io/docs/concepts/policy/resource-quotas/
### Why
Overlapping CronJobs stampede at midnight, exhausting quotas and evicting serving Pods.
### Fix
Stagger schedules, quota batch namespaces, fence onto batch pools.

## practice cronjob-pod-active-deadline
Kinds: CronJob
Severity: INFO
Title: Set activeDeadlineSeconds inside CronJob templates
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/job/#job-termination-and-cleanup
### Why
Hung ticks overlap the next schedule forever under Allow policy.
### Fix
Set activeDeadlineSeconds in every jobTemplate; pair with concurrencyPolicy.

## practice cronjob-failed-history-review
Kinds: CronJob
Severity: INFO
Title: Review failed history weekly
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/cron-jobs/
### Why
Failed history pruned too fast hides flaky Jobs that fail every third night.
### Fix
Keep failed history 5-10; review weekly; ship logs centrally regardless.

## practice cronjob-dst-test
Kinds: CronJob
Severity: WARN
Title: Test schedules across DST boundaries
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/cron-jobs/#time-zones
### Why
Spring-forward skips hourly runs, fall-back double-fires daily batches with billing impact.
### Fix
Simulate DST transitions in staging with pinned timeZone; document expected behavior.

## practice ing-tls-secret-rotation
Kinds: Ingress
Severity: WARN
Title: Rotate TLS secrets without downtime
Docs:
- https://kubernetes.io/docs/concepts/services-networking/ingress/#tls
### Why
Manual secret swaps cause brief mismatches or stale controller caches serving expired certs.
### Fix
Rotate via cert-manager renewal (automatic) or staged secret swap with controller reload verification.

## practice ing-controller-resources
Kinds: Ingress
Severity: INFO
Title: Size Ingress controller requests/limits
Docs:
- https://kubernetes.io/docs/concepts/services-networking/ingress-controllers/
### Why
Uncapped controllers OOM under traffic spikes exactly when edge capacity matters most.
### Fix
Set requests/limits per traffic profile; HPA the controller deployment itself.

## practice ing-controller-replicas
Kinds: Ingress
Severity: WARN
Title: Run 2+ Ingress controller replicas
Docs:
- https://kubernetes.io/docs/concepts/services-networking/ingress-controllers/
### Why
Single-replica controllers make the edge a single point of failure for every route.
### Fix
Run at least 2 with anti-affinity plus PDB; verify rolling updates keep capacity.

## practice ing-access-log-format
Kinds: Ingress
Severity: INFO
Title: Standardize structured access-log format
Docs:
- https://kubernetes.io/docs/concepts/services-networking/ingress/
### Why
Unstructured edge logs resist aggregation; debugging needs request-ID joins across tiers.
### Fix
Emit JSON access logs with request ID, host, path, status, latency; index centrally.

## practice ing-client-ip-preservation
Kinds: Ingress
Severity: INFO
Title: Preserve client IPs end to end
Docs:
- https://kubernetes.io/docs/tasks/access-application-cluster/create-external-load-balancer/#preserving-the-client-source-ip
### Why
SNAT at multiple layers hides true client IPs, breaking rate limits, audit and geo.
### Fix
Use externalTrafficPolicy Local or PROXY protocol plus X-Forwarded-For handling; verify with echo backends.

## practice ing-websocket-support
Kinds: Ingress
Severity: INFO
Title: Enable WebSocket and SSE paths explicitly
Docs:
- https://kubernetes.io/docs/concepts/services-networking/ingress/
### Why
Default proxy buffering breaks WebSockets and server-sent events with timeouts and buffering delays.
### Fix
Annotate WS/SSE routes (proxy buffering off, long timeouts); test with real socket clients through the edge.

## practice ing-redirect-www-canonical
Kinds: Ingress
Severity: INFO
Title: Canonicalize www vs apex in one place
Docs:
- https://kubernetes.io/docs/concepts/services-networking/ingress/
### Why
Split www/apex handling across Ingresses causes redirect loops and SEO splits.
### Fix
Redirect canonically at the edge (one direction only); test both variants plus HSTS.

## practice ing-host-collision-guard
Kinds: Ingress
Severity: WARN
Title: Prevent host collisions across namespaces (guard)
Docs:
- https://kubernetes.io/docs/concepts/services-networking/ingress/
### Why
Duplicate hosts across namespaces race nondeterministically between backends.
### Fix
Enforce one-owner-per-host policy validated in CI across all namespaces.

## practice pvc-label-owner
Kinds: PersistentVolumeClaim
Severity: INFO
Title: Label PVCs with owner and purpose
Docs:
- https://kubernetes.io/docs/concepts/storage/persistent-volumes/
### Why
Ownerless volumes block safe deletion; nobody knows which app breaks if the PVC goes.
### Fix
Label app, team and purpose on every PVC; surface in JKubeTerm Object view.

## practice pvc-alert-inodes
Kinds: PersistentVolumeClaim
Severity: WARN
Title: Alert on inode exhaustion, not just bytes
Docs:
- https://kubernetes.io/docs/concepts/storage/persistent-volumes/
### Why
Millions of tiny files exhaust inodes while byte gauges look healthy; writes fail with ENOSPC confusion.
### Fix
Monitor inode usage per volume alongside bytes; alert on both.

## practice pvc-fstrim-schedule
Kinds: PersistentVolumeClaim
Severity: INFO
Title: Schedule fstrim on thin-provisioned volumes
Docs:
- https://kubernetes.io/docs/concepts/storage/persistent-volumes/
### Why
Untrimmed thin volumes grow forever as deletes never return blocks to the pool.
### Fix
Run periodic fstrim (CronJob or driver policy) on thin classes; track reclaimed bytes.

## practice pvc-clone-test-restore
Kinds: PersistentVolumeClaim
Severity: INFO
Title: Test clones as restore rehearsal
Docs:
- https://kubernetes.io/docs/concepts/storage/volume-pvc-datasource/
### Why
Untested clone paths fail during real incidents with driver-specific errors.
### Fix
Clone to scratch namespaces quarterly; time the operation for RTO math.

## practice pvc-pod-affinity-zone
Kinds: PersistentVolumeClaim
Severity: INFO
Title: Co-schedule Pods with their volumes zones
Docs:
- https://kubernetes.io/docs/concepts/storage/storage-classes/#allowed-topologies
### Why
Cross-zone attach adds latency and egress per I/O or fails outright.
### Fix
Pair allowedTopologies with Pod affinity so compute follows storage zones.

## practice pvc-delete-retention-check
Kinds: PersistentVolumeClaim
Severity: WARN
Title: Check retention before deleting bound PVCs
Docs:
- https://kubernetes.io/docs/concepts/storage/persistent-volumes/#reclaiming
### Why
Deleting a PVC with Delete-retention wipes the PV data instantly with no recycle bin.
### Fix
Snapshot first; confirm reclaim policy; require two-person approval for prod deletes.

## practice pvc-metrics-capacity
Kinds: PersistentVolumeClaim
Severity: INFO
Title: Expose kubelet volume metrics per PVC
Docs:
- https://kubernetes.io/docs/concepts/storage/persistent-volumes/
### Why
Missing volume stats leave usage invisible until ENOSPC crashes the app.
### Fix
Ensure kubelet volume metrics plus Prometheus rules per PVC; dashboard bytes and inodes.

## practice pvc-accessmode-review-ci
Kinds: PersistentVolumeClaim
Severity: INFO
Title: Lint accessModes in CI
Docs:
- https://kubernetes.io/docs/concepts/storage/persistent-volumes/#access-modes
### Why
RWO-vs-RWX mistakes surface only at multi-attach runtime, not at apply time.
### Fix
Lint accessModes against replica counts and strategies in CI; block RWO multi-writer.

## practice ev-drill-owner-first
Kinds: Event
Severity: INFO
Title: Drill to the owner before reading Pod Events
Docs:
- https://kubernetes.io/docs/concepts/overview/working-with-objects/owners-dependents/
### Why
Pod Events show symptoms; the faulty template field lives two levels up.
### Fix
In JKubeTerm drill from Pod to Deployment/Job first, then read owner Events.

## practice ev-slo-burn-events
Kinds: Event
Severity: WARN
Title: Burn error budgets on Warning Event rates
Docs:
- https://kubernetes.io/docs/reference/kubectl/generated/kubectl_events/
### Why
Raw error counts without budgets page on every rollout blip.
### Fix
Define Warning-rate SLOs per reason; page on burn, ticket on drift.

## practice ev-correlate-deploys
Kinds: Event
Severity: INFO
Title: Overlay Events with deploy markers
Docs:
- https://kubernetes.io/docs/reference/kubectl/generated/kubectl_events/
### Why
Errors without deploy context get blamed on the wrong change.
### Fix
Join event timelines with rollout annotations in Grafana; note firstTimestamp vs deploy time.

## practice ev-quiet-namespaces-audit
Kinds: Event
Severity: INFO
Title: Audit quiet namespaces for missing exporters
Docs:
- https://kubernetes.io/docs/reference/kubectl/generated/kubectl_events/
### Why
Silent namespaces may lack exporter coverage rather than lack problems.
### Fix
Quarterly: generate test Warnings per namespace; verify central receipt.

## practice ev-field-selector-skill
Kinds: Event
Severity: INFO
Title: Master fieldSelector for Events triage
Docs:
- https://kubernetes.io/docs/reference/kubectl/generated/kubectl_events/
### Why
Unfiltered kubectl get events drowns triage in unrelated objects.
### Fix
Practice involvedObject and reason field selectors; save snippets in runbooks.

## practice ev-count-reset-meaning
Kinds: Event
Severity: INFO
Title: Interpret count resets after rescheduling
Docs:
- https://kubernetes.io/docs/reference/kubectl/generated/kubectl_events/
### Why
New Pod names reset counts, hiding a chronic loop as a series of fresh low-count Events.
### Fix
Group by reason plus template hash across reschedules, not by Pod name.

## practice ev-warning-ratio-dashboard
Kinds: Event
Severity: INFO
Title: Dashboard Warning-to-Normal ratio per workload
Docs:
- https://kubernetes.io/docs/reference/kubectl/generated/kubectl_events/
### Why
Absolute counts mislead across fleet sizes; ratios reveal degrading workloads early.
### Fix
Track Warning share per Deployment/Job over rolling weeks.

## practice ev-prewarm-runbook-links
Kinds: Event
Severity: INFO
Title: Link runbooks from alert annotations, not memory
Docs:
- https://kubernetes.io/docs/reference/kubectl/generated/kubectl_events/
### Why
Pasted URLs rot; responders search under pressure.
### Fix
Annotate alert rules with runbook URLs; verify links quarterly.

## practice ev-postmortem-event-timeline
Kinds: Event
Severity: INFO
Title: Paste Event timelines into postmortems
Docs:
- https://kubernetes.io/docs/reference/kubectl/generated/kubectl_events/
### Why
Postmortems without timelines rely on memory and misattribute causes.
### Fix
Export the filtered Event window (first/last/count) into every incident doc.

## practice node-gpu-time-slicing
Kinds: Node
Severity: INFO
Title: Share GPUs with time-slicing deliberately
Docs:
- https://kubernetes.io/docs/tasks/manage-gpus/scheduling-gpus/
### Why
Whole-GPU allocation for tiny inference wastes expensive accelerators.
### Fix
Use device-plugin time-slicing or MPS with quotas; monitor per-share utilization.

## practice node-kube-reserved-math
Kinds: Node
Severity: INFO
Title: Derive kube-reserved from node size, not folklore
Docs:
- https://kubernetes.io/docs/tasks/administer-cluster/reserve-compute-resources/
### Why
Copy-pasted reservations under-protect small nodes and over-tax large ones.
### Fix
Compute reservations per instance size (percentage plus floor); verify via allocatable math.

## practice node-max-pods-density
Kinds: Node
Severity: INFO
Title: Tune max-pods to IP and density reality
Docs:
- https://kubernetes.io/docs/concepts/architecture/nodes/
### Why
Default 110 pods/node exhausts IPs or crashes density before CPU fills, or wastes IPs on small nodes.
### Fix
Set max-pods per pool from IP budget and workload size; monitor pod-density vs IP exhaustion.

## practice node-cgroup-driver-match
Kinds: Node
Severity: WARN
Title: Match cgroup driver across kubelet and runtime
Docs:
- https://kubernetes.io/docs/tasks/administer-cluster/kubelet-config-file/
### Why
Mismatched systemd/cgroupfs drivers cause flaky resource accounting and OOM behavior per node.
### Fix
Pin systemd driver on both; verify in kubelet logs cluster-wide after changes.

## practice node-swap-disabled-policy
Kinds: Node
Severity: INFO
Title: Keep swap disabled or account it explicitly
Docs:
- https://kubernetes.io/docs/tasks/administer-cluster/kubelet-config-file/
### Why
Partial swap enablement produces unpredictable performance and eviction behavior per node.
### Fix
Keep swap off (default) or adopt NodeSwap feature deliberately with limits per workload.

## practice node-topology-manager-policy
Kinds: Node
Severity: INFO
Title: Pin TopologyManager for latency-sensitive Pods
Docs:
- https://kubernetes.io/docs/tasks/administer-cluster/cpu-management-policies/
### Why
Cross-NUMA memory access adds tail latency invisible in averages.
### Fix
Use single-numa-node policy with static CPU manager for DPDK/ML inference Pods.

## practice node-cpu-manager-static
Kinds: Node
Severity: INFO
Title: Static CPU manager only with Guaranteed QoS
Docs:
- https://kubernetes.io/docs/tasks/administer-cluster/cpu-management-policies/
### Why
Static policy without Guaranteed QoS silently does nothing, yet teams assume pinning works.
### Fix
Pair static policy with Guaranteed Pods; verify CPUAffinity in Pod status.

## practice node-boot-image-pinned
Kinds: Node
Severity: INFO
Title: Pin boot images per node pool in Git
Docs:
- https://kubernetes.io/docs/concepts/architecture/nodes/
### Why
Rolling boot-image changes under running Pods introduce kernel skew silently.
### Fix
Version node images per pool; roll pools with cordon-drain-verify cycles.

## practice node-audit-ssh-access
Kinds: Node
Severity: WARN
Title: Audit SSH access to nodes quarterly
Docs:
- https://kubernetes.io/docs/concepts/architecture/nodes/
### Why
Standing SSH to nodes bypasses API audit and enables untracked kubelet tweaks.
### Fix
Remove standing SSH; require ephemeral access with session recording and expiry.

## practice node-firewall-kubelet-ports
Kinds: Node
Severity: WARN
Title: Firewall kubelet and API ports to cluster CIDRs
Docs:
- https://kubernetes.io/docs/concepts/architecture/nodes/
### Why
Exposed kubelet ports (10250/10255) allow unauthenticated reads or exec paths on misconfigured clusters.
### Fix
Restrict 10250/10255/6443 to cluster and admin CIDRs; scan externally quarterly.

## practice ns-quota-scopes
Kinds: Namespace
Severity: INFO
Title: Scope quotas per priority class and scope
Docs:
- https://kubernetes.io/docs/concepts/policy/resource-quotas/#quota-and-cluster-capacity
### Why
Flat quotas block critical Pods while best-effort hogs capacity within the same limit.
### Fix
Add scoped quotas (BestEffort vs NotBestEffort, Terminating vs NotTerminating) per team namespace.

## practice ns-limitrange-defaults
Kinds: Namespace
Severity: INFO
Title: Set LimitRange defaults so every Pod has requests
Docs:
- https://kubernetes.io/docs/concepts/policy/limit-range/
### Why
Without defaults, forgotten requests create BestEffort Pods that evict first and blind HPA.
### Fix
Set default requests/limits plus min/max per namespace; verify with dry-run creates.

## practice ns-allowed-registries
Kinds: Namespace
Severity: INFO
Title: Restrict image registries per namespace
Docs:
- https://kubernetes.io/docs/concepts/containers/images/
### Why
Any-registry namespaces pull typosquatted or stale images without policy friction.
### Fix
Enforce allowed registries via policy agents per namespace tier (prod strictest).

## practice ns-owner-contact-annotation
Kinds: Namespace
Severity: INFO
Title: Require owner contact annotations
Docs:
- https://kubernetes.io/docs/concepts/overview/working-with-objects/annotations/
### Why
Ownerless namespaces stall incident response while responders hunt for humans.
### Fix
Require owner/team/contact annotations at creation; re-verify quarterly.

## practice ns-dr-test-restore
Kinds: Namespace
Severity: INFO
Title: Rehearse namespace restore, not just backup
Docs:
- https://kubernetes.io/docs/concepts/overview/working-with-objects/namespaces/
### Why
Backups without restore rehearsal fail on CRDs, finalizers and cross-namespace refs.
### Fix
Restore each tier namespace to scratch quarterly; time RTO per namespace.

## practice ns-default-deny-audit
Kinds: Namespace
Severity: WARN
Title: Audit that default-deny actually denies
Docs:
- https://kubernetes.io/docs/concepts/services-networking/network-policies/
### Why
Applied-but-unenforced policies (wrong CNI) give false confidence while traffic flows freely.
### Fix
Probe connectivity (allowed vs denied) per namespace after policy changes; alert on enforcement gaps.

## practice ns-resourcequota-vs-limitrange
Kinds: Namespace
Severity: INFO
Title: Pair quotas with LimitRanges or quotas misfire
Docs:
- https://kubernetes.io/docs/concepts/policy/resource-quotas/
### Why
Quotas counting requests fail open when Pods lack requests (counted as zero).
### Fix
Always deploy LimitRange defaults alongside quotas; test with request-less dry-runs.

## practice ns-image-pull-policy-guard
Kinds: Namespace
Severity: INFO
Title: Require explicit pull policies per tier
Docs:
- https://kubernetes.io/docs/concepts/containers/images/#updating-images
### Why
Implicit pull policies differ per tag pattern, causing stale-image surprises per namespace.
### Fix
Require explicit imagePullPolicy in prod namespaces via policy checks.

## practice pv-ebs-az-affinity
Kinds: PersistentVolume
Severity: INFO
Title: Match EBS-style volumes to workload AZs
Docs:
- https://kubernetes.io/docs/concepts/storage/volumes/#awselasticblockstore
### Why
Cross-AZ attach fails or bills egress per I/O for zonal volumes.
### Fix
Constrain both PV topology and Pod affinity to the same zone; verify attach latency.

## practice pv-nfs-idmap-coherence
Kinds: PersistentVolume
Severity: INFO
Title: Keep NFS idmapping coherent across nodes
Docs:
- https://kubernetes.io/docs/concepts/storage/volumes/#nfs
### Why
Squashed or mismatched UID mapping turns correct PVC mounts into permission-denied per node.
### Fix
Standardize idmapd.conf plus fsGroup policy; test mounts from every pool.

## practice pv-ceph-pool-placement
Kinds: PersistentVolume
Severity: INFO
Title: Place Ceph pools per performance tier
Docs:
- https://kubernetes.io/docs/concepts/storage/volumes/#rbd
### Why
Single-pool Ceph mixes DB WAL with backups, letting bulk I/O starve latency-sensitive volumes.
### Fix
Separate pools (replicated SSD vs erasure HDD); map StorageClasses per pool with docs.

## practice pv-ebs-encryption-kms
Kinds: PersistentVolume
Severity: INFO
Title: Encrypt cloud volumes with customer KMS keys
Docs:
- https://kubernetes.io/docs/concepts/storage/storage-classes/
### Why
Default SSE still leaves key control with the provider; compliance needs CMKs.
### Fix
Set encrypted plus kmsKeyId per sensitive StorageClass; verify per-PV encryption.

## practice pv-delete-retention-audit
Kinds: PersistentVolume
Severity: INFO
Title: Audit Delete-retention PVs for data classes
Docs:
- https://kubernetes.io/docs/concepts/storage/persistent-volumes/#reclaiming
### Why
Delete policy on data-class PVs wipes on routine namespace cleanup.
### Fix
Restrict Delete to scratch classes via policy; require Retain for data classes.

## practice pv-longhorn-replica-count
Kinds: PersistentVolume
Severity: INFO
Title: Set Longhorn replica counts per criticality
Docs:
- https://kubernetes.io/docs/concepts/storage/volumes/
### Why
Single-replica Longhorn volumes die with their node despite looking persistent.
### Fix
Set 3 replicas for prod data, 2 minimum; alert on degraded replica counts.

## practice pv-ebs-encryption-kms-dup
Kinds: PersistentVolume
Severity: INFO
Title: Duplicate KMS encryption guard for sensitive PVs
Docs:
- https://kubernetes.io/docs/concepts/storage/storage-classes/
### Why
A second explicit check keeps encryption policy visible in the advisor backfill.
### Fix
Keep encrypted StorageClasses for sensitive namespaces; verify per-PV encryption.

## practice pv-csi-capacity-reporting
Kinds: PersistentVolume
Severity: INFO
Title: Enable CSI capacity reporting for smarter scheduling
Docs:
- https://kubernetes.io/docs/concepts/storage/storage-capacity/
### Why
Without CSIStorageCapacity the scheduler places Pods needing volumes on nodes whose local topology has no capacity left.
### Fix
Enable capacity reporting on CSI drivers; treat missing capacity objects as driver health signal.

## practice pv-documentation-per-class
Kinds: PersistentVolume
Severity: INFO
Title: Document every StorageClass as a product contract
Docs:
- https://kubernetes.io/docs/concepts/storage/storage-classes/
### Why
Undocumented classes force teams to guess performance, encryption, backup and topology behavior per volume, repeating the research every incident.
### Fix
Publish per-class cards: provisioner, performance, encryption, backup policy, topology, expansion behavior, reclaim default. Link from JKubeTerm via class annotations.

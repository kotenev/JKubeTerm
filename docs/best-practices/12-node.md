# Nodes — 30 best practices

*The machines under the Pods: capacity, health, upgrades.*

> **PDF:** `12-node.pdf` — `tools/export-book-pdf.sh` (Chromium `--print-to-pdf`, same as guides/training).

## In JKubeTerm

Open any Nodes row: the **Object** view breaks it into typed sections, **Best practices** cards flag violations with Fix hints, and drill-down jumps to related objects. Practices Wiki (`Help → Practices Wiki…`) holds the same entries with per-user Markdown overrides.

## Practices

### :warning: Audit SSH access to nodes quarterly {#node-audit-ssh-access}

**ID:** `node-audit-ssh-access` · **Severity:** WARN · **Applies to:** Node

**Why it matters.** Standing SSH to nodes bypasses API audit and enables untracked kubelet tweaks.

**Fix in JKubeTerm.** Remove standing SSH; require ephemeral access with session recording and expiry.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/architecture/nodes/)

### :warning: Plan node capacity with headroom for surge and failure {#node-capacity-plan}

**ID:** `node-capacity-plan` · **Severity:** WARN · **Applies to:** Node

**Why it matters.** Nodes at 95% allocatable have no room for rolling-update surge, DaemonSet overhead or a failed peer's Pods, turning routine drains into Pending cascades.

**Fix in JKubeTerm.** Keep allocatable headroom (target under 75% requested on steady state); size node pools for N+1 failure; alert on projected exhaustion 30 days out.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/architecture/nodes/)

### :warning: Match cgroup driver across kubelet and runtime {#node-cgroup-driver-match}

**ID:** `node-cgroup-driver-match` · **Severity:** WARN · **Applies to:** Node

**Why it matters.** Mismatched systemd/cgroupfs drivers cause flaky resource accounting and OOM behavior per node.

**Fix in JKubeTerm.** Pin systemd driver on both; verify in kubelet logs cluster-wide after changes.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/tasks/administer-cluster/kubelet-config-file/)

### :warning: Firewall kubelet and API ports to cluster CIDRs {#node-firewall-kubelet-ports}

**ID:** `node-firewall-kubelet-ports` · **Severity:** WARN · **Applies to:** Node

**Why it matters.** Exposed kubelet ports (10250/10255) allow unauthenticated reads or exec paths on misconfigured clusters.

**Fix in JKubeTerm.** Restrict 10250/10255/6443 to cluster and admin CIDRs; scan externally quarterly.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/architecture/nodes/)

### :warning: Keep node clocks synchronized or everything lies {#node-ntp-sync}

**ID:** `node-ntp-sync` · **Severity:** WARN · **Applies to:** Node

**Why it matters.** Clock skew breaks TLS validation, token expiry, CronJob timing and log correlation simultaneously, producing multi-symptom mysteries with one root cause.

**Fix in JKubeTerm.** Run chrony/ntpd on every node with monitoring on offset; alert above 500ms; include clock check in node bootstrap validation and post-maintenance checks.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/architecture/nodes/)

### :warning: Upgrade node OS and kubelet in lockstep tested order {#node-os-upgrades}

**ID:** `node-os-upgrades` · **Severity:** WARN · **Applies to:** Node

**Why it matters.** Ad-hoc OS patching skews kubelet, containerd and kernel versions across nodes, producing works-on-node-A-only bugs that defy reproduction.

**Fix in JKubeTerm.** Upgrade via managed node groups or kubeadm in dev → staging → prod waves with version skew checks; pin and record OS image per node pool in Git.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/tasks/administer-cluster/kubeadm/kubeadm-upgrade/)

### :warning: React to DiskPressure and MemoryPressure, not just Ready (alias) {#node-pressure}

**ID:** `node-pressure` · **Severity:** WARN · **Applies to:** Node

**Why it matters.** Compatibility alias for the ClusterAdvisor trigger `node-pressure`; canonical guidance lives in `node-pressure-eviction` in this wiki.

**Fix in JKubeTerm.** See `node-pressure-eviction` above for the full JKubeTerm workflow.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/architecture/nodes/#condition)

### :warning: Reserve kubelet, system and eviction headroom explicitly {#node-resource-reservations}

**ID:** `node-resource-reservations` · **Severity:** WARN · **Applies to:** Node

**Why it matters.** Without kube/system reservations and hard eviction thresholds, application Pods consume 100% of the node and starve kubelet, containerd and the OS into wedging the node.

**Fix in JKubeTerm.** Set kube-reserved, system-reserved and eviction-hard (memory.available, nodefs.available) per node size class; verify via /system.slice accounting, not guesses.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/tasks/administer-cluster/reserve-compute-resources/)

### :warning: Handle spot and preemptible interruptions gracefully {#node-spot-interruption}

**ID:** `node-spot-interruption` · **Severity:** WARN · **Applies to:** Node

**Why it matters.** Two-minute interruption notices ignored by apps cause hard kills mid-transaction on the cheapest capacity exactly when savings matter most.

**Fix in JKubeTerm.** Handle termination notices (AWS node-termination-handler, PDBs, graceful periods under 120s); run only fault-tolerant workloads on spot; keep on-demand core for stateful paths.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/scheduling-eviction/node-pressure-eviction/)

### :warning: Keep kubelet versions within skew policy of the API {#node-version-skew-nodes}

**ID:** `node-version-skew-nodes` · **Severity:** WARN · **Applies to:** Node

**Why it matters.** Kubelets too far behind the API server lose feature gates and hit deprecated API removals, failing in ways that look like app bugs per node.

**Fix in JKubeTerm.** Upgrade kubelets within two minors behind the control plane; block node joins outside skew via admission or automation gates; dashboard skew per node pool.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/setup/release/version-skew-policy/)

### :information_source: Collect kubelet and auth logs per node centrally {#node-audit-logging}

**ID:** `node-audit-logging` · **Severity:** INFO · **Applies to:** Node

**Why it matters.** Node-local kubelet logs vanish on node replacement, taking the evidence for wedge, OOM and mount failures with them.

**Fix in JKubeTerm.** Ship kubelet, containerd and auth logs per node to central storage with node-name indexing; retain through at least one upgrade cycle for comparisons.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/tasks/debug/debug-cluster/audit/)

### :information_source: Auto-repair or replace unhealthy nodes {#node-auto-repair}

**ID:** `node-auto-repair` · **Severity:** INFO · **Applies to:** Node

**Why it matters.** Manually nursing NotReady nodes for days leaves workloads degraded while engineers context-switch; humans are slow at the obvious.

**Fix in JKubeTerm.** Enable managed auto-repair (or draino plus autoscaler) with safe drain semantics; alert on repair loops (same node flapping) rather than single repairs.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/architecture/nodes/)

### :information_source: Pin boot images per node pool in Git {#node-boot-image-pinned}

**ID:** `node-boot-image-pinned` · **Severity:** INFO · **Applies to:** Node

**Why it matters.** Rolling boot-image changes under running Pods introduce kernel skew silently.

**Fix in JKubeTerm.** Version node images per pool; roll pools with cordon-drain-verify cycles.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/architecture/nodes/)

### :information_source: Tune image and container garbage collection thresholds {#node-containerd-gc}

**ID:** `node-containerd-gc` · **Severity:** INFO · **Applies to:** Node

**Why it matters.** Default image GC thresholds fill disks with stale layers on high-churn nodes, triggering DiskPressure evictions of application Pods to protect images nobody needs.

**Fix in JKubeTerm.** Set imageMinimumGCAge and thresholds per node class; monitor image filesystem usage separately from container writable layers; alert before GC storms.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/architecture/garbage-collection/)

### :information_source: Cordon before maintenance, drain with budgets in mind {#node-cordon-discipline}

**ID:** `node-cordon-discipline` · **Severity:** INFO · **Applies to:** Node

**Why it matters.** Rebooting uncordoned nodes kills Pods without PDB protection or graceful termination, converting planned maintenance into unplanned outages.

**Fix in JKubeTerm.** Always cordon, then drain with --ignore-daemonsets and PDB awareness; watch eviction progress per workload; uncordon only after node Ready plus agent checks.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/tasks/administer-cluster/safely-drain-node/)

### :information_source: Static CPU manager only with Guaranteed QoS {#node-cpu-manager-static}

**ID:** `node-cpu-manager-static` · **Severity:** INFO · **Applies to:** Node

**Why it matters.** Static policy without Guaranteed QoS silently does nothing, yet teams assume pinning works.

**Fix in JKubeTerm.** Pair static policy with Guaranteed Pods; verify CPUAffinity in Pod status.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/tasks/administer-cluster/cpu-management-policies/)

### :information_source: Separate OS, container and etcd disks on control plane {#node-disk-separation}

**ID:** `node-disk-separation` · **Severity:** INFO · **Applies to:** Node

**Why it matters.** etcd sharing a disk with container image churn suffers fsync latency spikes that destabilize the whole control plane under exactly the load peaks that matter.

**Fix in JKubeTerm.** Give etcd dedicated low-latency disks (SSD/NVMe) on control-plane nodes; separate container storage from OS root; monitor disk latency percentiles, not just usage.

**Official docs:** [etcd.io](https://etcd.io/docs/latest/op-guide/hardware/)

### :information_source: Bound drain times with pod-eviction timeouts {#node-drain-timeouts}

**ID:** `node-drain-timeouts` · **Severity:** INFO · **Applies to:** Node

**Why it matters.** A single PDB-blocked or finalizer-stuck Pod halts node maintenance forever while the upgrade window burns, with no escalation path.

**Fix in JKubeTerm.** Set drain timeouts with --timeout plus alerting on stuck evictions; pre-check PDBs and finalizers before maintenance windows; document force-escalation criteria.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/tasks/administer-cluster/safely-drain-node/)

### :information_source: Taint specialized hardware so only intended Pods land {#node-gpu-taints}

**ID:** `node-gpu-taints` · **Severity:** INFO · **Applies to:** Node

**Why it matters.** General workloads scheduled on GPU or high-memory nodes waste expensive capacity and get evicted the moment real GPU jobs arrive.

**Fix in JKubeTerm.** Taint GPU/ARM/spot-special nodes with matching tolerations only on intended workloads; verify with dry-run scheduling before trusting defaults.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/scheduling-eviction/taint-and-toleration/)

### :information_source: Share GPUs with time-slicing deliberately {#node-gpu-time-slicing}

**ID:** `node-gpu-time-slicing` · **Severity:** INFO · **Applies to:** Node

**Why it matters.** Whole-GPU allocation for tiny inference wastes expensive accelerators.

**Fix in JKubeTerm.** Use device-plugin time-slicing or MPS with quotas; monitor per-share utilization.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/tasks/manage-gpus/scheduling-gpus/)

### :information_source: Pre-pull critical images to beat cold starts {#node-image-prepull}

**ID:** `node-image-prepull` · **Severity:** INFO · **Applies to:** Node

**Why it matters.** First schedule on a fresh node pulls gigabytes while users wait, and registry blips during scale-up cascade into Pending storms.

**Fix in JKubeTerm.** Pre-pull critical images via DaemonSet (pause plus app images) or node image cache warming on autoscaler scale-up hooks; monitor pull durations per image.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/containers/images/)

### :information_source: Tune sysctls for connection-heavy workloads deliberately {#node-kernel-tuning}

**ID:** `node-kernel-tuning` · **Severity:** INFO · **Applies to:** Node

**Why it matters.** Default conntrack, somaxconn and file limits saturate under load-balancer or mesh traffic, dropping connections with kernel-level errors apps never see.

**Fix in JKubeTerm.** Set namespaced sysctls per Pod where possible, node sysctls via kubelet config where required; load-test to find ceilings; document every non-default sysctl with rationale.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/tasks/administer-cluster/sysctl-cluster/)

### :information_source: Derive kube-reserved from node size, not folklore {#node-kube-reserved-math}

**ID:** `node-kube-reserved-math` · **Severity:** INFO · **Applies to:** Node

**Why it matters.** Copy-pasted reservations under-protect small nodes and over-tax large ones.

**Fix in JKubeTerm.** Compute reservations per instance size (percentage plus floor); verify via allocatable math.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/tasks/administer-cluster/reserve-compute-resources/)

### :information_source: Manage kubelet config centrally, not per-node flags {#node-kubelet-config}

**ID:** `node-kubelet-config` · **Severity:** INFO · **Applies to:** Node

**Why it matters.** Hand-tuned kubelet flags drift across nodes (different eviction thresholds, log rotation, authz modes), making behavior node-dependent and upgrades risky.

**Fix in JKubeTerm.** Use kubelet config files versioned in Git (or managed node groups); diff running vs desired config quarterly; never SSH-tune single nodes.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/tasks/administer-cluster/kubelet-config-file/)

### :information_source: Govern node labels like an API {#node-labels-hygiene}

**ID:** `node-labels-hygiene` · **Severity:** INFO · **Applies to:** Node

**Why it matters.** Ad-hoc node labels (`ssd=true`, `gpu2`) proliferate spellings that selectors miss, so workloads land on wrong hardware or nowhere with cryptic affinity errors.

**Fix in JKubeTerm.** Define the allowed label vocabulary (topology, hardware, pool) in docs; validate in CI; deprecate renames with dual-label transition periods.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/overview/working-with-objects/labels/)

### :information_source: Tune max-pods to IP and density reality {#node-max-pods-density}

**ID:** `node-max-pods-density` · **Severity:** INFO · **Applies to:** Node

**Why it matters.** Default 110 pods/node exhausts IPs or crashes density before CPU fills, or wastes IPs on small nodes.

**Fix in JKubeTerm.** Set max-pods per pool from IP budget and workload size; monitor pod-density vs IP exhaustion.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/architecture/nodes/)

### :information_source: Run node-problem-detector for kernel and hardware faults {#node-problem-detector}

**ID:** `node-problem-detector` · **Severity:** INFO · **Applies to:** Node

**Why it matters.** Kernel oopses, disk errors and NTP drift stay invisible to Kubernetes until Pods mysteriously fail; node conditions show only kubelet-level health.

**Fix in JKubeTerm.** Deploy node-problem-detector with log monitors mapping to node conditions plus taints; alert on KernelDeadlock and disk errors; cordon automatically on hardware faults.

**Official docs:** [github.com](https://github.com/kubernetes/node-problem-detector)

### :information_source: Enable seccomp default at kubelet level {#node-seccomp-default}

**ID:** `node-seccomp-default` · **Severity:** INFO · **Applies to:** Node

**Why it matters.** Per-Pod seccomp relies on every author remembering; one forgotten manifest widens that Pod's syscall surface permanently.

**Fix in JKubeTerm.** Enable the kubelet seccompDefault (stable on modern clusters) so RuntimeDefault applies unless explicitly overridden; audit overrides quarterly.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/tasks/configure-pod-container/security-context/)

### :information_source: Keep swap disabled or account it explicitly {#node-swap-disabled-policy}

**ID:** `node-swap-disabled-policy` · **Severity:** INFO · **Applies to:** Node

**Why it matters.** Partial swap enablement produces unpredictable performance and eviction behavior per node.

**Fix in JKubeTerm.** Keep swap off (default) or adopt NodeSwap feature deliberately with limits per workload.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/tasks/administer-cluster/kubelet-config-file/)

### :information_source: Pin TopologyManager for latency-sensitive Pods {#node-topology-manager-policy}

**ID:** `node-topology-manager-policy` · **Severity:** INFO · **Applies to:** Node

**Why it matters.** Cross-NUMA memory access adds tail latency invisible in averages.

**Fix in JKubeTerm.** Use single-numa-node policy with static CPU manager for DPDK/ML inference Pods.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/tasks/administer-cluster/cpu-management-policies/)

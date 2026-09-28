# PersistentVolumeClaims — 30 best practices

*Requests for disk that must bind and survive.*

> **PDF:** `10-pvc.pdf` — `tools/export-book-pdf.sh` (Chromium `--print-to-pdf`, same as guides/training).

## In JKubeTerm

Open any PersistentVolumeClaims row: the **Object** view breaks it into typed sections, **Best practices** cards flag violations with Fix hints, and drill-down jumps to related objects. Practices Wiki (`Help → Practices Wiki…`) holds the same entries with per-user Markdown overrides.

## Practices

### :warning: Alert on inode exhaustion, not just bytes {#pvc-alert-inodes}

**ID:** `pvc-alert-inodes` · **Severity:** WARN · **Applies to:** PersistentVolumeClaim

**Why it matters.** Millions of tiny files exhaust inodes while byte gauges look healthy; writes fail with ENOSPC confusion.

**Fix in JKubeTerm.** Monitor inode usage per volume alongside bytes; alert on both.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/storage/persistent-volumes/)

### :warning: Request realistic storage with growth headroom {#pvc-capacity-planning}

**ID:** `pvc-capacity-planning` · **Severity:** WARN · **Applies to:** PersistentVolumeClaim

**Why it matters.** Undersized PVCs fill up silently until apps crash on ENOSPC, and many StorageClasses forbid or complicate online expansion, turning a small misestimate into a migration.

**Fix in JKubeTerm.** Size to 12-month growth plus 30% headroom; prefer allowVolumeExpansion StorageClasses; alert at 70/85/95% with runbooked expansion steps tested in staging.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/storage/persistent-volumes/)

### :warning: Protect production PVCs from accidental deletion {#pvc-delete-protection}

**ID:** `pvc-delete-protection` · **Severity:** WARN · **Applies to:** PersistentVolumeClaim

**Why it matters.** `kubectl delete ns` or a GitOps prune removes PVCs as casually as ConfigMaps, and Retain policies still strand data in hard-to-reattach states.

**Fix in JKubeTerm.** Guard prod PVCs with finalizers or policy agents (Kyverno/Ops), require manual confirmation runbooks for deletes, and snapshot before any planned removal.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/storage/persistent-volumes/)

### :warning: Check retention before deleting bound PVCs {#pvc-delete-retention-check}

**ID:** `pvc-delete-retention-check` · **Severity:** WARN · **Applies to:** PersistentVolumeClaim

**Why it matters.** Deleting a PVC with Delete-retention wipes the PV data instantly with no recycle bin.

**Fix in JKubeTerm.** Snapshot first; confirm reclaim policy; require two-person approval for prod deletes.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/storage/persistent-volumes/#reclaiming)

### :warning: Never leave storageClassName empty unintentionally {#pvc-empty-storage-class}

**ID:** `pvc-empty-storage-class` · **Severity:** WARN · **Applies to:** PersistentVolumeClaim

**Why it matters.** Empty storageClassName binds only to no-class PVs (or the default, depending on admission), producing Pending PVCs whose cause hides in admission plugin behavior.

**Fix in JKubeTerm.** Always set storageClassName explicitly (or `""` deliberately for no-class binding with a comment); lint manifests for missing class fields.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/storage/persistent-volumes/#class-1)

### :warning: Encrypt storage for sensitive data classes {#pvc-encryption-at-rest}

**ID:** `pvc-encryption-at-rest` · **Severity:** WARN · **Applies to:** PersistentVolumeClaim

**Why it matters.** Unencrypted volumes expose data to anyone with snapshot, host or cloud-console access, violating compliance for PII, secrets-adjacent and financial data.

**Fix in JKubeTerm.** Use encrypted StorageClasses (cloud KMS or LUKS) for sensitive namespaces; verify encryption status per PV and rotate keys per policy.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/tasks/administer-cluster/encrypt-data/) · [kubernetes.io](https://kubernetes.io/docs/concepts/storage/storage-classes/)

### :warning: Guard against multi-attach with topology and strategy {#pvc-multi-attach-guard}

**ID:** `pvc-multi-attach-guard` · **Severity:** WARN · **Applies to:** PersistentVolumeClaim

**Why it matters.** Rolling updates briefly run two Pods against one RWO volume during handover; without Recreate strategy the new Pod pends and the rollout stalls mid-way.

**Fix in JKubeTerm.** Pair RWO claims with Recreate strategy (or RWOP where supported); alert on Multi-Attach errors as rollout-blockers, not storage bugs.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/storage/persistent-volumes/#access-modes)

### :warning: Back up PVC data independently of retention {#pvc-retention-backup}

**ID:** `pvc-retention-backup` · **Severity:** WARN · **Applies to:** PersistentVolumeClaim

**Why it matters.** Retention policies and namespace deletes wipe PVCs by design; without separate backups the "working as intended" deletion is also unrecoverable data loss.

**Fix in JKubeTerm.** Snapshot or Velero-backup every PVC holding non-reproducible data on a schedule; test restores to scratch namespaces; alert on backup age, not just backup success.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/storage/volume-snapshots/)

### :warning: ReadWriteOnce volumes allow a single writer (alias) {#pvc-rwo-replicas}

**ID:** `pvc-rwo-replicas` · **Severity:** WARN · **Applies to:** PersistentVolumeClaim, Deployment, StatefulSet

**Why it matters.** Compatibility alias for the ClusterAdvisor trigger `pvc-rwo-replicas`; canonical guidance lives in `pvc-rwo-single-writer` in this wiki.

**Fix in JKubeTerm.** See `pvc-rwo-single-writer` above for the full JKubeTerm workflow.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/storage/persistent-volumes/#access-modes)

### :warning: Snapshot stateful PVCs on a schedule with retention {#pvc-snapshot-schedule}

**ID:** `pvc-snapshot-schedule` · **Severity:** WARN · **Applies to:** PersistentVolumeClaim

**Why it matters.** Ad-hoc snapshots before risky changes miss the incident that happens on a quiet Tuesday; no schedule means no recovery point.

**Fix in JKubeTerm.** Automate snapshot schedules (hourly/daily per criticality) with retention windows; monitor snapshot age and size growth, not just job success.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/storage/volume-snapshots/)

### :warning: Every PVC needs a StorageClass or a bound PV (alias) {#pvc-storage-class}

**ID:** `pvc-storage-class` · **Severity:** WARN · **Applies to:** PersistentVolumeClaim

**Why it matters.** Compatibility alias for the ClusterAdvisor trigger `pvc-storage-class`; canonical guidance lives in `pvc-class-or-pv` in this wiki.

**Fix in JKubeTerm.** See `pvc-class-or-pv` above for the full JKubeTerm workflow.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/storage/persistent-volumes/)

### :information_source: Audit accessModes against real mount counts {#pvc-access-modes-audit}

**ID:** `pvc-access-modes-audit` · **Severity:** INFO · **Applies to:** PersistentVolumeClaim

**Why it matters.** RWO claims mounted by two Deployments (or a DaemonSet) deadlock the second consumer in Multi-Attach with no clear error at Apply time.

**Fix in JKubeTerm.** List actual mount counts per PVC quarterly; migrate shared-read needs to RWX/ROX drivers; enforce Recreate strategy for RWO single-writers in review.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/storage/persistent-volumes/#access-modes)

### :information_source: Lint accessModes in CI {#pvc-accessmode-review-ci}

**ID:** `pvc-accessmode-review-ci` · **Severity:** INFO · **Applies to:** PersistentVolumeClaim

**Why it matters.** RWO-vs-RWX mistakes surface only at multi-attach runtime, not at apply time.

**Fix in JKubeTerm.** Lint accessModes against replica counts and strategies in CI; block RWO multi-writer.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/storage/persistent-volumes/#access-modes)

### :information_source: Let apps own scratch PVC lifecycle via owners {#pvc-app-managed-cleanup}

**ID:** `pvc-app-managed-cleanup` · **Severity:** INFO · **Applies to:** PersistentVolumeClaim

**Why it matters.** Scratch PVCs outlive the Jobs that created them without ownerReferences, accumulating billable disks nobody dares delete.

**Fix in JKubeTerm.** Set ownerReferences from scratch PVCs to their Jobs (or use TTL patterns); audit orphan PVCs monthly with creation-age reports.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/overview/working-with-objects/owners-dependents/)

### :information_source: Test clones as restore rehearsal {#pvc-clone-test-restore}

**ID:** `pvc-clone-test-restore` · **Severity:** INFO · **Applies to:** PersistentVolumeClaim

**Why it matters.** Untested clone paths fail during real incidents with driver-specific errors.

**Fix in JKubeTerm.** Clone to scratch namespaces quarterly; time the operation for RTO math.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/storage/volume-pvc-datasource/)

### :information_source: Clone and restore via dataSource, not manual copies {#pvc-data-source}

**ID:** `pvc-data-source` · **Severity:** INFO · **Applies to:** PersistentVolumeClaim

**Why it matters.** Manual `kubectl cp` restores are slow, lossy on permissions, and unrepeatable; snapshot-clone semantics exist precisely for fast consistent copies.

**Fix in JKubeTerm.** Use `dataSource` (PVC or VolumeSnapshot) for clones and restores; document source retention separately from clone lifecycle.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/storage/volume-pvc-datasource/)

### :information_source: Test volume expansion before you need it {#pvc-expansion-testing}

**ID:** `pvc-expansion-testing` · **Severity:** INFO · **Applies to:** PersistentVolumeClaim

**Why it matters.** Expansion fails exactly during incidents: filesystem resize needs Pod restarts, some drivers allow only offline expansion, and quotas block the new size.

**Fix in JKubeTerm.** Rehearse expansion per StorageClass in staging (edit requests.storage, restart Pods, verify filesystem); document online vs offline behavior per driver in the runbook.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/storage/persistent-volumes/#expanding-persistent-volumes-claims)

### :information_source: Set fstype explicitly per StorageClass {#pvc-fs-type}

**ID:** `pvc-fs-type` · **Severity:** INFO · **Applies to:** PersistentVolumeClaim

**Why it matters.** Default filesystems vary by provisioner (ext4 vs xfs), and apps sensitive to reflink, discard or journaling behave differently with no manifest hint why.

**Fix in JKubeTerm.** Pin `csi.storage.k8s.io/fstype` per class (xfs for databases, ext4 general); document the choice and mount options alongside the class definition.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/storage/storage-classes/)

### :information_source: Set fsGroup policy for shared writable volumes {#pvc-fsgroup-policy}

**ID:** `pvc-fsgroup-policy` · **Severity:** INFO · **Applies to:** PersistentVolumeClaim

**Why it matters.** Multi-container Pods with different UIDs hit permission denied on shared volumes when fsGroup is unset, producing app errors that look like storage failures.

**Fix in JKubeTerm.** Set `fsGroupPolicy: File` (or ReadWriteOnceWithFSType) plus Pod `fsGroup`; verify group ownership inside Pods after first mount.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/storage/storage-classes/)

### :information_source: Schedule fstrim on thin-provisioned volumes {#pvc-fstrim-schedule}

**ID:** `pvc-fstrim-schedule` · **Severity:** INFO · **Applies to:** PersistentVolumeClaim

**Why it matters.** Untrimmed thin volumes grow forever as deletes never return blocks to the pool.

**Fix in JKubeTerm.** Run periodic fstrim (CronJob or driver policy) on thin classes; track reclaimed bytes.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/storage/persistent-volumes/)

### :information_source: Match StorageClass performance to workload IOPS {#pvc-iops-class}

**ID:** `pvc-iops-class` · **Severity:** INFO · **Applies to:** PersistentVolumeClaim

**Why it matters.** Databases on throughput-optimized or HDD-backed classes suffer tail-latency spikes misdiagnosed as app issues, while premium SSD for logs wastes budget.

**Fix in JKubeTerm.** Define classes per tier (fast-ssd for DBs, standard for general, cold for archives) with documented IOPS; benchmark per class in the lab and label PVCs accordingly.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/storage/storage-classes/)

### :information_source: Label PVCs with owner and purpose {#pvc-label-owner}

**ID:** `pvc-label-owner` · **Severity:** INFO · **Applies to:** PersistentVolumeClaim

**Why it matters.** Ownerless volumes block safe deletion; nobody knows which app breaks if the PVC goes.

**Fix in JKubeTerm.** Label app, team and purpose on every PVC; surface in JKubeTerm Object view.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/storage/persistent-volumes/)

### :information_source: Expose kubelet volume metrics per PVC {#pvc-metrics-capacity}

**ID:** `pvc-metrics-capacity` · **Severity:** INFO · **Applies to:** PersistentVolumeClaim

**Why it matters.** Missing volume stats leave usage invisible until ENOSPC crashes the app.

**Fix in JKubeTerm.** Ensure kubelet volume metrics plus Prometheus rules per PVC; dashboard bytes and inodes.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/storage/persistent-volumes/)

### :information_source: Tune mountOptions (noatime, discard) per workload {#pvc-mount-options}

**ID:** `pvc-mount-options` · **Severity:** INFO · **Applies to:** PersistentVolumeClaim

**Why it matters.** Default relatime writes on every read and missing discard leaves SSD garbage uncollected, costing IOPS and device lifespan on busy volumes.

**Fix in JKubeTerm.** Set noatime for read-heavy, discard for SSD-backed classes; verify via /proc/mounts inside Pods after provisioning.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/storage/persistent-volumes/)

### :information_source: Baseline volume performance before blaming the app {#pvc-performance-baseline}

**ID:** `pvc-performance-baseline` · **Severity:** INFO · **Applies to:** PersistentVolumeClaim

**Why it matters.** Slow queries blamed on code are often slow disks, but without a baseline nobody can prove whether the volume or the query regressed.

**Fix in JKubeTerm.** fio-baseline every StorageClass at provisioning and quarterly; record expected IOPS/latency in the class docs; compare incident volumes against baseline first.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/storage/persistent-volumes/)

### :information_source: Co-schedule Pods with their volumes zones {#pvc-pod-affinity-zone}

**ID:** `pvc-pod-affinity-zone` · **Severity:** INFO · **Applies to:** PersistentVolumeClaim

**Why it matters.** Cross-zone attach adds latency and egress per I/O or fails outright.

**Fix in JKubeTerm.** Pair allowedTopologies with Pod affinity so compute follows storage zones.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/storage/storage-classes/#allowed-topologies)

### :information_source: Quota PVC counts and total storage per namespace {#pvc-quota-per-team}

**ID:** `pvc-quota-per-team` · **Severity:** INFO · **Applies to:** PersistentVolumeClaim, Namespace

**Why it matters.** One team's unbounded PVC creation exhausts provisioner capacity or cloud quotas, blocking every other team's deploys with cryptic provision errors.

**Fix in JKubeTerm.** Add ResourceQuota (persistentvolumeclaims count + requests.storage) per team namespace; alert at 80% with expansion request workflow.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/policy/resource-quotas/)

### :information_source: Keep quotas in sync when expanding volumes {#pvc-resize-quota-sync}

**ID:** `pvc-resize-quota-sync` · **Severity:** INFO · **Applies to:** PersistentVolumeClaim

**Why it matters.** Expansion past quota fails with Forbidden errors misread as provisioner bugs, stalling incident response while disks fill.

**Fix in JKubeTerm.** Raise the namespace storage quota before or with the PVC expansion request; automate quota bump in the expansion runbook.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/policy/resource-quotas/)

### :information_source: Bind to pre-provisioned PVs with selectors deliberately {#pvc-selector-binding}

**ID:** `pvc-selector-binding` · **Severity:** INFO · **Applies to:** PersistentVolumeClaim

**Why it matters.** Label selectors on claims that almost match bind the wrong PV (wrong zone, wrong performance tier) with no warning at bind time.

**Fix in JKubeTerm.** Use selectors only for hand-crafted PV pools with documented labels; otherwise rely on StorageClass dynamic provisioning and drop selectors.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/storage/persistent-volumes/)

### :information_source: Constrain volumes with allowedTopologies {#pvc-topology-labels}

**ID:** `pvc-topology-labels` · **Severity:** INFO · **Applies to:** PersistentVolumeClaim

**Why it matters.** Volumes provisioned in zone A with Pods scheduled in zone B either fail to attach or incur cross-zone latency and egress on every I/O.

**Fix in JKubeTerm.** Set allowedTopologies per StorageClass matching workload zones; combine with Pod topologySpreadConstraints so compute follows storage.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/storage/storage-classes/#allowed-topologies)

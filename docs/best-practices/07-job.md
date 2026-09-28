# Jobs — 30 best practices

*Run-to-completion work that must actually finish.*

> **PDF:** `07-job.pdf` — `tools/export-book-pdf.sh` (Chromium `--print-to-pdf`, same as guides/training).

## In JKubeTerm

Open any Jobs row: the **Object** view breaks it into typed sections, **Best practices** cards flag violations with Fix hints, and drill-down jumps to related objects. Practices Wiki (`Help → Practices Wiki…`) holds the same entries with per-user Markdown overrides.

## Practices

### :warning: Bound every Job with activeDeadlineSeconds {#job-deadline}

**ID:** `job-deadline` · **Severity:** WARN · **Applies to:** Job

**Why it matters.** A hung migration holding a lock blocks all subsequent deploys indefinitely while looking merely slow, and retries pile more hung copies on top.

**Fix in JKubeTerm.** Set `activeDeadlineSeconds` matching the worst sane runtime plus margin; alert on DeadlineExceeded. Pair with backoffLimit so failures surface fast.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/controllers/job/#job-termination-and-cleanup)

### :warning: Make every Job idempotent and re-runnable {#job-idempotency}

**ID:** `job-idempotency` · **Severity:** WARN · **Applies to:** Job

**Why it matters.** Retries, duplicate CronJob ticks and operator replays run the same Job twice; non-idempotent migrations double-apply, double-charge or corrupt data.

**Fix in JKubeTerm.** Design tasks as safe to re-run (upserts, guarded migrations, deduplicated side effects); test by running the Job twice against staging and diffing results.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/controllers/job/)

### :warning: Job Pods must use restartPolicy Never or OnFailure (alias) {#job-restart-policy}

**ID:** `job-restart-policy` · **Severity:** WARN · **Applies to:** Job

**Why it matters.** Compatibility alias for the ClusterAdvisor trigger `job-restart-policy`; canonical guidance lives in `job-restart-never` in this wiki.

**Fix in JKubeTerm.** See `job-restart-never` above for the full JKubeTerm workflow.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/controllers/job/#pod-template)

### :warning: Run Jobs with least-privilege ServiceAccounts {#job-serviceaccount-least}

**ID:** `job-serviceaccount-least` · **Severity:** WARN · **Applies to:** Job

**Why it matters.** Migrations and batch Jobs often run as default or admin accounts, so a compromised batch image inherits full namespace (or cluster) rights for the Job's lifetime.

**Fix in JKubeTerm.** Create dedicated ServiceAccounts per Job class with only the verbs and resources the task needs; set automount false where the Job never calls the API.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/tasks/configure-pod-container/configure-service-account/)

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

### :information_source: Match imagePullPolicy to the tagging strategy (alias) {#image-pull-policy}

**ID:** `image-pull-policy` · **Severity:** INFO · **Applies to:** Pod, Deployment, StatefulSet, DaemonSet, Job, CronJob

**Why it matters.** Compatibility alias for the ClusterAdvisor trigger `image-pull-policy`; canonical guidance lives in `pod-image-pull-policy` in this wiki.

**Fix in JKubeTerm.** See `pod-image-pull-policy` above for the full JKubeTerm workflow.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/containers/images/#updating-images)

### :information_source: Bound Job retries with backoffLimit (alias) {#job-backoff}

**ID:** `job-backoff` · **Severity:** INFO · **Applies to:** Job

**Why it matters.** Compatibility alias for the ClusterAdvisor trigger `job-backoff`; canonical guidance lives in `job-backoff-limit` in this wiki.

**Fix in JKubeTerm.** See `job-backoff-limit` above for the full JKubeTerm workflow.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/controllers/job/)

### :information_source: Label every Job run with run-id and trigger {#job-labels-run-id}

**ID:** `job-labels-run-id` · **Severity:** INFO · **Applies to:** Job

**Why it matters.** Identical Job names across reruns confuse logs, drill-down and audit about which run did what.

**Fix in JKubeTerm.** Inject run-id plus trigger (manual/cron/backfill) labels per run; filter by them in JKubeTerm.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/overview/working-with-objects/labels/)

### :information_source: Ship Job logs before TTL deletes them {#job-log-retention}

**ID:** `job-log-retention` · **Severity:** INFO · **Applies to:** Job

**Why it matters.** TTL cleanup deletes the only copy of migration output exactly when auditors or incident review ask what the Job did last Tuesday.

**Fix in JKubeTerm.** Ship Job Pod logs to centralized logging (or object storage) as part of the Job pipeline; keep `kubectl logs job/name` working only as a short-term convenience.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/cluster-administration/logging/)

### :information_source: Keep a manual Job trigger for every CronJob {#job-manual-trigger}

**ID:** `job-manual-trigger` · **Severity:** INFO · **Applies to:** Job

**Why it matters.** CronJobs cannot be run ad-hoc without crafting manifests under pressure, so backfills and incident replays start with YAML archaeology.

**Fix in JKubeTerm.** Document `kubectl create job --from=cronjob/name manual-run` (or an Argo Workflow equivalent) in the runbook; test the manual path quarterly.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/controllers/cron-jobs/)

### :information_source: SLO Job durations and alert on drift {#job-monitor-duration-slo}

**ID:** `job-monitor-duration-slo` · **Severity:** INFO · **Applies to:** Job

**Why it matters.** Gradually slowing Jobs (data growth) cross batch windows silently until they overlap the next tick.

**Fix in JKubeTerm.** Record duration histograms per Job; alert on p95 drift week over week.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/controllers/job/)

### :information_source: Fence batch Jobs onto batch node pools {#job-node-selector-batch}

**ID:** `job-node-selector-batch` · **Severity:** INFO · **Applies to:** Job

**Why it matters.** Batch floods on serving nodes starve latency-sensitive Pods of CPU and disk.

**Fix in JKubeTerm.** Use nodeSelector/affinity to batch pools with taints; keep serving pools clean.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/scheduling-eviction/assign-pod-node/)

### :information_source: Notify on Job completion and failure, not just failure {#job-notify-completion}

**ID:** `job-notify-completion` · **Severity:** INFO · **Applies to:** Job

**Why it matters.** Silent success means nobody notices when a nightly Job stops being scheduled at all — success alerts missing is itself the signal, but only if success normally alerts.

**Fix in JKubeTerm.** Emit completion events to chat/metrics for critical Jobs (success and failure); alert on absence of success within the expected window, not only on failures.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/controllers/job/)

### :information_source: Parallelize with indexed completion mode {#job-parallelism-indexed}

**ID:** `job-parallelism-indexed` · **Severity:** INFO · **Applies to:** Job

**Why it matters.** Non-indexed parallel Jobs duplicate or skip work items because workers cannot tell which shard is theirs, causing double-processing or gaps in batch pipelines.

**Fix in JKubeTerm.** Use `completionMode: Indexed` with `completions: N` so each Pod gets `JOB_COMPLETION_INDEX`; shard deterministically by index. Verify index env propagation in Pod logs.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/controllers/job/#completion-mode)

### :information_source: Fail fast on fatal errors with podFailurePolicy {#job-pod-failure-policy}

**ID:** `job-pod-failure-policy` · **Severity:** INFO · **Applies to:** Job

**Why it matters.** Backoff retries treat OutOfMemory and config errors the same as transient network blips, burning hours retrying jobs that can never succeed.

**Fix in JKubeTerm.** Add `podFailurePolicy` rules failing fast on OOMKilled, InvalidImageName and config exits while retrying only transient codes. Available on recent clusters — check server version first.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/controllers/job/#pod-failure-policy)

### :information_source: Run batch Jobs below serving priority {#job-priority-low}

**ID:** `job-priority-low` · **Severity:** INFO · **Applies to:** Job

**Why it matters.** Batch bursts preempt serving Pods when priorities tie or batch outranks accidentally.

**Fix in JKubeTerm.** Assign lower PriorityClass plus preemptionPolicy Never to batch Jobs.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/scheduling-eviction/pod-priority-preemption/)

### :information_source: Size Job resources for bursts, quota the namespace {#job-resource-quotas}

**ID:** `job-resource-quotas` · **Severity:** INFO · **Applies to:** Job

**Why it matters.** Parallel Jobs burst to hundreds of Pods and exhaust namespace quotas or node capacity, starving serving workloads sharing the cluster.

**Fix in JKubeTerm.** Set per-Job requests/limits plus namespace ResourceQuotas; schedule heavy batches in off-peak windows or dedicated node pools with taints.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/policy/resource-quotas/)

### :information_source: Job successPolicy can short-circuit completions on 1.36 and newer (alias) {#job-success-policy}

**ID:** `job-success-policy` · **Severity:** INFO · **Applies to:** Job

**Why it matters.** Compatibility alias for the ClusterAdvisor trigger `job-success-policy`; canonical guidance lives in `job-success-policy` in this wiki.

**Fix in JKubeTerm.** See `job-success-shortcircuit` above for the full JKubeTerm workflow.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/controllers/job/)

### :information_source: Use suspend for dry-run validation {#job-suspend-flag}

**ID:** `job-suspend-flag` · **Severity:** INFO · **Applies to:** Job

**Why it matters.** Untested Jobs fire immediately on apply in production namespaces during reviews.

**Fix in JKubeTerm.** Create suspended, review rendered spec, then unsuspend; wire this into GitOps PR checks.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/controllers/job/#suspending-a-job)

### :information_source: Auto-clean finished Jobs with ttlSecondsAfterFinished {#job-ttl-cleanup}

**ID:** `job-ttl-cleanup` · **Severity:** INFO · **Applies to:** Job

**Why it matters.** Completed Jobs and their Pods accumulate forever, cluttering listings, slowing API queries and confusing drill-down with hundreds of stale entries.

**Fix in JKubeTerm.** Set `ttlSecondsAfterFinished` (e.g. 3600 for debugging window, 300 for routine) on every Job and CronJob template. Keep failed Jobs longer than successful ones.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/controllers/job/#clean-up-finished-jobs-automatically)

### :information_source: Keep failed Jobs longer than successful ones {#job-ttl-failed-longer}

**ID:** `job-ttl-failed-longer` · **Severity:** INFO · **Applies to:** Job

**Why it matters.** Equal TTL deletes failure evidence before morning triage while successful clutter lingers.

**Fix in JKubeTerm.** Set longer TTL for failures via separate cleanup policies or manual holds on failure labels.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/controllers/job/#clean-up-finished-jobs-automatically)

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

### :information_source: Set restartPolicy deliberately on bare Pods and Jobs {#pod-restart-policy}

**ID:** `pod-restart-policy` · **Severity:** INFO · **Applies to:** Pod, Job, CronJob

**Why it matters.** Bare Pods default to Always, which silently restarts one-shot scripts forever instead of reporting failure, while Jobs reject Always outright and never start.

**Fix in JKubeTerm.** Use Always for long-running bare Pods, OnFailure for retried scripts, Never for run-once debugging. Job and CronJob templates accept only Never or OnFailure — the API rejects the rest.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/#restart-policy)

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

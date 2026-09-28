# CronJobs — 30 best practices

*Scheduled work that must fire, once, on time.*

> **PDF:** `08-cronjob.pdf` — `tools/export-book-pdf.sh` (Chromium `--print-to-pdf`, same as guides/training).

## In JKubeTerm

Open any CronJobs row: the **Object** view breaks it into typed sections, **Best practices** cards flag violations with Fix hints, and drill-down jumps to related objects. Practices Wiki (`Help → Practices Wiki…`) holds the same entries with per-user Markdown overrides.

## Practices

### :warning: Choose concurrencyPolicy deliberately, default is Allow {#cron-concurrency}

**ID:** `cron-concurrency` · **Severity:** WARN · **Applies to:** CronJob

**Why it matters.** Allow overlaps long-running batches (double writes, lock fights) while Forbid silently skips ticks that look like successes in dashboards.

**Fix in JKubeTerm.** Use Forbid for non-overlappable work, Replace for latest-wins polling; document the choice and alert on skipped (Forbid) or replaced ticks.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/controllers/cron-jobs/)

### :warning: Set startingDeadlineSeconds on every CronJob {#cron-deadlines}

**ID:** `cron-deadlines` · **Severity:** WARN · **Applies to:** CronJob

**Why it matters.** Without a deadline a missed schedule (controller down, clock skew) silently never runs, and nobody notices the report that never arrived.

**Fix in JKubeTerm.** Set `startingDeadlineSeconds` (e.g. 200) so missed starts count as failures and alert; pair with failedJobsHistoryLimit high enough to investigate.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/controllers/cron-jobs/)

### :warning: Alert on missed and failed CronJob ticks {#cron-monitor-missed}

**ID:** `cron-monitor-missed` · **Severity:** WARN · **Applies to:** CronJob

**Why it matters.** CronJob status shows last schedule time but nobody watches it; a suspended or broken CronJob fails by silence, discovered only when stakeholders complain.

**Fix in JKubeTerm.** Alert on `kube_cronjob_status_last_successful_time` staleness and on failed Jobs per CronJob; dashboard next-schedule vs last-success gap.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/controllers/cron-jobs/)

### :warning: Cron schedules must be valid 5-field expressions (alias) {#cron-schedule}

**ID:** `cron-schedule` · **Severity:** WARN · **Applies to:** CronJob

**Why it matters.** Compatibility alias for the ClusterAdvisor trigger `cron-schedule`; canonical guidance lives in `cronjob-schedule-valid` in this wiki.

**Fix in JKubeTerm.** See `cronjob-schedule-valid` above for the full JKubeTerm workflow.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/controllers/cron-jobs/#cron-schedule-syntax)

### :warning: Pin timeZone explicitly on every CronJob {#cron-timezone}

**ID:** `cron-timezone` · **Severity:** WARN · **Applies to:** CronJob

**Why it matters.** Controller-manager timezone upgrades or node moves silently shift schedules by hours; daylight-saving transitions double-fire or skip business-critical batches.

**Fix in JKubeTerm.** Set `spec.timeZone` (IANA name, e.g. Europe/Moscow) on every CronJob on 1.27+ clusters; verify next-run math across a DST boundary in staging.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/controllers/cron-jobs/#time-zones)

### :warning: Test schedules across DST boundaries {#cronjob-dst-test}

**ID:** `cronjob-dst-test` · **Severity:** WARN · **Applies to:** CronJob

**Why it matters.** Spring-forward skips hourly runs, fall-back double-fires daily batches with billing impact.

**Fix in JKubeTerm.** Simulate DST transitions in staging with pinned timeZone; document expected behavior.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/controllers/cron-jobs/#time-zones)

### :warning: Scope CronJob ServiceAccounts minimally {#cronjob-rbac-least}

**ID:** `cronjob-rbac-least` · **Severity:** WARN · **Applies to:** CronJob

**Why it matters.** Nightly jobs with admin rights are juicy targets running on predictable schedules.

**Fix in JKubeTerm.** Dedicated least-privilege SAs per CronJob; audit verbs quarterly.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/tasks/configure-pod-container/configure-service-account/)

### :warning: Never leave startingDeadlineSeconds unset {#cronjob-starting-deadline-default}

**ID:** `cronjob-starting-deadline-default` · **Severity:** WARN · **Applies to:** CronJob

**Why it matters.** Unset deadlines silently skip late starts; the missing run looks like success.

**Fix in JKubeTerm.** Set explicit deadlines (e.g. 200s) on every CronJob; alert on missed starts.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/controllers/cron-jobs/)

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

### :information_source: Design CronJobs safe to backfill and replay {#cron-backfill-safety}

**ID:** `cron-backfill-safety` · **Severity:** INFO · **Applies to:** CronJob

**Why it matters.** Catch-up runs after downtime replay weeks of ticks at once with overlapping writes, and manual replays for backfills corrupt non-idempotent pipelines.

**Fix in JKubeTerm.** Make handlers idempotent with date-partitioned outputs; test backfill of N missed ticks in staging; document the manual trigger command.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/controllers/cron-jobs/)

### :information_source: Keep enough Job history to debug, not to clutter {#cron-history-limits}

**ID:** `cron-history-limits` · **Severity:** INFO · **Applies to:** CronJob

**Why it matters.** Defaults (3 success, 1 failure) delete the evidence before morning standup investigates an overnight failure, while unlimited history chokes listings.

**Fix in JKubeTerm.** Set `successfulJobsHistoryLimit: 3-5` and `failedJobsHistoryLimit: 5-10` per criticality; export failure logs centrally regardless of limits.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/controllers/cron-jobs/)

### :information_source: Review the embedded Job template as a real Job {#cron-job-template-review}

**ID:** `cron-job-template-review` · **Severity:** INFO · **Applies to:** CronJob

**Why it matters.** CronJob templates skip Job-level review (backoff, deadline, restartPolicy) because "it's just a schedule wrapper", shipping broken Jobs that fail only at 3 AM.

**Fix in JKubeTerm.** Apply the same Job checklist (backoffLimit, activeDeadlineSeconds, restartPolicy, resources, idempotency) to every jobTemplate; render and dry-run the template standalone.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/controllers/cron-jobs/)

### :information_source: Document manual runs and dry-runs for every CronJob {#cron-manual-runbook}

**ID:** `cron-manual-runbook` · **Severity:** INFO · **Applies to:** CronJob

**Why it matters.** Incident response stalls when the only person knowing the manual trigger is asleep and the manifest needs reconstructing from memory.

**Fix in JKubeTerm.** Keep a one-command manual run plus dry-run variant per CronJob in the runbook; link it from the CronJob annotations for discoverability in JKubeTerm.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/controllers/cron-jobs/)

### :information_source: Stagger CronJobs out of peak windows {#cron-resource-windows}

**ID:** `cron-resource-windows` · **Severity:** INFO · **Applies to:** CronJob

**Why it matters.** Ten heavy batches all at minute zero saturate nodes and storage simultaneously, causing cascading Pending and timeouts that look like an outage.

**Fix in JKubeTerm.** Spread schedules across the hour (use hash-based minutes per job), set resource requests honestly, and prefer off-peak windows for heavy batch work.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/controllers/cron-jobs/)

### :information_source: A suspended CronJob schedules nothing (alias) {#cron-suspend}

**ID:** `cron-suspend` · **Severity:** INFO · **Applies to:** CronJob

**Why it matters.** Compatibility alias for the ClusterAdvisor trigger `cron-suspend`; canonical guidance lives in `cronjob-suspend` in this wiki.

**Fix in JKubeTerm.** See `cronjob-suspend` above for the full JKubeTerm workflow.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/controllers/cron-jobs/)

### :information_source: Treat suspend as a change-managed flag {#cron-suspend-discipline}

**ID:** `cron-suspend-discipline` · **Severity:** INFO · **Applies to:** CronJob

**Why it matters.** Someone suspends a noisy CronJob "temporarily" and it stays suspended for months; resuming later fires a surprise backlog with production impact.

**Fix in JKubeTerm.** Suspend via GitOps PR with expiry date and owner annotation, never imperatively; alert on any CronJob suspended longer than 24h.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/controllers/cron-jobs/)

### :information_source: Review failed history weekly {#cronjob-failed-history-review}

**ID:** `cronjob-failed-history-review` · **Severity:** INFO · **Applies to:** CronJob

**Why it matters.** Failed history pruned too fast hides flaky Jobs that fail every third night.

**Fix in JKubeTerm.** Keep failed history 5-10; review weekly; ship logs centrally regardless.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/controllers/cron-jobs/)

### :information_source: Dashboard last-schedule vs now gap {#cronjob-last-schedule-dashboard}

**ID:** `cronjob-last-schedule-dashboard` · **Severity:** INFO · **Applies to:** CronJob

**Why it matters.** Nobody watches CronJob status pages; silent suspensions persist for weeks.

**Fix in JKubeTerm.** Graph time-since-last-success per CronJob; page when gap exceeds 2x schedule.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/controllers/cron-jobs/)

### :information_source: Set activeDeadlineSeconds inside CronJob templates {#cronjob-pod-active-deadline}

**ID:** `cronjob-pod-active-deadline` · **Severity:** INFO · **Applies to:** CronJob

**Why it matters.** Hung ticks overlap the next schedule forever under Allow policy.

**Fix in JKubeTerm.** Set activeDeadlineSeconds in every jobTemplate; pair with concurrencyPolicy.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/controllers/job/#job-termination-and-cleanup)

### :information_source: Quota nightly batch windows {#cronjob-resource-quotas-nightly}

**ID:** `cronjob-resource-quotas-nightly` · **Severity:** INFO · **Applies to:** CronJob

**Why it matters.** Overlapping CronJobs stampede at midnight, exhausting quotas and evicting serving Pods.

**Fix in JKubeTerm.** Stagger schedules, quota batch namespaces, fence onto batch pools.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/policy/resource-quotas/)

### :information_source: Match imagePullPolicy to the tagging strategy (alias) {#image-pull-policy}

**ID:** `image-pull-policy` · **Severity:** INFO · **Applies to:** Pod, Deployment, StatefulSet, DaemonSet, Job, CronJob

**Why it matters.** Compatibility alias for the ClusterAdvisor trigger `image-pull-policy`; canonical guidance lives in `pod-image-pull-policy` in this wiki.

**Fix in JKubeTerm.** See `pod-image-pull-policy` above for the full JKubeTerm workflow.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/containers/images/#updating-images)

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

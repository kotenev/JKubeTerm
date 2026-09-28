# Events — 30 best practices

*The cluster news feed as a debugging instrument.*

> **PDF:** `11-events.pdf` — `tools/export-book-pdf.sh` (Chromium `--print-to-pdf`, same as guides/training).

## In JKubeTerm

Open any Events row: the **Object** view breaks it into typed sections, **Best practices** cards flag violations with Fix hints, and drill-down jumps to related objects. Practices Wiki (`Help → Practices Wiki…`) holds the same entries with per-user Markdown overrides.

## Practices

### :warning: Read count as a loop detector {#ev-count-means-loop}

**ID:** `ev-count-means-loop` · **Severity:** WARN · **Applies to:** Event

**Why it matters.** Count 500 on BackOff means the same failure retried for hours (fix the cause), while count 2 on FailedScheduling means a transient blip; teams treat both as "some errors".

**Fix in JKubeTerm.** Prioritize high-count Warnings as systemic loops; low-count fresh Warnings as new regressions. Graph count growth rate to distinguish stuck loops from flapping.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/reference/kubectl/generated/kubectl_events/)

### :warning: Evicted Pods tell you the node's story, read it {#ev-evicted-context}

**ID:** `ev-evicted-context` · **Severity:** WARN · **Applies to:** Event

**Why it matters.** Evicted Events name the pressure (DiskPressure, MemoryPressure) and the victim QoS, but teams reschedule without addressing the node condition, repeating the eviction loop.

**Fix in JKubeTerm.** Note the eviction reason, check node conditions and QoS class, fix capacity or requests before rescheduling; cordon pressured nodes first.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/tasks/administer-cluster/out-of-resource/)

### :warning: FailedMount points at volumes, secrets or images {#ev-failed-mount}

**ID:** `ev-failed-mount` · **Severity:** WARN · **Applies to:** Event

**Why it matters.** Mount failures (missing Secret key, unformatted disk, bad subPath) block container start entirely, yet teams debug the app image while the volume never attached.

**Fix in JKubeTerm.** Check the named volume source first (Secret/ConfigMap keys exist? PVC bound? subPath valid?); describe the volume source object before touching the image.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/storage/volumes/)

### :warning: FailedScheduling always names the missing resource {#ev-failed-scheduling}

**ID:** `ev-failed-scheduling` · **Severity:** WARN · **Applies to:** Event

**Why it matters.** Pending Pods with no further investigation sit for days because "scheduling will sort it out"; the Event already states exactly which resource, taint or affinity blocks placement.

**Fix in JKubeTerm.** Read the FailedScheduling message fully (insufficient cpu/memory, node selector mismatch, taints); fix capacity, tolerations or requests — never just wait longer.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/scheduling-eviction/)

### :warning: Alert on Warning Event patterns, never on raw streams {#ev-no-alert-fatigue}

**ID:** `ev-no-alert-fatigue` · **Severity:** WARN · **Applies to:** Event

**Why it matters.** Raw Event alerting fires hundreds of alerts per rollout (every Pulled/Started), training the team to ignore the channel that later carries the real outage.

**Fix in JKubeTerm.** Alert on rate-of-change of specific reasons (BackOff, FailedMount, FailedScheduling) via event exporters (eventrouter); never page on Normal events.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/reference/kubectl/generated/kubectl_events/)

### :warning: OOMKilled in Events means limits work, sizing doesn't {#ev-oom-events}

**ID:** `ev-oom-events` · **Severity:** WARN · **Applies to:** Event

**Why it matters.** Teams read OOMKilled as "Kubernetes killed my app" and raise limits blindly, when the event actually proves the limit correctly contained a leak or spike.

**Fix in JKubeTerm.** Correlate OOM Events with memory graphs to distinguish leaks (raise-then-fix-code) from spikes (raise limits, add HPA); never remove limits to "fix" OOMs.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/tasks/configure-pod-container/assign-memory-resource/)

### :warning: Unhealthy probe Events demand probe redesign, not threshold inflation {#ev-probe-failures}

**ID:** `ev-probe-failures` · **Severity:** WARN · **Applies to:** Event

**Why it matters.** Raising failureThreshold to silence Unhealthy Events masks genuinely dead apps behind green probes, converting fast detectable failures into slow mysterious ones.

**Fix in JKubeTerm.** Treat repeated Unhealthy as app or probe-path bugs: fix the endpoint, separate liveness from readiness, add startup probes for slow boots. Tune thresholds last.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/)

### :warning: Burn error budgets on Warning Event rates {#ev-slo-burn-events}

**ID:** `ev-slo-burn-events` · **Severity:** WARN · **Applies to:** Event

**Why it matters.** Raw error counts without budgets page on every rollout blip.

**Fix in JKubeTerm.** Define Warning-rate SLOs per reason; page on burn, ticket on drift.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/reference/kubectl/generated/kubectl_events/)

### :information_source: Link every alerted reason to a runbook {#ev-actionable-runbooks}

**ID:** `ev-actionable-runbooks` · **Severity:** INFO · **Applies to:** Event

**Why it matters.** A paged BackOff without a linked runbook starts with search-engine roulette under time pressure, and every responder invents different first steps.

**Fix in JKubeTerm.** Maintain reason → runbook mapping for the top 10 reasons (BackOff, FailedMount, FailedScheduling, Unhealthy, OOMKilled...); link from alerts directly.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/reference/kubectl/generated/kubectl_events/)

### :information_source: Overlay Events with deploy markers {#ev-correlate-deploys}

**ID:** `ev-correlate-deploys` · **Severity:** INFO · **Applies to:** Event

**Why it matters.** Errors without deploy context get blamed on the wrong change.

**Fix in JKubeTerm.** Join event timelines with rollout annotations in Grafana; note firstTimestamp vs deploy time.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/reference/kubectl/generated/kubectl_events/)

### :information_source: Interpret count resets after rescheduling {#ev-count-reset-meaning}

**ID:** `ev-count-reset-meaning` · **Severity:** INFO · **Applies to:** Event

**Why it matters.** New Pod names reset counts, hiding a chronic loop as a series of fresh low-count Events.

**Fix in JKubeTerm.** Group by reason plus template hash across reschedules, not by Pod name.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/reference/kubectl/generated/kubectl_events/)

### :information_source: Drill to the owner before reading Pod Events {#ev-drill-owner-first}

**ID:** `ev-drill-owner-first` · **Severity:** INFO · **Applies to:** Event

**Why it matters.** Pod Events show symptoms; the faulty template field lives two levels up.

**Fix in JKubeTerm.** In JKubeTerm drill from Pod to Deployment/Job first, then read owner Events.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/overview/working-with-objects/owners-dependents/)

### :information_source: Understand aggregation before assuming silence {#ev-duplicate-suppression}

**ID:** `ev-duplicate-suppression` · **Severity:** INFO · **Applies to:** Event

**Why it matters.** The API aggregates identical events (count++, updated lastTimestamp), so a quiet stream can hide an active loop that merely stopped emitting new rows.

**Fix in JKubeTerm.** Watch count and lastTimestamp deltas, not row counts; alert on aggregation growth for Warning reasons even when no new rows appear.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/reference/kubectl/generated/kubectl_events/)

### :information_source: Cover all namespaces with the event exporter {#ev-exporter-coverage}

**ID:** `ev-exporter-coverage` · **Severity:** INFO · **Applies to:** Event

**Why it matters.** Exporters scoped to default miss kube-system, monitoring and argocd incidents entirely, creating blind spots exactly where platform failures originate.

**Fix in JKubeTerm.** Deploy cluster-scoped export with all-namespaces RBAC; verify coverage by generating a test Warning per namespace quarterly.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/reference/kubectl/generated/kubectl_events/)

### :information_source: Master fieldSelector for Events triage {#ev-field-selector-skill}

**ID:** `ev-field-selector-skill` · **Severity:** INFO · **Applies to:** Event

**Why it matters.** Unfiltered kubectl get events drowns triage in unrelated objects.

**Fix in JKubeTerm.** Practice involvedObject and reason field selectors; save snippets in runbooks.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/reference/kubectl/generated/kubectl_events/)

### :information_source: Correlate firstTimestamp with deploys and changes {#ev-first-last-times}

**ID:** `ev-first-last-times` · **Severity:** INFO · **Applies to:** Event

**Why it matters.** An error without a start time cannot be blamed on the 14:03 deploy or the 14:05 config edit; teams guess instead of correlating.

**Fix in JKubeTerm.** Always note firstTimestamp vs deploy/config timestamps; lastTimestamp freshness tells whether the issue is ongoing or stale. JKubeTerm shows both in the Object view.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/reference/kubectl/generated/kubectl_events/)

### :information_source: Reconstruct pull failures from the Event timeline {#ev-imagepull-timeline}

**ID:** `ev-imagepull-timeline` · **Severity:** INFO · **Applies to:** Event

**Why it matters.** ImagePullBackOff shows only the latest state; the timeline (Pulling → Failed → BackOff) reveals whether the registry, the tag, the secret or the network is at fault.

**Fix in JKubeTerm.** Read the sequence: auth errors implicate imagePullSecrets, not-found implicates tags, timeouts implicate network/registry. Fix that layer, not the Pod spec randomly.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/containers/images/)

### :information_source: Always scope Events to the involved object {#ev-involved-filter}

**ID:** `ev-involved-filter` · **Severity:** INFO · **Applies to:** Event

**Why it matters.** Namespace-wide event streams mix dozens of objects; the BackOff explaining your outage sits ten screens below unrelated Scheduling successes.

**Fix in JKubeTerm.** Filter `field-selector involvedObject.name=X` (JKubeTerm drill from any object lands pre-filtered); correlate the object's event timeline with its Pod logs.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/reference/kubectl/generated/kubectl_events/)

### :information_source: Killing Events with long grace reveal drain problems {#ev-killing-grace}

**ID:** `ev-killing-grace` · **Severity:** INFO · **Applies to:** Event

**Why it matters.** Killing Events that linger for minutes indicate missing SIGTERM handlers or too-short grace periods, foreshadowing 502 spikes in the next rollout.

**Fix in JKubeTerm.** Compare Killing duration against terminationGracePeriodSeconds; add preStop hooks and handlers for slow drainers; alert on Killing older than grace plus margin.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/#pod-termination)

### :information_source: Read the full message, not just the reason {#ev-note-field}

**ID:** `ev-note-field` · **Severity:** INFO · **Applies to:** Event

**Why it matters.** Reason codes (FailedMount, Unhealthy) categorize; the message names the volume, the path, the HTTP code and the threshold that actually failed. Skimming reasons wastes the detail.

**Fix in JKubeTerm.** Expand truncated messages (JKubeTerm Object view shows full text); copy the exact failing object names into the investigation rather than paraphrasing.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/reference/kubectl/generated/kubectl_events/)

### :information_source: Follow Events up the owner chain {#ev-owner-chain}

**ID:** `ev-owner-chain` · **Severity:** INFO · **Applies to:** Event

**Why it matters.** Pod-level Events describe symptoms (BackOff) while the cause (bad template, missing secret) lives on the Deployment or Job; fixing Pods treats symptoms.

**Fix in JKubeTerm.** From a Pod Event drill to the owner (ReplicaSet, Job, Deployment) and read its Events too; fix at the highest level that owns the faulty field.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/overview/working-with-objects/owners-dependents/)

### :information_source: Paste Event timelines into postmortems {#ev-postmortem-event-timeline}

**ID:** `ev-postmortem-event-timeline` · **Severity:** INFO · **Applies to:** Event

**Why it matters.** Postmortems without timelines rely on memory and misattribute causes.

**Fix in JKubeTerm.** Export the filtered Event window (first/last/count) into every incident doc.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/reference/kubectl/generated/kubectl_events/)

### :information_source: Link runbooks from alert annotations, not memory {#ev-prewarm-runbook-links}

**ID:** `ev-prewarm-runbook-links` · **Severity:** INFO · **Applies to:** Event

**Why it matters.** Pasted URLs rot; responders search under pressure.

**Fix in JKubeTerm.** Annotate alert rules with runbook URLs; verify links quarterly.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/reference/kubectl/generated/kubectl_events/)

### :information_source: Audit quiet namespaces for missing exporters {#ev-quiet-namespaces-audit}

**ID:** `ev-quiet-namespaces-audit` · **Severity:** INFO · **Applies to:** Event

**Why it matters.** Silent namespaces may lack exporter coverage rather than lack problems.

**Fix in JKubeTerm.** Quarterly: generate test Warnings per namespace; verify central receipt.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/reference/kubectl/generated/kubectl_events/)

### :information_source: Ship Events centrally; etcd keeps one hour only {#ev-retention-window}

**ID:** `ev-retention-window` · **Severity:** INFO · **Applies to:** Event

**Why it matters.** The API prunes Events after about an hour, so postmortems for overnight incidents find an empty timeline and rely on memories instead of evidence.

**Fix in JKubeTerm.** Deploy an event exporter (eventrouter to Loki/ES) with at least 30-day retention; include involvedObject, reason, count and first/last timestamps in the schema.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/reference/kubectl/generated/kubectl_events/)

### :information_source: Triage by type Normal vs Warning, then by count {#ev-severity-triage}

**ID:** `ev-severity-triage` · **Severity:** INFO · **Applies to:** Event

**Why it matters.** Treating every Event equally buries the single Warning with count 200 among hundreds of Normal Scheduled/Pulled notices that mean success.

**Fix in JKubeTerm.** Filter Warning first, sort by count then lastTimestamp; Normal events are context, Warning events are leads. In JKubeTerm open Events, filter by object, scan Warnings top-down.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/reference/kubectl/generated/kubectl_events/)

### :information_source: Sweep stale Warnings after incidents close {#ev-stale-sweep}

**ID:** `ev-stale-sweep` · **Severity:** INFO · **Applies to:** Event

**Why it matters.** Resolved incidents leave Warning Events (and alert silences) behind that desensitize the next on-call to genuinely fresh failures with identical text.

**Fix in JKubeTerm.** Close the loop: verify Warning rates return to baseline post-incident, lift silences, and note resolved counts in the postmortem timeline.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/reference/kubectl/generated/kubectl_events/)

### :information_source: Learn the Normal baseline to spot abnormal fast {#ev-success-baseline}

**ID:** `ev-success-baseline` · **Severity:** INFO · **Applies to:** Event

**Why it matters.** Without knowing the healthy sequence (Scheduled → Pulling → Pulled → Created → Started) every incident starts from zero pattern-matching instead of instant deviation spotting.

**Fix in JKubeTerm.** Study Normal sequences for your workloads in calm times; during incidents scan for missing steps (no Pulled = registry; no Scheduled = scheduler) before reading messages.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/reference/kubectl/generated/kubectl_events/)

### :information_source: Dashboard Warning-to-Normal ratio per workload {#ev-warning-ratio-dashboard}

**ID:** `ev-warning-ratio-dashboard` · **Severity:** INFO · **Applies to:** Event

**Why it matters.** Absolute counts mislead across fleet sizes; ratios reveal degrading workloads early.

**Fix in JKubeTerm.** Track Warning share per Deployment/Job over rolling weeks.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/reference/kubectl/generated/kubectl_events/)

### :information_source: Read Events newest-first, then describe the object (alias) {#event-hygiene}

**ID:** `event-hygiene` · **Severity:** INFO · **Applies to:** Event

**Why it matters.** Compatibility alias for the ClusterAdvisor trigger `event-hygiene`; canonical guidance lives in `ev-hygiene-triage` in this wiki.

**Fix in JKubeTerm.** See `ev-hygiene-triage` above for the full JKubeTerm workflow.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/reference/kubectl/generated/kubectl_events/)

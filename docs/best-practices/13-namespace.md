# Namespaces — 30 best practices

*Team boundaries: quotas, policy, cost, lifecycle.*

> **PDF:** `13-namespace.pdf` — `tools/export-book-pdf.sh` (Chromium `--print-to-pdf`, same as guides/training).

## In JKubeTerm

Open any Namespaces row: the **Object** view breaks it into typed sections, **Best practices** cards flag violations with Fix hints, and drill-down jumps to related objects. Practices Wiki (`Help → Practices Wiki…`) holds the same entries with per-user Markdown overrides.

## Practices

### :rotating_light: Guard production namespaces against deletion and prune {#ns-delete-protection}

**ID:** `ns-delete-protection` · **Severity:** CRITICAL · **Applies to:** Namespace

**Why it matters.** One `kubectl delete ns` (or GitOps prune of the namespace object) wipes every workload, PVC reference and policy in it within seconds, with no per-object confirmation.

**Fix in JKubeTerm.** Protect prod namespaces with policy agents blocking deletes, require manual runbook confirmation, snapshot before planned removal; never let GitOps prune namespace objects automatically.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/overview/working-with-objects/namespaces/)

### :warning: Scope backups per namespace with tested restores {#ns-backup-scope}

**ID:** `ns-backup-scope` · **Severity:** WARN · **Applies to:** Namespace

**Why it matters.** Cluster-wide backup dumps look complete but restore the wrong namespace versions together, mixing prod data with staging config on recovery day.

**Fix in JKubeTerm.** Velero schedules per namespace (or label-selected sets) with independent retention; rehearse single-namespace restore to scratch quarterly.

**Official docs:** [velero.io](https://velero.io/docs/latest/)

### :warning: Audit that default-deny actually denies {#ns-default-deny-audit}

**ID:** `ns-default-deny-audit` · **Severity:** WARN · **Applies to:** Namespace

**Why it matters.** Applied-but-unenforced policies (wrong CNI) give false confidence while traffic flows freely.

**Fix in JKubeTerm.** Probe connectivity (allowed vs denied) per namespace after policy changes; alert on enforcement gaps.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/services-networking/network-policies/)

### :warning: Default-deny network traffic in every application namespace {#ns-network-default-deny}

**ID:** `ns-network-default-deny` · **Severity:** WARN · **Applies to:** Namespace

**Why it matters.** Flat pod networking lets any compromised Pod reach databases, metadata endpoints and other teams' APIs; breaches spread laterally by default.

**Fix in JKubeTerm.** Apply default-deny Ingress+Egress per namespace (with DNS egress allowance), then whitelist required flows explicitly; test with connectivity probes before enforcing.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/services-networking/network-policies/)

### :warning: Keep the default namespace empty of real workloads {#ns-no-default-workloads}

**ID:** `ns-no-default-workloads` · **Severity:** WARN · **Applies to:** Namespace

**Why it matters.** Everything forgotten lands in default, mixing blast radii, quotas and policies into one ungovernable soup where drill-down and RBAC scoping break down.

**Fix in JKubeTerm.** Deploy nothing real to default (use it for smoke tests only); alert on any Deployment/Service outside known namespaces; default-deny NetworkPolicy there.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/overview/working-with-objects/namespaces/)

### :warning: Enforce Pod Security Standards per namespace tier {#ns-pss-enforce}

**ID:** `ns-pss-enforce` · **Severity:** WARN · **Applies to:** Namespace

**Why it matters.** Without enforce labels, privileged Pods land in team namespaces routinely and nobody notices until the audit (or the incident).

**Fix in JKubeTerm.** Label namespaces `pod-security.kubernetes.io/enforce: restricted` (baseline minimum), audit mode first for legacy, exemptions only documented with expiry.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/security/pod-security-standards/)

### :warning: Scope team RBAC to namespaces, never cluster-wide {#ns-rbac-per-team}

**ID:** `ns-rbac-per-team` · **Severity:** WARN · **Applies to:** Namespace

**Why it matters.** ClusterRoleBindings for teams grant accidental control over kube-system, monitoring and other teams' namespaces; one bad apply cascades everywhere.

**Fix in JKubeTerm.** Bind team Roles within their namespaces only; cluster access via break-glass accounts with expiry and audit; review bindings quarterly with `kubectl auth can-i` matrices.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/reference/access-authn-authz/rbac/)

### :warning: Never share Secrets across namespaces by copying values {#ns-secret-isolation}

**ID:** `ns-secret-isolation` · **Severity:** WARN · **Applies to:** Namespace

**Why it matters.** Copied secret values drift (rotation updates one copy), and grep cannot find all copies when the credential leaks and must be revoked everywhere at once.

**Fix in JKubeTerm.** Use ExternalSecrets per namespace from one vault path, or scoped reflectors with audit; never paste values between namespaces manually.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/configuration/secret/)

### :information_source: Bound every team namespace with quotas and limits (alias) {#namespace-quotas}

**ID:** `namespace-quotas` · **Severity:** INFO · **Applies to:** Namespace

**Why it matters.** Compatibility alias for the ClusterAdvisor trigger `namespace-quotas`; canonical guidance lives in `ns-quotas` in this wiki.

**Fix in JKubeTerm.** See `ns-quotas` above for the full JKubeTerm workflow.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/policy/resource-quotas/)

### :information_source: Restrict image registries per namespace {#ns-allowed-registries}

**ID:** `ns-allowed-registries` · **Severity:** INFO · **Applies to:** Namespace

**Why it matters.** Any-registry namespaces pull typosquatted or stale images without policy friction.

**Fix in JKubeTerm.** Enforce allowed registries via policy agents per namespace tier (prod strictest).

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/containers/images/)

### :information_source: Allocate cost and ownership per namespace {#ns-cost-allocation}

**ID:** `ns-cost-allocation` · **Severity:** INFO · **Applies to:** Namespace

**Why it matters.** Shared clusters without cost attribution let runaway namespaces burn budget with no owner paged, and optimization lacks accountability.

**Fix in JKubeTerm.** Label namespaces with team/cost-center, export per-namespace usage (OpenCost or cloud billing), review top consumers monthly with owners present.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/overview/working-with-objects/namespaces/)

### :information_source: Rehearse namespace restore, not just backup {#ns-dr-test-restore}

**ID:** `ns-dr-test-restore` · **Severity:** INFO · **Applies to:** Namespace

**Why it matters.** Backups without restore rehearsal fail on CRDs, finalizers and cross-namespace refs.

**Fix in JKubeTerm.** Restore each tier namespace to scratch quarterly; time RTO per namespace.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/overview/working-with-objects/namespaces/)

### :information_source: Inventory namespaces before cluster operations {#ns-drill-inventory}

**ID:** `ns-drill-inventory` · **Severity:** INFO · **Applies to:** Namespace

**Why it matters.** Upgrades and drains planned against "our three namespaces" miss the shadow IT namespaces created months ago, whose workloads break unannounced.

**Fix in JKubeTerm.** List all namespaces (including Terminating) before every maintenance; tag system vs team vs ephemeral; confirm owners for any unknown namespace first.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/overview/working-with-objects/namespaces/)

### :information_source: Control egress per namespace with allowlists {#ns-egress-control}

**ID:** `ns-egress-control` · **Severity:** INFO · **Applies to:** Namespace

**Why it matters.** Unrestricted egress lets any compromised Pod exfiltrate to anywhere and reach cloud metadata endpoints for credential theft.

**Fix in JKubeTerm.** Default-deny egress plus allowlisted external CIDRs/ports per namespace; always allow kube-dns; block metadata endpoints except where explicitly needed.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/services-networking/network-policies/)

### :information_source: Separate environments by cluster or strict namespace policy {#ns-env-separation}

**ID:** `ns-env-separation` · **Severity:** INFO · **Applies to:** Namespace

**Why it matters.** Prod and dev sharing namespaces (or loosely separated ones) leak prod credentials into dev Pods and let load tests crush production Services.

**Fix in JKubeTerm.** Prefer separate clusters for prod vs non-prod; if sharing, enforce with quotas, NetworkPolicies, distinct ServiceAccounts and no shared Secrets; test isolation, don't assume it.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/overview/working-with-objects/namespaces/)

### :information_source: Audit finalizers that block namespace teardown {#ns-finalizers-hygiene}

**ID:** `ns-finalizers-hygiene` · **Severity:** INFO · **Applies to:** Namespace

**Why it matters.** Third-party operators add finalizers that never complete when the operator itself is deleted first, permanently wedging the namespace.

**Fix in JKubeTerm.** List finalizers before deleting operators (delete workloads first, operators last); document required deletion order per operator in runbooks.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/overview/working-with-objects/finalizers/)

### :information_source: Require explicit pull policies per tier {#ns-image-pull-policy-guard}

**ID:** `ns-image-pull-policy-guard` · **Severity:** INFO · **Applies to:** Namespace

**Why it matters.** Implicit pull policies differ per tag pattern, causing stale-image surprises per namespace.

**Fix in JKubeTerm.** Require explicit imagePullPolicy in prod namespaces via policy checks.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/containers/images/#updating-images)

### :information_source: Set LimitRange defaults so every Pod has requests {#ns-limitrange-defaults}

**ID:** `ns-limitrange-defaults` · **Severity:** INFO · **Applies to:** Namespace

**Why it matters.** Without defaults, forgotten requests create BestEffort Pods that evict first and blind HPA.

**Fix in JKubeTerm.** Set default requests/limits plus min/max per namespace; verify with dry-run creates.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/policy/limit-range/)

### :information_source: Dashboard health per namespace, not just cluster-wide {#ns-monitoring-per-ns}

**ID:** `ns-monitoring-per-ns` · **Severity:** INFO · **Applies to:** Namespace

**Why it matters.** Cluster-green dashboards hide a fully red team namespace; owners learn about their outage from users instead of their own dashboard.

**Fix in JKubeTerm.** Provision per-namespace Grafana folders (deploy health, error budget, quota usage); page team channels on their namespace signals, not the global average.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/cluster-administration/monitoring/)

### :information_source: Name namespaces for team plus environment, never mutable concepts {#ns-name-conventions}

**ID:** `ns-name-conventions` · **Severity:** INFO · **Applies to:** Namespace

**Why it matters.** Names like `test-final-v2` or `john-dev` become permanent API surface that DNS, RBAC and NetworkPolicies depend on; renaming later means migrating everything.

**Fix in JKubeTerm.** Use `team-env` (`payments-prod`), keep `default` empty of real workloads, reserve `kube-*` prefixes; enforce the pattern with policy agents at creation.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/overview/working-with-objects/namespaces/)

### :information_source: Offboard namespaces completely when teams leave {#ns-offboarding-cleanup}

**ID:** `ns-offboarding-cleanup` · **Severity:** INFO · **Applies to:** Namespace

**Why it matters.** Departed teams' namespaces keep running (and billing) forgotten CronJobs and Ingresses with stale DNS pointing at recycled IPs.

**Fix in JKubeTerm.** Offboarding runbook: snapshot data, delete DNS/LB artifacts, remove namespace, verify quota release and cost drop; confirm within one billing cycle.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/overview/working-with-objects/namespaces/)

### :information_source: Onboard namespaces with a checklist, not folklore {#ns-onboarding-checklist}

**ID:** `ns-onboarding-checklist` · **Severity:** INFO · **Applies to:** Namespace

**Why it matters.** New team namespaces miss quotas, policies, monitoring and backups when created ad-hoc, and gaps surface only during their first incident.

**Fix in JKubeTerm.** Templated onboarding: namespace, quotas, LimitRange, PSS labels, NetworkPolicies, RBAC, dashboards, backup schedule, owner annotation — all from one reviewed PR template.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/overview/working-with-objects/namespaces/)

### :information_source: Require owner contact annotations {#ns-owner-contact-annotation}

**ID:** `ns-owner-contact-annotation` · **Severity:** INFO · **Applies to:** Namespace

**Why it matters.** Ownerless namespaces stall incident response while responders hunt for humans.

**Fix in JKubeTerm.** Require owner/team/contact annotations at creation; re-verify quarterly.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/overview/working-with-objects/annotations/)

### :information_source: Enforce namespace standards with policy agents {#ns-policy-agent}

**ID:** `ns-policy-agent` · **Severity:** INFO · **Applies to:** Namespace

**Why it matters.** Documented standards without enforcement decay within weeks: quotas missing, PSS unenforced, labels absent — discovered only during incidents.

**Fix in JKubeTerm.** Deploy Kyverno or Gatekeeper policies requiring quotas, PSS labels, owner annotations and naming patterns on namespace creation; block, don't just warn.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/policy/)

### :information_source: Scope quotas per priority class and scope {#ns-quota-scopes}

**ID:** `ns-quota-scopes` · **Severity:** INFO · **Applies to:** Namespace

**Why it matters.** Flat quotas block critical Pods while best-effort hogs capacity within the same limit.

**Fix in JKubeTerm.** Add scoped quotas (BestEffort vs NotBestEffort, Terminating vs NotTerminating) per team namespace.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/policy/resource-quotas/#quota-and-cluster-capacity)

### :information_source: Pair quotas with LimitRanges or quotas misfire {#ns-resourcequota-vs-limitrange}

**ID:** `ns-resourcequota-vs-limitrange` · **Severity:** INFO · **Applies to:** Namespace

**Why it matters.** Quotas counting requests fail open when Pods lack requests (counted as zero).

**Fix in JKubeTerm.** Always deploy LimitRange defaults alongside quotas; test with request-less dry-runs.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/policy/resource-quotas/)

### :information_source: Know how to unstick Terminating namespaces {#ns-terminating-debug}

**ID:** `ns-terminating-debug` · **Severity:** INFO · **Applies to:** Namespace

**Why it matters.** Namespaces hang Terminating on stuck finalizers (often deleted controllers' leftovers), blocking redeploys and quota release while teams wait helplessly.

**Fix in JKubeTerm.** Find the blocking finalizer (`kubectl get ns -o yaml`), remove the responsible resource or patch the finalizer list deliberately after snapshotting; fix the controller that left it.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/overview/working-with-objects/namespaces/)

### :information_source: Expire ephemeral namespaces automatically {#ns-ttl-ephemeral}

**ID:** `ns-ttl-ephemeral` · **Severity:** INFO · **Applies to:** Namespace

**Why it matters.** PR preview and experiment namespaces accumulate forever, consuming quotas, IPs and attention long after their branches merged.

**Fix in JKubeTerm.** TTL controllers or nightly jobs delete namespaces past their annotated expiry; require `expires-at` annotation on non-prod namespaces; notify owners before deletion.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/overview/working-with-objects/namespaces/)

### :information_source: Share wildcard TLS via per-namespace copies, not cross-mounts {#ns-wildcard-tls-shared}

**ID:** `ns-wildcard-tls-shared` · **Severity:** INFO · **Applies to:** Namespace

**Why it matters.** Cross-namespace secret mounts break the namespace boundary and complicate rotation; one rotation miss leaves some Ingresses serving expired certs.

**Fix in JKubeTerm.** Replicate wildcard certs per namespace via GitOps or reflector with explicit RBAC; rotate centrally and verify every copy's expiry in one dashboard.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/services-networking/ingress/#tls)

### :information_source: Quota PVC counts and total storage per namespace {#pvc-quota-per-team}

**ID:** `pvc-quota-per-team` · **Severity:** INFO · **Applies to:** PersistentVolumeClaim, Namespace

**Why it matters.** One team's unbounded PVC creation exhausts provisioner capacity or cloud quotas, blocking every other team's deploys with cryptic provision errors.

**Fix in JKubeTerm.** Add ResourceQuota (persistentvolumeclaims count + requests.storage) per team namespace; alert at 80% with expansion request workflow.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/policy/resource-quotas/)

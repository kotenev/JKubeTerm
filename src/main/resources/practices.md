# JKubeTerm best-practices wiki
# One `## practice <id>` section per entry. User entries in ~/.jkubeterm/practices.md override bundled ones by id.

## practice readiness-probe
Kinds: Pod, Deployment, StatefulSet, DaemonSet
Severity: WARN
Title: Every container needs a readinessProbe
Docs:
- https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/#container-probes
- https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/
### Why
Without a readiness probe, broken Pods keep receiving Service traffic and users see 500s during a bad rollout.
### Fix
Add an httpGet or tcpSocket readinessProbe (periodSeconds: 5 is a sane start) to every container.

## practice liveness-probe
Kinds: Pod, Deployment, StatefulSet, DaemonSet
Severity: INFO
Title: Add a livenessProbe distinct from readiness
Docs:
- https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/
### Why
A deadlock that still answers readiness checks is never restarted without a liveness probe.
### Fix
Add a cheap livenessProbe (different endpoint or longer period than readiness).

## practice startup-probe
Kinds: Pod, Deployment, StatefulSet, DaemonSet
Severity: INFO
Title: Slow-starting containers need a startupProbe
Docs:
- https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/#define-startup-probes
### Why
Without a startup probe, liveness kills slow starters (JVM, migrations) before they finish booting.
### Fix
Add a startupProbe with generous failureThreshold (e.g. 30 x periodSeconds 10) guarding liveness.

## practice resource-requests
Kinds: Pod, Deployment, StatefulSet, DaemonSet, Job, CronJob
Severity: WARN
Title: Set resource requests on every container
Docs:
- https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/
- https://kubernetes.io/docs/tasks/configure-pod-container/assign-memory-resource/
### Why
Without requests the scheduler and HPA are blind, QoS drops to BestEffort, and Pods are evicted first.
### Fix
Set requests matching idle usage (e.g. cpu: 50m, memory: 64Mi) on every container.

## practice resource-limits
Kinds: Pod, Deployment, StatefulSet, DaemonSet, Job, CronJob
Severity: INFO
Title: Set resource limits to contain leaks
Docs:
- https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/
### Why
One leaking container without limits can OOM the whole node.
### Fix
Set limits (e.g. cpu: 200m, memory: 128Mi) and watch Grafana for throttling or OOMKilled.

## practice run-as-non-root
Kinds: Pod, Deployment, StatefulSet, DaemonSet, Job, CronJob
Severity: WARN
Title: Run containers as non-root
Docs:
- https://kubernetes.io/docs/tasks/configure-pod-container/security-context/
- https://kubernetes.io/docs/concepts/security/pod-security-standards/
### Why
A breakout from a root container has full privileges on the node.
### Fix
Set securityContext.runAsNonRoot: true (plus runAsUser, seccompProfile type RuntimeDefault).

## practice no-privilege-escalation
Kinds: Pod, Deployment, StatefulSet, DaemonSet, Job, CronJob
Severity: INFO
Title: Disable privilege escalation explicitly
Docs:
- https://kubernetes.io/docs/tasks/configure-pod-container/security-context/
### Why
Implicit escalation lets a compromised process gain more privileges than it started with.
### Fix
Set securityContext.allowPrivilegeEscalation: false on every container.

## practice read-only-root
Kinds: Pod, Deployment, StatefulSet, DaemonSet
Severity: INFO
Title: Prefer a read-only root filesystem
Docs:
- https://kubernetes.io/docs/tasks/configure-pod-container/security-context/
### Why
A writable root lets attackers drop binaries and persist inside the container.
### Fix
Set securityContext.readOnlyRootFilesystem: true and mount writable tmp dirs explicitly.

## practice drop-capabilities
Kinds: Pod, Deployment, StatefulSet, DaemonSet
Severity: INFO
Title: Drop all Linux capabilities, add back only needed ones
Docs:
- https://kubernetes.io/docs/tasks/configure-pod-container/security-context/
### Why
Default capabilities (CHOWN, NET_RAW and friends) widen every breakout.
### Fix
Set securityContext.capabilities.drop: [ALL], then add: only what the app proves it needs.

## practice host-namespaces
Kinds: Pod, Deployment, StatefulSet, DaemonSet
Severity: CRITICAL
Title: Never share host network, PID or IPC
Docs:
- https://kubernetes.io/docs/concepts/security/pod-security-standards/
### Why
A container escape from a host-networked Pod is a node escape.
### Fix
Remove hostNetwork/hostPID/hostIPC unless a CNI-class DaemonSet truly needs them.

## practice pinned-image
Kinds: Pod, Deployment, StatefulSet, DaemonSet, Job, CronJob
Severity: WARN
Title: Pin image tags, never float on latest
Docs:
- https://kubernetes.io/docs/concepts/containers/images/#image-names
### Why
Floating tags make rollouts irreproducible and rollbacks meaningless.
### Fix
Pin image:tag (e.g. nginx:1.27) and bump deliberately; use digest for releases.

## practice image-pull-policy
Kinds: Pod, Deployment, StatefulSet, DaemonSet, Job, CronJob
Severity: INFO
Title: Match imagePullPolicy to the tagging strategy
Docs:
- https://kubernetes.io/docs/concepts/containers/images/#updating-images
### Why
latest plus IfNotPresent runs stale images on different nodes.
### Fix
Use Always with floating tags, or better: pin the tag and keep IfNotPresent.

## practice replica-count
Kinds: Deployment
Severity: WARN
Title: Run at least two replicas for availability
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/deployment/
### Why
A single replica means downtime on every node failure, drain or bad rollout.
### Fix
Use replicas: 2 or more (JKubeTerm Scale button or Edit YAML).

## practice rolling-update
Kinds: Deployment
Severity: INFO
Title: Prefer RollingUpdate over Recreate
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/deployment/#strategy
### Why
Recreate kills everything before starting replacements — downtime on every deploy.
### Fix
Keep the default RollingUpdate with maxUnavailable/maxSurge 25% unless a ReadWriteOnce volume forces Recreate.

## practice rollout-complete
Kinds: Deployment
Severity: WARN
Title: A stuck rollout is an incident, not patience
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/deployment/#checking-rollout-status
### Why
updated != ready means new Pods crash or never become ready while old ones already drain.
### Fix
Watch Pods and Events; check image, probes and resources; `kubectl rollout undo` when stuck.

## practice pdb
Kinds: Deployment, StatefulSet
Severity: INFO
Title: Guard voluntary disruption with a PodDisruptionBudget
Docs:
- https://kubernetes.io/docs/concepts/workloads/pods/disruptions/
### Why
Node drains and upgrades evict everything at once without a PDB, including your only replica.
### Fix
Create a PDB with minAvailable: 1 (or maxUnavailable: 1) per critical workload.

## practice stateful-storage
Kinds: StatefulSet
Severity: INFO
Title: StatefulSets need volumeClaimTemplates
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/statefulset/
### Why
Without per-Pod PVCs, rescheduled Pods lose their data.
### Fix
Add a volumeClaimTemplates entry (1Gi RWO is enough for a lab).

## practice daemonset-tolerations
Kinds: DaemonSet
Severity: INFO
Title: DaemonSets need tolerations for tainted nodes
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/daemonset/
- https://kubernetes.io/docs/concepts/scheduling-eviction/taint-and-toleration/
### Why
Control-plane taints silently skip DaemonSet Pods, leaving nodes without logging or monitoring.
### Fix
Add tolerations matching the cluster taints (or a nodeSelector for the intended nodes).

## practice service-selector
Kinds: Service
Severity: WARN
Title: Every Service needs a matching selector
Docs:
- https://kubernetes.io/docs/concepts/services-networking/service/
### Why
A selector-less Service (outside ExternalName) routes to zero Pods — empty Endpoints, silent 503s.
### Fix
Add spec.selector matching the Pod labels, or use ExternalName for outside addresses.

## practice service-type
Kinds: Service
Severity: INFO
Title: Expose via ClusterIP plus Ingress, not NodePort
Docs:
- https://kubernetes.io/docs/concepts/services-networking/service/#publishing-services-service-types
### Why
NodePort opens a high port on every node; LoadBalancer stays pending without a cloud LB or tunnel.
### Fix
Prefer ClusterIP plus Ingress for user traffic; NodePort only for quick lab checks.

## practice ingress-tls
Kinds: Ingress
Severity: WARN
Title: Terminate TLS on every Ingress
Docs:
- https://kubernetes.io/docs/concepts/services-networking/ingress/#tls
- https://cert-manager.io/docs/
### Why
Plain-HTTP Ingresses leak credentials and tokens on the wire.
### Fix
Add spec.tls with a cert-manager Certificate secret (staging issuer first).

## practice ingress-class
Kinds: Ingress
Severity: INFO
Title: Always set ingressClassName
Docs:
- https://kubernetes.io/docs/concepts/services-networking/ingress/#ingress-class
### Why
Without a class, the Ingress may be ignored when several controllers exist.
### Fix
Set ingressClassName: nginx (the minikube addon class).

## practice cron-suspend
Kinds: CronJob
Severity: INFO
Title: A suspended CronJob schedules nothing
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/cron-jobs/
### Why
suspend: true is sticky — the silence looks like success until somebody checks.
### Fix
Set spec.suspend: false to resume; alert on missed schedules.

## practice cron-schedule
Kinds: CronJob
Severity: WARN
Title: Cron schedules must be valid 5-field expressions
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/cron-jobs/#cron-schedule-syntax
### Why
A malformed schedule never fires (or fires at surprising times).
### Fix
Use minute hour day month weekday, e.g. '0 * * * *' for hourly.

## practice job-backoff
Kinds: Job
Severity: INFO
Title: Bound Job retries with backoffLimit
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/job/
### Why
Unbounded retries turn a broken migration into a hot CrashLoop burning CPU for hours.
### Fix
Set backoffLimit: 3-6 and activeDeadlineSeconds for long tasks.

## practice job-restart-policy
Kinds: Job
Severity: WARN
Title: Job Pods must use restartPolicy Never or OnFailure
Docs:
- https://kubernetes.io/docs/concepts/workloads/controllers/job/#pod-template
### Why
restartPolicy: Always is rejected by the API — the Job never starts.
### Fix
Set restartPolicy: Never (or OnFailure for retryable steps) in the Pod template.

## practice config-no-secrets
Kinds: ConfigMap
Severity: WARN
Title: Never put secrets into ConfigMaps
Docs:
- https://kubernetes.io/docs/concepts/configuration/secret/
- https://kubernetes.io/docs/concepts/configuration/configmap/
### Why
ConfigMaps are readable cluster-wide and land in backups, logs and exports in plain text.
### Fix
Move credentials to Secrets (or SealedSecrets/ExternalSecrets) and reference them via envFrom/secretKeyRef.

## practice config-immutable
Kinds: ConfigMap
Severity: INFO
Title: Mark stable ConfigMaps immutable
Docs:
- https://kubernetes.io/docs/concepts/configuration/configmap/#immutable-configmaps
### Why
Mutable ConfigMaps update mounted files with up to a minute of skew across Pods — half the fleet on old config.
### Fix
Set immutable: true for versioned config; roll the name (config-v2) instead of editing.

## practice secret-sealed
Kinds: *
Severity: WARN
Title: Never commit plain Secrets to Git
Docs:
- https://kubernetes.io/docs/concepts/configuration/secret/
- https://github.com/bitnami-labs/sealed-secrets
### Why
base64 is not encryption — a committed Secret is a leaked Secret.
### Fix
Use SealedSecrets or ExternalSecrets; keep only references in Git.

## practice secret-rotation
Kinds: *
Severity: INFO
Title: Rotate leaked or old credentials instead of hiding them
Docs:
- https://kubernetes.io/docs/concepts/configuration/secret/
### Why
Deleting a screenshot does not un-leak a token that is already copied.
### Fix
Recreate the Secret/SA/token, update consumers, then revoke the old value.

## practice pvc-storage-class
Kinds: PersistentVolumeClaim
Severity: WARN
Title: Every PVC needs a StorageClass or a bound PV
Docs:
- https://kubernetes.io/docs/concepts/storage/persistent-volumes/
- https://kubernetes.io/docs/concepts/storage/storage-classes/
### Why
A PVC without provisioner or matching PV stays Pending forever.
### Fix
Enable a provisioner (minikube storage-provisioner addon) or pre-create the PV.

## practice pvc-rwo-replicas
Kinds: PersistentVolumeClaim, Deployment, StatefulSet
Severity: WARN
Title: ReadWriteOnce volumes allow a single writer
Docs:
- https://kubernetes.io/docs/concepts/storage/persistent-volumes/#access-modes
### Why
Two replicas on one RWO claim deadlock in Multi-Attach — the second Pod pends forever.
### Fix
Use strategy Recreate for single-writer, or an RWX driver (NFS/Longhorn) for shared writes.

## practice pv-reclaim
Kinds: PersistentVolume
Severity: INFO
Title: Choose the reclaim policy deliberately
Docs:
- https://kubernetes.io/docs/concepts/storage/persistent-volumes/#reclaiming
### Why
Retain keeps disks (and bills) after the claim is gone; Delete wipes lab data on namespace removal.
### Fix
Use Retain for valuable data, Delete for scratch; document the choice per StorageClass.

## practice node-pressure
Kinds: Node
Severity: WARN
Title: React to DiskPressure and MemoryPressure, not just Ready
Docs:
- https://kubernetes.io/docs/concepts/architecture/nodes/#condition
- https://kubernetes.io/docs/tasks/administer-cluster/out-of-resource/
### Why
Ready=True with DiskPressure means evictions are already starting.
### Fix
Free disk, raise memory, or cordon and drain before the kubelet starts killing Pods.

## practice node-taints
Kinds: Node
Severity: INFO
Title: Keep control-plane taints unless this is a lab
Docs:
- https://kubernetes.io/docs/concepts/scheduling-eviction/taint-and-toleration/
### Why
Scheduling workloads on control-plane nodes starves etcd and the API server.
### Fix
Keep NoSchedule taints on control-plane; add tolerations only to system DaemonSets.

## practice namespace-quotas
Kinds: Namespace
Severity: INFO
Title: Bound every team namespace with quotas and limits
Docs:
- https://kubernetes.io/docs/concepts/policy/resource-quotas/
- https://kubernetes.io/docs/concepts/policy/limit-range/
### Why
One runaway namespace starves the whole lab cluster without quotas.
### Fix
Add ResourceQuota (cpu/memory/pvc count) plus LimitRange defaults per namespace.

## practice event-hygiene
Kinds: Event
Severity: INFO
Title: Read Events newest-first, then describe the object
Docs:
- https://kubernetes.io/docs/reference/kubectl/generated/kubectl_events/
### Why
Stale warnings drown the one fresh BackOff that explains the outage.
### Fix
Sort by lastTimestamp, filter by involved object, then `kubectl describe` that object.

## practice labels-standard
Kinds: *
Severity: INFO
Title: Use recommended app.kubernetes.io labels
Docs:
- https://kubernetes.io/docs/concepts/overview/working-with-objects/common-labels/
### Why
Ad-hoc labels break selectors, drill-down and dashboards that expect the standard set.
### Fix
Label every workload with app.kubernetes.io/name, instance, version, component, part-of, managed-by.

## practice annotations-purpose
Kinds: *
Severity: INFO
Title: Keep annotations for tooling, labels for identity
Docs:
- https://kubernetes.io/docs/concepts/overview/working-with-objects/annotations/
### Why
Selectors cannot match annotations, so routing or drill-down on annotated fields silently finds nothing.
### Fix
Put identity (app, tier, env) in labels; changelogs, owners and restart markers in annotations.

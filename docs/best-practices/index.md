# K8S Best Practices in JKubeTerm

> **PDF:** one file per chapter — `tools/export-book-pdf.sh`. Generated PDFs are git-ignored build artifacts.

A hands-on book generated from the bundled practices wiki (`src/main/resources/practices.md`, the same DB the in-app advisor reads). 351 practices across 14 Kubernetes categories, each with *why it matters*, *fix in JKubeTerm*, and official-docs links. Goal: a maximally resilient, secure cluster operated from JKubeTerm.

## How to read this book

1. Work chapters in order with the [QuickStart](../guides/quickstart-minikube.md) lab open.
2. For every practice: reproduce the violation, watch the JKubeTerm card appear, apply the fix, watch it turn green.
3. Severity order inside each chapter: :rotating_light: CRITICAL → :warning: WARN → :information_source: INFO.

## Chapters

| Chapter | Practices | Focus |
|---|---|---|
| [Pods](01-pod.md) | 30 | The smallest deployable unit — and the source of most incidents. |
| [Deployments](02-deployment.md) | 37 | Stateless rollouts without downtime. |
| [StatefulSets](03-statefulset.md) | 37 | Stable identity plus per-Pod storage. |
| [DaemonSets](04-daemonset.md) | 30 | One agent per node, done right. |
| [Services](05-service.md) | 30 | Stable virtual IPs over disposable Pods. |
| [ConfigMaps](06-configmap.md) | 30 | Plain-text config without rebuilds — and without secrets. |
| [Jobs](07-job.md) | 30 | Run-to-completion work that must actually finish. |
| [CronJobs](08-cronjob.md) | 30 | Scheduled work that must fire, once, on time. |
| [Ingresses](09-ingress.md) | 30 | The front door: routing, TLS, and edge policy. |
| [PersistentVolumeClaims](10-pvc.md) | 30 | Requests for disk that must bind and survive. |
| [Events](11-events.md) | 30 | The cluster news feed as a debugging instrument. |
| [Nodes](12-node.md) | 30 | The machines under the Pods: capacity, health, upgrades. |
| [Namespaces](13-namespace.md) | 30 | Team boundaries: quotas, policy, cost, lifecycle. |
| [PersistentVolumes](14-pv.md) | 30 | Real disks with lifecycles of their own. |

## Failure-mode map (where outages actually come from)

| If you see… | Read chapters |
|---|---|
| CrashLoop / ImagePullBackOff | Pods, Events |
| 502s during deploys | Deployments, Services, Ingresses |
| Pending Pods | Pods, Nodes, PersistentVolumeClaims |
| Data loss on reschedule | StatefulSets, PersistentVolumeClaims, PersistentVolumes |
| Silent cron gaps | CronJobs, Jobs, Events |
| TLS expiry at midnight | Ingresses |
| Namespace-wide starvation | Namespaces, Nodes |

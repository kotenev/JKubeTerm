# Kubernetes 1.37 highlights (course pins: minikube v1.39.0, kubectl v1.37)
# Rendered in Help → What's new in Kubernetes for the connected server version.

## v1.37 "The Mountain Goat"
Stable:чили ; Pod-level resources GA; HPA container-size auto-scaling beta.
Docs:
- https://kubernetes.io/blog/2026/01/15/kubernetes-v1-37-release/
### Try it in the lab
Compare `kubectl top pods` against the new Pod-level usage API on the demo Deployment.
### Fix
Pin kubectl to the server minor (v1.37.x) — JKubeTerm Connect shows both versions.

## v1.36 highlights
Stable:чили ; Job success-policy improvements; scheduler queueing hints beta.
Docs:
- https://kubernetes.io/blog/2025/10/15/kubernetes-v1-36-release/
### Try it in the lab
Add `successPolicy` to the demo migration Job and watch completions in JKubeTerm Jobs view.
### Fix
Re-apply CronJob manifests after the upgrade — deprecated batch fields were removed.

## v1.35 highlights
Stable:чили ; StatefulSet PVC auto-delete GA; image volume sources beta.
Docs:
- https://kubernetes.io/blog/2025/08/27/kubernetes-v1-35-release/
### Try it in the lab
Set `persistentVolumeClaimRetentionPolicy` on the StatefulSet lab and delete a Pod — the PVC survives by default.
### Fix
Audit StorageClasses after upgrade: default provisioner annotations moved in v1.35.

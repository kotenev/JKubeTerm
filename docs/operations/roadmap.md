# Roadmap & architectural hooks

The README roadmap, mapped to the components each item will touch. Use this
page to plan changes without breaking the documented model.

| # | Roadmap item | Architectural hook today | Planned change |
|---|---|---|---|
| 1 | Streaming Watch API & incremental list updates | `KubernetesService.list()` one-shot per kind | Replace/augment with `Watch` per kind; table updates via `Platform.runLater` from a listener; keep the single worker or add a dedicated watch thread |
| 2 | Native interactive Exec WebSocket terminal & log stream | One-shot `kubectl exec` via `ExternalTools` (ADR-0004) | Fabric8 `pod().inContainer().exec()` / `watchLog()`; new UI terminal component; retire the subprocess exec path |
| 3 | Port-forward manager (stop/reconnect) | kubectl subprocess tracked in `portProcesses` (ADR-0007) | Fabric8 port-forward with per-forward handles and a manager UI |
| 4 | API discovery, CRD browser, Metrics/Prometheus | Hardcoded `ResourceKind` + `isClusterScoped` list (ADR-0008) | Dynamic kinds from discovery; extend the resource catalog page and C4/ArchiMate models |
| 5 | Helm lifecycle, GitOps, file browser | `helm list` only | Extend `ExternalTools` or move to Helm SDK; new container in the container view |
| 6 | Optional AI assistant (read-only default, explicit write approval) | — | New component beside Kubernetes Service; reuse the worker executor; approval flow reuses the confirmation-dialog pattern |

## Model maintenance rules

When a roadmap item lands:

1. Update the affected Mermaid/PlantUML/ArchiMate/Structurizr sources in the
   same change (see the [notation guide](../diagrams/index.md) for the view
   matrix and element registry).
2. Add or amend an ADR in [decisions](../architecture/decisions.md) when the
   change supersedes one (e.g. ADR-0007 is expected to be superseded by the
   port-forward manager).
3. Re-check the [threading model](../architecture/threading.md) if the change
   adds blocking work or new threads.

# Services — 30 best practices

*Stable virtual IPs over disposable Pods.*

> **PDF:** `05-service.pdf` — `tools/export-book-pdf.sh` (Chromium `--print-to-pdf`, same as guides/training).

## In JKubeTerm

Open any Services row: the **Object** view breaks it into typed sections, **Best practices** cards flag violations with Fix hints, and drill-down jumps to related objects. Practices Wiki (`Help → Practices Wiki…`) holds the same entries with per-user Markdown overrides.

## Practices

### :warning: Every Service needs a matching selector (alias) {#service-selector}

**ID:** `service-selector` · **Severity:** WARN · **Applies to:** Service

**Why it matters.** Compatibility alias for the ClusterAdvisor trigger `service-selector`; canonical guidance lives in `svc-selector` in this wiki.

**Fix in JKubeTerm.** See `svc-selector` above for the full JKubeTerm workflow.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/services-networking/service/)

### :warning: Alert on Services with zero Endpoints {#svc-endpoints-check}

**ID:** `svc-endpoints-check` · **Severity:** WARN · **Applies to:** Service

**Why it matters.** Empty Endpoints means total traffic blackhole, yet the Service, Deployment and Pods each look healthy in isolation. This is the most common "everything green, app down" state.

**Fix in JKubeTerm.** Alert on `kube_endpoint_address_available == 0` per Service; in JKubeTerm drill from the Service to Pods behind it to see the selector mismatch instantly.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/services-networking/service/)

### :warning: Restrict LoadBalancer sources with loadBalancerSourceRanges {#svc-lb-source-ranges}

**ID:** `svc-lb-source-ranges` · **Severity:** WARN · **Applies to:** Service

**Why it matters.** A public LoadBalancer without source ranges exposes admin UIs, ArgoCD and Grafana to the whole internet, and scanners find them within hours.

**Fix in JKubeTerm.** Set `loadBalancerSourceRanges` to office/VPN CIDRs for internal tools; keep truly public endpoints on Ingress with WAF and auth instead.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/tasks/access-application-cluster/configure-cloud-controller-manager/)

### :warning: Never put a headless Service behind a cloud load balancer {#svc-no-headless-lb}

**ID:** `svc-no-headless-lb` · **Severity:** WARN · **Applies to:** Service

**Why it matters.** Headless Services have no ClusterIP for the LB to target, so the cloud controller either rejects the Service or programs nonsense backends.

**Fix in JKubeTerm.** Keep headless Services ClusterIP-less for discovery only; front user traffic with a separate ClusterIP Service or Ingress.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/services-networking/service/#headless-services)

### :information_source: Expose via ClusterIP plus Ingress, not NodePort (alias) {#service-type}

**ID:** `service-type` · **Severity:** INFO · **Applies to:** Service

**Why it matters.** Compatibility alias for the ClusterAdvisor trigger `service-type`; canonical guidance lives in `svc-type-exposure` in this wiki.

**Fix in JKubeTerm.** See `svc-type-exposure` above for the full JKubeTerm workflow.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/services-networking/service/#publishing-services-service-types)

### :information_source: Decide allocateLoadBalancerNodePorts deliberately {#svc-allocate-lb-nodeports}

**ID:** `svc-allocate-lb-nodeports` · **Severity:** INFO · **Applies to:** Service

**Why it matters.** Default NodePort allocation per LB Service exhausts the nodeport range on LB-heavy clusters.

**Fix in JKubeTerm.** Disable NodePort allocation when the cloud LB talks directly to Pods; keep otherwise.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/services-networking/service/#load-balancer-nodeport-allocation)

### :information_source: Declare appProtocol for protocol-aware infrastructure {#svc-app-protocol}

**ID:** `svc-app-protocol` · **Severity:** INFO · **Applies to:** Service

**Why it matters.** Service meshes and LBs guess protocols from port numbers, misclassifying gRPC as plain HTTP and breaking retries, tracing and mTLS policies.

**Fix in JKubeTerm.** Set `appProtocol: kubernetes.io/h2c` (or http, grpc, ws) on ports served by meshes and protocol-aware LBs; verify mesh telemetry picks it up.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/services-networking/service/#application-protocol)

### :information_source: Audit and delete unused Services quarterly {#svc-audit-unused}

**ID:** `svc-audit-unused` · **Severity:** INFO · **Applies to:** Service

**Why it matters.** Stale Services accumulate cloud LBs, static IPs and DNS records that bill monthly and widen the attack surface long after the app is gone.

**Fix in JKubeTerm.** Quarterly: list Services without Endpoints or with zero traffic, confirm ownership via labels, delete with DNS and LB cleanup. Automate the report, not the delete.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/services-networking/service/)

### :information_source: Use None ClusterIP only for headless discovery {#svc-clusterip-none-headless}

**ID:** `svc-clusterip-none-headless` · **Severity:** INFO · **Applies to:** Service

**Why it matters.** Setting clusterIP None for a serving Service removes load balancing silently; clients get raw A records unexpectedly.

**Fix in JKubeTerm.** Reserve None for StatefulSet governing services; use real ClusterIPs for serving.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/services-networking/service/#headless-services)

### :information_source: Design for Service DNS TTL and caching {#svc-dns-ttl}

**ID:** `svc-dns-ttl` · **Severity:** INFO · **Applies to:** Service

**Why it matters.** Clients caching DNS for minutes keep hitting dead Pod IPs after scaling events, causing intermittent failures no dashboard explains.

**Fix in JKubeTerm.** Keep Service DNS TTLs short, prefer watching Endpoints/EndpointSlice in clients, and use headless Services where clients must react fast to membership changes.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/services-networking/dns-pod-service/)

### :information_source: Plan dual-stack before you need IPv6 {#svc-dual-stack}

**ID:** `svc-dual-stack` · **Severity:** INFO · **Applies to:** Service

**Why it matters.** Single-stack clusters painted into IPv4 corner the whole fleet when compliance or carriers demand IPv6; retrofitting touches CNI, kube-proxy and every LoadBalancer.

**Fix in JKubeTerm.** On new clusters enable dual-stack CNI with ipFamilies; for existing ones document the migration window and test Pod-to-Service paths in both families.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/services-networking/dual-stack/)

### :information_source: Watch EndpointSlices on large Services {#svc-endpointslice}

**ID:** `svc-endpointslice` · **Severity:** INFO · **Applies to:** Service

**Why it matters.** Classic Endpoints objects hit etcd size limits past a few thousand addresses, stalling updates for the biggest Services during scale events.

**Fix in JKubeTerm.** Ensure EndpointSliceMirroring is on (default in modern clusters) and monitor slice counts; split mega-Services before slices fragment.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/services-networking/endpoint-slices/)

### :information_source: Choose externalTrafficPolicy deliberately {#svc-external-traffic-policy}

**ID:** `svc-external-traffic-policy` · **Severity:** INFO · **Applies to:** Service

**Why it matters.** Cluster policy SNATs client IPs (breaking rate limiting and audit) while Local policy drops traffic on nodes without local Pods (breaking small clusters).

**Fix in JKubeTerm.** Use Local when client IP matters and every node runs the Pods (DaemonSet-fronted); otherwise Cluster with PROXY protocol or X-Forwarded-For at Ingress.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/tasks/access-application-cluster/create-external-load-balancer/#preserving-the-client-source-ip)

### :information_source: Point at outside dependencies with ExternalName {#svc-externalname}

**ID:** `svc-externalname` · **Severity:** INFO · **Applies to:** Service

**Why it matters.** Hardcoding external hostnames in every Deployment couples deploys to DNS migrations and prevents per-environment overrides.

**Fix in JKubeTerm.** Create ExternalName Services for managed databases and SaaS endpoints; apps use the in-cluster DNS name and ops repoint one object per environment.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/services-networking/service/#externalname)

### :information_source: Use headless Services for direct Pod addressing {#svc-headless}

**ID:** `svc-headless` · **Severity:** INFO · **Applies to:** Service

**Why it matters.** ClusterIP load-balances, which breaks gossip protocols, leader election and stateful clients that must reach specific peers by stable DNS.

**Fix in JKubeTerm.** Set `clusterIP: None` for StatefulSet governing services and peer-discovery use cases; clients then resolve all Pod A records and pick ordinals directly.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/services-networking/service/#headless-services)

### :information_source: Align cloud LB health checks with readiness {#svc-health-probes-lb}

**ID:** `svc-health-probes-lb` · **Severity:** INFO · **Applies to:** Service

**Why it matters.** Cloud LBs with default TCP checks route to Pods that pass TCP but fail readiness, serving errors from half-started apps during every rollout.

**Fix in JKubeTerm.** Set `healthCheckNodePort` explicitly or use externalTrafficPolicy Local with proper probes so LB membership follows real readiness.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/services-networking/service/#health-check-nodeport)

### :information_source: Pin healthCheckNodePort for stable LB checks {#svc-healthcheck-nodeport}

**ID:** `svc-healthcheck-nodeport` · **Severity:** INFO · **Applies to:** Service

**Why it matters.** Auto-assigned health ports change on Service edits, breaking firewall rules and LB configs silently.

**Fix in JKubeTerm.** Pin healthCheckNodePort explicitly and record it alongside firewall/DNS docs.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/tasks/access-application-cluster/create-external-load-balancer/#health-check-nodeport)

### :information_source: Use internal load balancers for private traffic {#svc-internal-lb}

**ID:** `svc-internal-lb` · **Severity:** INFO · **Applies to:** Service

**Why it matters.** Routing private east-west traffic through public LBs adds cost, latency and an internet-reachable attack surface for purely internal APIs.

**Fix in JKubeTerm.** Annotate internal Services for internal LB class per cloud (e.g. `service.beta.kubernetes.io/aws-load-balancer-internal`); verify with dig that names resolve privately.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/services-networking/service/#internal-load-balancer)

### :information_source: Set ipFamilyPolicy explicitly on dual-stack Services {#svc-ip-family-policy}

**ID:** `svc-ip-family-policy` · **Severity:** INFO · **Applies to:** Service

**Why it matters.** Default SingleStack silently drops one family after cluster migration, breaking half the clients with no EndpointSlice hint about the missing family.

**Fix in JKubeTerm.** Set `ipFamilyPolicy: PreferDualStack` (or RequireDualStack) on Services that must serve both families; verify both ClusterIPs in the Service YAML.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/services-networking/dual-stack/)

### :information_source: Pin loadBalancerClass for multi-LB clusters {#svc-lb-class}

**ID:** `svc-lb-class` · **Severity:** INFO · **Applies to:** Service

**Why it matters.** Two LB controllers fight over unclassified Services, flipping EXTERNAL-IP between implementations.

**Fix in JKubeTerm.** Set loadBalancerClass per Service matching the intended controller; default only one controller per cluster.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/services-networking/service/#load-balancer-class)

### :information_source: Standardize scrape annotations on Services {#svc-monitor-annotations}

**ID:** `svc-monitor-annotations` · **Severity:** INFO · **Applies to:** Service

**Why it matters.** Every team inventing its own prometheus.io annotations means half the fleet unscraped and dashboards with mysterious gaps.

**Fix in JKubeTerm.** Standardize `prometheus.io/scrape/port/path` annotations (or ServiceMonitors) in chart templates; in JKubeTerm the Object view surfaces missing scrape config per Service.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/cluster-administration/monitoring/)

### :information_source: Name every Service port and keep targetPorts symbolic {#svc-ports-named}

**ID:** `svc-ports-named` · **Severity:** INFO · **Applies to:** Service

**Why it matters.** Numeric port soup across Service, Deployment and Ingress drifts apart silently; a container port change breaks routing with no validation error.

**Fix in JKubeTerm.** Name ports (http, metrics, grpc) and reference targetPort by name in Services and Ingress backends so renames propagate by contract.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/services-networking/service/#defining-a-service)

### :information_source: Publish not-ready addresses only for peer discovery {#svc-publish-not-ready}

**ID:** `svc-publish-not-ready` · **Severity:** INFO · **Applies to:** Service

**Why it matters.** Enabling publishNotReadyAddresses on serving Services routes traffic to starting Pods, causing 502 during scale-up.

**Fix in JKubeTerm.** Enable only for gossip/peer-discovery headless Services; keep serving Services readiness-gated.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/services-networking/service/)

### :information_source: Use session affinity only as a last resort {#svc-session-affinity}

**ID:** `svc-session-affinity` · **Severity:** INFO · **Applies to:** Service

**Why it matters.** ClientIP stickiness breaks load distribution, defeats autoscaling math, and fails over badly since clients pin to dying Pods until timeout.

**Fix in JKubeTerm.** Keep the default None; fix statefulness with shared sessions (Redis) or cookies at Ingress instead. If forced, set explicit timeoutSeconds and document why.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/services-networking/service/#session-affinity)

### :information_source: Bound session-sticky timeouts explicitly {#svc-session-sticky-timeout}

**ID:** `svc-session-sticky-timeout` · **Severity:** INFO · **Applies to:** Service

**Why it matters.** Default 3h stickiness pins clients to long-dead Pods behind NATs through rollouts.

**Fix in JKubeTerm.** Set sessionAffinityConfig clientIP timeoutSeconds short (e.g. 300) when stickiness is unavoidable.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/services-networking/service/#session-affinity)

### :information_source: Keep Service names short, stable and environment-free {#svc-short-names}

**ID:** `svc-short-names` · **Severity:** INFO · **Applies to:** Service

**Why it matters.** Long environment-baked names (`payments-prod-us-east-1-svc`) break DNS length limits, churn on every promotion, and force app reconfig per environment.

**Fix in JKubeTerm.** Name Services `payments`, qualify by namespace (`payments.prod`); resolve environment via namespace, not name. Enforce DNS-1123 length in CI.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/overview/working-with-objects/names/)

### :information_source: Implement stickiness at Ingress, not Service {#svc-sticky-ingress-instead}

**ID:** `svc-sticky-ingress-instead` · **Severity:** INFO · **Applies to:** Service

**Why it matters.** Service-level ClientIP affinity is coarse (IP-based, timeout-limited) while modern apps need cookie affinity with failover semantics.

**Fix in JKubeTerm.** Terminate stickiness in the Ingress controller (nginx sticky sessions, mesh consistent hashing) and keep Services affinity-free.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/services-networking/ingress-controllers/)

### :information_source: Align Service and app timeouts with client expectations {#svc-timeout-keepalive}

**ID:** `svc-timeout-keepalive` · **Severity:** INFO · **Applies to:** Service

**Why it matters.** Mismatched idle timeouts between kube-proxy conntrack, cloud LBs and app servers produce sporadic connection resets under exactly the load that matters.

**Fix in JKubeTerm.** Document timeout budgets end to end (client, LB, Service, app) and load-test idle connections; prefer explicit timeouts over defaults at every layer.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/services-networking/service/)

### :information_source: Keep traffic zone-local with topology-aware routing {#svc-topology-routing}

**ID:** `svc-topology-routing` · **Severity:** INFO · **Applies to:** Service

**Why it matters.** Cross-zone traffic adds latency and cloud egress bills on every request, yet default routing sprays uniformly across zones.

**Fix in JKubeTerm.** Enable topology-aware hints (or internalTrafficPolicy) for high-churn internal Services; verify endpoint distribution per zone before relying on it.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/services-networking/service/#topology-aware-routing)

### :information_source: Prefer trafficDistribution over legacy hints {#svc-traffic-distribution}

**ID:** `svc-traffic-distribution` · **Severity:** INFO · **Applies to:** Service

**Why it matters.** Legacy topology hints annotated inconsistently across Services produce unpredictable zone skew.

**Fix in JKubeTerm.** Set spec.trafficDistribution PreferClose on supported clusters for zone-local routing; verify per-zone endpoints.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/services-networking/service/#topology-aware-routing)

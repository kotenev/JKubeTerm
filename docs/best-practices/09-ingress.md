# Ingresses — 30 best practices

*The front door: routing, TLS, and edge policy.*

> **PDF:** `09-ingress.pdf` — `tools/export-book-pdf.sh` (Chromium `--print-to-pdf`, same as guides/training).

## In JKubeTerm

Open any Ingresses row: the **Object** view breaks it into typed sections, **Best practices** cards flag violations with Fix hints, and drill-down jumps to related objects. Practices Wiki (`Help → Practices Wiki…`) holds the same entries with per-user Markdown overrides.

## Practices

### :warning: Run 2+ Ingress controller replicas {#ing-controller-replicas}

**ID:** `ing-controller-replicas` · **Severity:** WARN · **Applies to:** Ingress

**Why it matters.** Single-replica controllers make the edge a single point of failure for every route.

**Fix in JKubeTerm.** Run at least 2 with anti-affinity plus PDB; verify rolling updates keep capacity.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/services-networking/ingress-controllers/)

### :warning: Prevent host collisions across namespaces (guard) {#ing-host-collision-guard}

**ID:** `ing-host-collision-guard` · **Severity:** WARN · **Applies to:** Ingress

**Why it matters.** Duplicate hosts across namespaces race nondeterministically between backends.

**Fix in JKubeTerm.** Enforce one-owner-per-host policy validated in CI across all namespaces.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/services-networking/ingress/)

### :warning: Rotate TLS secrets without downtime {#ing-tls-secret-rotation}

**ID:** `ing-tls-secret-rotation` · **Severity:** WARN · **Applies to:** Ingress

**Why it matters.** Manual secret swaps cause brief mismatches or stale controller caches serving expired certs.

**Fix in JKubeTerm.** Rotate via cert-manager renewal (automatic) or staged secret swap with controller reload verification.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/services-networking/ingress/#tls)

### :warning: IP-allowlist admin paths at the edge {#ingress-allowlist-admin}

**ID:** `ingress-allowlist-admin` · **Severity:** WARN · **Applies to:** Ingress

**Why it matters.** Admin UIs (ArgoCD, Grafana, dashboards) reachable from anywhere are brute-forced within hours of exposure, regardless of password strength.

**Fix in JKubeTerm.** Allowlist office/VPN CIDRs on admin Ingress paths (or separate admin Ingresses); keep public paths minimal and authenticated. Prefer private Ingress classes for tooling.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/services-networking/ingress/)

### :warning: Set explicit client body size limits {#ingress-body-size}

**ID:** `ingress-body-size` · **Severity:** WARN · **Applies to:** Ingress

**Why it matters.** Default 1MB nginx limits reject legitimate uploads with cryptic 413s, while unlimited bodies invite disk-fill DoS through the front door.

**Fix in JKubeTerm.** Set `proxy-body-size` per route to the real maximum upload plus margin; keep a tight global default and loosen only documented upload paths.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/services-networking/ingress/)

### :warning: Monitor certificate expiry and rotation, not just issuance {#ingress-cert-rotation}

**ID:** `ingress-cert-rotation` · **Severity:** WARN · **Applies to:** Ingress

**Why it matters.** Issuance succeeding once means nothing: failed renewals surface as midnight expiries months later, and staging-issuer leftovers serve untrusted certs silently.

**Fix in JKubeTerm.** Alert on cert-manager Certificate Ready=False and on TLS secret expiry under 21 days; dashboard per-Ingress cert age; never ship staging issuers to prod Ingresses.

**Official docs:** [cert-manager.io](https://cert-manager.io/docs/) · [kubernetes.io](https://kubernetes.io/docs/concepts/services-networking/ingress/#tls)

### :warning: Define CORS explicitly, never wildcard with credentials {#ingress-cors-explicit}

**ID:** `ingress-cors-explicit` · **Severity:** WARN · **Applies to:** Ingress

**Why it matters.** Wildcard `Access-Control-Allow-Origin: *` combined with credentials leaks authenticated responses to any site, while missing CORS breaks legitimate frontends opaquely.

**Fix in JKubeTerm.** Enumerate allowed origins, methods and headers per Ingress; never combine `*` with allow-credentials. Test preflight (OPTIONS) in CI, not just GET.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/services-networking/ingress/)

### :warning: Prevent host collisions across namespaces {#ingress-host-collision}

**ID:** `ingress-host-collision` · **Severity:** WARN · **Applies to:** Ingress

**Why it matters.** Two Ingresses claiming the same host in different namespaces race: the winner depends on controller merge order, and deploys flap traffic between backends.

**Fix in JKubeTerm.** Govern hosts centrally (one owner per host via policy or Gateway listeners); validate uniqueness in CI across all namespaces before applying.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/services-networking/ingress/)

### :warning: Choose pathType deliberately: Prefix vs Exact vs ImplementationSpecific {#ingress-path-types}

**ID:** `ingress-path-types` · **Severity:** WARN · **Applies to:** Ingress

**Why it matters.** Prefix `/api` also matches `/apiv2` and `/apiary` by element rules, while ImplementationSpecific behaves differently per controller — both cause traffic to land on wrong backends after innocent-looking edits.

**Fix in JKubeTerm.** Prefer Explicit Exact for precise routes and Prefix with trailing-slash discipline (`/api/`); avoid ImplementationSpecific unless the controller's regex semantics are pinned and documented.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/services-networking/ingress/#path-types)

### :warning: Rate-limit public Ingresses at the edge {#ingress-rate-limit}

**ID:** `ingress-rate-limit` · **Severity:** WARN · **Applies to:** Ingress

**Why it matters.** unauthenticated public endpoints without rate limits are scraped, brute-forced and DDoSed for free; app-level limits kick in too late and per-Pod.

**Fix in JKubeTerm.** Enable controller rate limiting (connections plus requests per second) on public Ingresses with allowlists for health checks and office ranges; alert on limit trips.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/services-networking/ingress/)

### :warning: Force HTTPS redirect on every public Ingress {#ingress-ssl-redirect}

**ID:** `ingress-ssl-redirect` · **Severity:** WARN · **Applies to:** Ingress

**Why it matters.** Serving HTTP and HTTPS side by side lets clients, webhooks and health checks drift onto plaintext, leaking tokens that TLS elsewhere was meant to protect.

**Fix in JKubeTerm.** Enable force-ssl-redirect (with HSTS preload for owned domains) on all public Ingresses; exempt only ACME challenge paths handled by cert-manager solvers.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/services-networking/ingress/#tls)

### :warning: Terminate TLS on every Ingress (alias) {#ingress-tls}

**ID:** `ingress-tls` · **Severity:** WARN · **Applies to:** Ingress

**Why it matters.** Compatibility alias for the ClusterAdvisor trigger `ingress-tls`; canonical guidance lives in `ing-tls` in this wiki.

**Fix in JKubeTerm.** See `ing-tls` above for the full JKubeTerm workflow.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/services-networking/ingress/#tls)

### :information_source: Standardize structured access-log format {#ing-access-log-format}

**ID:** `ing-access-log-format` · **Severity:** INFO · **Applies to:** Ingress

**Why it matters.** Unstructured edge logs resist aggregation; debugging needs request-ID joins across tiers.

**Fix in JKubeTerm.** Emit JSON access logs with request ID, host, path, status, latency; index centrally.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/services-networking/ingress/)

### :information_source: Preserve client IPs end to end {#ing-client-ip-preservation}

**ID:** `ing-client-ip-preservation` · **Severity:** INFO · **Applies to:** Ingress

**Why it matters.** SNAT at multiple layers hides true client IPs, breaking rate limits, audit and geo.

**Fix in JKubeTerm.** Use externalTrafficPolicy Local or PROXY protocol plus X-Forwarded-For handling; verify with echo backends.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/tasks/access-application-cluster/create-external-load-balancer/#preserving-the-client-source-ip)

### :information_source: Size Ingress controller requests/limits {#ing-controller-resources}

**ID:** `ing-controller-resources` · **Severity:** INFO · **Applies to:** Ingress

**Why it matters.** Uncapped controllers OOM under traffic spikes exactly when edge capacity matters most.

**Fix in JKubeTerm.** Set requests/limits per traffic profile; HPA the controller deployment itself.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/services-networking/ingress-controllers/)

### :information_source: Canonicalize www vs apex in one place {#ing-redirect-www-canonical}

**ID:** `ing-redirect-www-canonical` · **Severity:** INFO · **Applies to:** Ingress

**Why it matters.** Split www/apex handling across Ingresses causes redirect loops and SEO splits.

**Fix in JKubeTerm.** Redirect canonically at the edge (one direction only); test both variants plus HSTS.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/services-networking/ingress/)

### :information_source: Enable WebSocket and SSE paths explicitly {#ing-websocket-support}

**ID:** `ing-websocket-support` · **Severity:** INFO · **Applies to:** Ingress

**Why it matters.** Default proxy buffering breaks WebSockets and server-sent events with timeouts and buffering delays.

**Fix in JKubeTerm.** Annotate WS/SSE routes (proxy buffering off, long timeouts); test with real socket clients through the edge.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/services-networking/ingress/)

### :information_source: Ship Ingress access logs with request IDs {#ingress-access-logs}

**ID:** `ingress-access-logs` · **Severity:** INFO · **Applies to:** Ingress

**Why it matters.** Without edge access logs every 502 is a mystery spanning client, edge, Service and app; correlating across four log systems by timestamp alone fails under load.

**Fix in JKubeTerm.** Enable controller access logs with request IDs propagated to backends; retain hot searchable window plus cold archive; dashboard 5xx by Ingress, host and path.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/services-networking/ingress/)

### :information_source: Declare backend protocols explicitly per Service {#ingress-backend-protocol}

**ID:** `ingress-backend-protocol` · **Severity:** INFO · **Applies to:** Ingress

**Why it matters.** Guessed protocols (HTTP vs HTTPS vs gRPC vs FastCGI) cause TLS-to-plaintext mismatches and protocol downgrade errors that surface as random 502s per backend.

**Fix in JKubeTerm.** Annotate backend-protocol per Ingress/Service pair matching what the Pod actually serves; verify with direct-to-Pod protocol checks before blaming the mesh.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/services-networking/ingress/)

### :information_source: Canary with weighted Ingresses before full rollout {#ingress-canary-weights}

**ID:** `ingress-canary-weights` · **Severity:** INFO · **Applies to:** Ingress

**Why it matters.** All-or-nothing Ingress switches turn every deploy into a coin flip for 100% of traffic; rollback still impacts everyone who already saw the bad version.

**Fix in JKubeTerm.** Use canary annotations (canary weight 5-10%) or Gateway API weights for new backends; promote by weight steps with error-budget gates between steps.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/services-networking/ingress/)

### :information_source: Always set ingressClassName (alias) {#ingress-class}

**ID:** `ingress-class` · **Severity:** INFO · **Applies to:** Ingress

**Why it matters.** Compatibility alias for the ClusterAdvisor trigger `ingress-class`; canonical guidance lives in `ing-class` in this wiki.

**Fix in JKubeTerm.** See `ing-class` above for the full JKubeTerm workflow.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/services-networking/ingress/#ingress-class)

### :information_source: Brand the default backend instead of 404 soup {#ingress-default-backend}

**ID:** `ingress-default-backend` · **Severity:** INFO · **Applies to:** Ingress

**Why it matters.** Unmatched hosts fall through to controller default 404 pages that leak infrastructure details and confuse users with no correlation IDs.

**Fix in JKubeTerm.** Deploy a branded default backend returning structured errors with request IDs; alert on its traffic share spiking (typo'd hosts or scan waves).

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/services-networking/ingress/#default-backend)

### :information_source: Manage DNS records with ExternalDNS, not by hand {#ingress-external-dns}

**ID:** `ingress-external-dns` · **Severity:** INFO · **Applies to:** Ingress

**Why it matters.** Manual DNS for every Ingress host drifts: deleted Ingresses leave stale records pointing at recycled LBs, and new hosts wait on ticket queues.

**Fix in JKubeTerm.** Deploy ExternalDNS syncing Ingress hosts to the DNS provider with TXT ownership records; restrict to managed zones and review planned changes in dry-run mode.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/services-networking/ingress/) · [github.com](https://github.com/kubernetes-sigs/external-dns)

### :information_source: Plan migration to Gateway API for complex routing {#ingress-gateway-migration}

**ID:** `ingress-gateway-migration` · **Severity:** INFO · **Applies to:** Ingress

**Why it matters.** Ingress annotations hit expressiveness walls (header matching, traffic splitting, cross-namespace) that force ever more controller-specific hacks.

**Fix in JKubeTerm.** For new complex routing adopt Gateway API (Gateway + HTTPRoute) alongside existing Ingresses; migrate route by route with traffic mirroring before cutover.

**Official docs:** [gateway-api.sigs.k8s.io](https://gateway-api.sigs.k8s.io/)

### :information_source: Expose a dedicated edge health endpoint {#ingress-health-endpoint}

**ID:** `ingress-health-endpoint` · **Severity:** INFO · **Applies to:** Ingress

**Why it matters.** Cloud LBs probing `/` hit app logic, auth and databases, flapping on unrelated failures and coupling edge health to deep dependencies.

**Fix in JKubeTerm.** Serve a cheap `/healthz` at the edge (controller default backend or tiny Service) that checks only edge readiness; point LB and monitoring there.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/services-networking/ingress/)

### :information_source: Enable HTTP/2 and gRPC explicitly at the edge {#ingress-http2-grpc}

**ID:** `ingress-http2-grpc` · **Severity:** INFO · **Applies to:** Ingress

**Why it matters.** gRPC behind HTTP/1-only Ingress fails with opaque protocol errors, and HTTP/2 multiplexing gains stay unrealized when the edge negotiates down silently.

**Fix in JKubeTerm.** Enable SSL redirect plus HTTP/2 on the controller; for gRPC set backend-protocol annotations and test with grpcurl through the Ingress, not direct to Service.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/services-networking/ingress/)

### :information_source: Audit rewrite-target annotations before copying examples {#ingress-rewrite-safety}

**ID:** `ingress-rewrite-safety` · **Severity:** INFO · **Applies to:** Ingress

**Why it matters.** Blog-copy rewrite rules silently strip or duplicate path prefixes, so apps receive paths they never handle, producing 404 storms that look like app bugs.

**Fix in JKubeTerm.** Test rewrites with curl against staging for every path variant; prefer app-native base-path config over annotation rewriting where possible.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/services-networking/ingress/)

### :information_source: Terminate stickiness at Ingress with cookie affinity {#ingress-sticky-sessions}

**ID:** `ingress-sticky-sessions` · **Severity:** INFO · **Applies to:** Ingress

**Why it matters.** Stateful apps behind round-robin Ingress lose sessions on every scale event, while Service-level ClientIP affinity is coarse and breaks behind NAT.

**Fix in JKubeTerm.** Enable Ingress cookie affinity (nginx affinity annotations) with explicit expiry for the stateful paths only; keep the rest stateless and affinity-free.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/services-networking/ingress/)

### :information_source: Tune proxy timeouts to backend reality {#ingress-timeout-tuning}

**ID:** `ingress-timeout-tuning` · **Severity:** INFO · **Applies to:** Ingress

**Why it matters.** Default 60s proxy timeouts kill long uploads, exports and SSE streams exactly at the minute mark, with errors attributed to the app instead of the edge.

**Fix in JKubeTerm.** Set proxy-connect/read/send timeouts per Ingress (annotations or controller ConfigMap) matching the slowest legitimate backend plus margin; document per-route exceptions.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/services-networking/ingress/)

### :information_source: Enable WAF and ModSecurity rules on public Ingresses {#ingress-waf-annotations}

**ID:** `ingress-waf-annotations` · **Severity:** INFO · **Applies to:** Ingress

**Why it matters.** Application CVEs (SQLi, RCE patterns) reach Pods unfiltered when the edge passes everything through, and patching apps lags exploit publication by weeks.

**Fix in JKubeTerm.** Enable the controller's WAF/ModSecurity with OWASP core rules in detection-then-block mode per public Ingress; tune false positives per app before enforcing.

**Official docs:** [kubernetes.io](https://kubernetes.io/docs/concepts/services-networking/ingress/)

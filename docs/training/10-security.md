# Модуль 10. Безопасность: RBAC, PodSecurity, NetworkPolicy, секреты

> **PDF:** `10-security.pdf` — `tools/export-training-pdf.sh`.

Закрываем лабораторию по чеклисту: кто может что (RBAC-матрица), что может Pod
(PodSecurity), кто с кем говорит (NetworkPolicy), где лежат секреты. База RBAC —
[модуль 03](03-access.md), здесь доводим до production-привычек.

## RBAC-матрица лаборатории

| Кто | Где | Verb'ы | Чем выдано |
|---|---|---|---|
| вы (bootstrap) | всё | `*` | `cluster-admin` из minikube |
| вы (повседневный) | `demo`, `lab-data` | get/list/create/delete Deploy, Pods, Svc | `Role` + `RoleBinding` |
| `lab-viewer` | весь кластер read | get/list/watch | `ClusterRole/view` + Binding |
| ArgoCD controller | всё (sync) | `*` | manifest-установка |
| Grafana/Prometheus | свои ns + cluster-read метрик | get/list | чарт |
| Headlamp login | по вашему выбору | ваш Binding | ручной токен SA |

```bash
# повседневная роль вместо cluster-admin:
kubectl -n demo create role dev --verb=get,list,create,update,delete --resource=deployments,pods,services,configmaps
kubectl -n demo create rolebinding dev-you --role=dev --user=you@example.com
kubectl auth can-i delete pods -n demo --as=you@example.com   # yes
kubectl auth can-i list nodes --as=you@example.com            # no — нужен ClusterRole
```

JKubeTerm честно покажет Forbidden диалогом — это не баг клиента, а ответ API.

## ServiceAccount и токены: сроки жизни

```bash
kubectl -n default create token lab-viewer --duration=24h   # короткий — для людей
# долгоживущий — только для роботов, Secret типа kubernetes.io/service-account-token
kubectl apply -f - <<'EOF'
apiVersion: v1
kind: Secret
metadata:
  name: ci-token
  namespace: demo
  annotations:
    kubernetes.io/service-account.name: lab-viewer
type: kubernetes.io/service-account-token
EOF
```

Ротация: `kubectl delete secret ci-token` → пересоздастся сам. Утёкший токен —
отозвать немедленно (удалить SA/Secret/Binding), а не «когда-нибудь».

## PodSecurity: restricted для demo

```bash
kubectl label ns demo pod-security.kubernetes.io/enforce=restricted
kubectl -n demo apply -f - <<'EOF'
apiVersion: v1
kind: Pod
metadata:
  name: bad-pod
spec:
  containers:
    - name: c
      image: nginx:1.27
      # нет runAsNonRoot, seccomp — будет отклонён
EOF
# ожидаем: Forbidden: violates PodSecurity "restricted:latest"
```

Шаблон compliant-контейнера (копируйте в свои манифесты):

```yaml
securityContext:
  runAsNonRoot: true
  runAsUser: 10001
  seccompProfile: {type: RuntimeDefault}
  capabilities: {drop: [ALL]}
```

ArgoCD sync podinfo это переживает; старые образы от root — нет (и правильно).

## NetworkPolicy: default-deny + точечные разрешения

minikube с дефолтным CNI политики **не энфорсит** — для практики включите
Cilium/Calico либо отработайте на kind. Логика всё равно обязательна к пониманию:

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: default-deny
  namespace: demo
spec:
  podSelector: {}
  policyTypes: [Ingress, Egress]
---
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: web-allow-ingress
  namespace: demo
spec:
  podSelector:
    matchLabels: {app: demo-nginx}
  ingress:
    - from: [{podSelector: {matchLabels: {app: ingress-allow}}}]
      ports: [{port: 80}]
  egress:
    - to: [{namespaceSelector: {matchLabels: {"kubernetes.io/metadata.name": kube-system}}}]
      ports: [{port: 53, protocol: UDP}]   # DNS не забудьте, иначе всё «лежит»
```

Порядок отладки при «всё отвалилось после Policy»: сначала Egress-DNS,
потом Ingress-селекторы, потом `policyTypes`.

## Секреты: 5 заповедей

1. base64 ≠ шифрование — в etcd включите encryption at rest (в проде).
2. Никогда plaintext в Git — SealedSecrets (асимметричное шифрование в репо)
   или ExternalSecrets (Vault/AWS SM).
3. `kubectl create secret` вместо YAML руками — меньше шансов засветить.
4. `stringData:` вместо ручного `base64` в манифестах (API закодирует сам).
5. Экспорты JKubeTerm **Save YAML…** и PDF-печати проверяйте на секреты перед
   публикацией.

## TLS и UI-доступы

- Dashboard/Headlamp/Grafana/ArgoCD — только через TLS + auth, никогда голым
  NodePort в интернет. В лаборатории — port-forward (loopback) или Ingress
  с cert-manager ([модуль 05](05-services-ingress.md)).
- `argocd-initial-admin-secret` удалите после заведения личного admin.
- Grafana `admin123` из QuickStart — смените в первый же день.

## Проверь себя в JKubeTerm

- [ ] Повесьте `enforce=restricted` на `demo`, задеплойте compliant-nginx (с securityContext) — встал? А без?
- [ ] Под `lab-viewer`-контекстом откройте Nodes (можно?) и попробуйте Delete (нельзя?) — объясните оба ответа.
- [ ] Найдите Secret `demo-secret` вида… — ага, его нет в 14 видах. Где он виден косвенно?
- [ ] Составьте Egress-правило для DNS и объясните, зачем оно в default-deny мире.

## Ловушки

| Ошибка | Что на самом деле |
|---|---|
| «Закрыл NetworkPolicy, всё легло» | забыт DNS-egress (53/UDP) или kubelet-probes |
| PodSecurity отклонил старый образ | образ требует root — пересоберите или поднимите уровень (audit, не enforce) |
| Токен в логах/скриншоте | отозвать + пересоздать, не «удалить скриншот» |
| `CERTIFICATE_VERIFY_FAILED` → «отключу проверку» | никогда: чините CA/endpoint ([security](../operations/security.md)) |

Дальше: [11 — диагностика: 15 сценариев](11-troubleshooting.md).

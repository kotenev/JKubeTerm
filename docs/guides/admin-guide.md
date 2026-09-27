# Руководство администратора домашней лаборатории

> **PDF:** [admin-guide.pdf](admin-guide.pdf) — печать этой страницы
> (кнопка доступна после `tools/export-guides-pdf.sh`).

Аудитория: вы — и пользователь, и админ кластера minikube из
[quickstart-minikube](quickstart-minikube.md). Здесь: RBAC для UI-доступов,
GitOps-раскладка, hardening, ресурсы, бэкапы, апгрейды, зачистка.

## Модель доступа: кто и как ходит в кластер {#access-model}

| Субъект | Как аутентифицируется | Права |
|---|---|---|
| Вы через JKubeTerm | kubeconfig `minikube` (client cert) | `cluster-admin` (minikube по умолчанию) |
| Вы через kubectl/helm | тот же `~/.kube/config` | те же |
| ArgoCD controller | in-cluster ServiceAccount | `cluster-admin` (manifest-установка) |
| Dashboard/Headlamp | ServiceAccount + Bearer token (ручной выпуск) | задаёте сами через Binding |
| Grafana/Prometheus | свои ServiceAccounts из чарта | namespace-scoped + cluster-read для kube-state-metrics |

Принцип: JKubeTerm работает **с вашими** RBAC-правами без эскалации
([security](../operations/security.md)). Для повседневной работы заведите
ограниченного пользователя, а `cluster-admin` держите для bootstrap.

```bash
# read-only пользователь лаборатории (пример):
kubectl create serviceaccount lab-viewer -n default
kubectl create clusterrolebinding lab-viewer-view \
  --clusterrole=view --serviceaccount=default:lab-viewer
# токен для Headlamp/Dashboard:
kubectl -n default create token lab-viewer --duration=24h
```

Отдельный kubeconfig под этот SA (чтобы подключить его в JKubeTerm вторым
контекстом): выпустите токен, склейте `~/.kube/lab-viewer.config`, затем
`KUBECONFIG=~/.kube/config:~/.kube/lab-viewer.config` — JKubeTerm покажет оба
контекста, первый объявивший имя побеждает
([kubeconfig](../architecture/kubeconfig.md#1-context-discovery-algorithm)).

## Namespaces и GitOps-раскладка {#namespaces-gitops}

Рекомендуемая раскладка лаборатории:

| Namespace | Что живёт | Источник правды |
|---|---|---|
| `argocd` | ArgoCD | manifest v3.4.8 (pin версии!) |
| `ingress-nginx` | Ingress controller | аддон minikube **или** Helm, не оба |
| `cert-manager` | выпуск TLS | Helm `jetstack/cert-manager`, `crds.enabled=true` |
| `monitoring` | Prometheus/Grafana | `kube-prometheus-stack` 91.x |
| `headlamp` / `kubernetes-dashboard` | Web UI | Helm |
| `demo` | демо-приложения | ArgoCD Application (Git) |
| `lab-data` (опц.) | PVC под эксперименты | Helm/Apply |

Правила:

- Pin версий манифестов (`.../argo-cd/v3.4.8/...`), чартов (`--version`) и
  `targetRevision` в Application — `HEAD` только для песочницы.
- Один Application = один namespace-назначение; `automated: {prune, selfHeal}`
  только для `demo`, не для `monitoring`/`argocd`.
- Секреты — только через `SealedSecrets`/`ExternalSecrets` или ручные
  `kubectl create secret`; **никогда** в Git plaintext и не через
  **Save YAML…** в общий доступ (YAML может содержать секреты,
  [resources](../architecture/resources.md#3-yaml-codec)).

Пример AppProject-ограничения для демо-команды:

```yaml
# noinspection KubernetesUnknownResourcesInspection
apiVersion: argoproj.io/v1alpha1
# noinspection KubernetesUnknownResourcesInspection
kind: AppProject
metadata:
  name: lab-demo
  namespace: argocd
spec:
  sourceRepos: [https://github.com/stefanprodan/podinfo]
  destinations: [{server: https://kubernetes.default.svc, namespace: demo}]
  clusterResourceWhitelist: [{group: '', kind: Namespace}]
```

## Сеть и TLS {#network-tls}

- minikube/docker: внешний доступ — через `minikube tunnel` (LoadBalancer),
  `minikube service --url` (NodePort) или Ingress + `*.nip.io`/`sslip.io`.
  JKubeTerm port-forward всегда loopback (`--address 127.0.0.1`) — для себя,
  не для публикации.
- cert-manager issuers для лаборатории:

```yaml
# noinspection KubernetesUnknownResourcesInspection
# staging — пока отлаживаете; prod — когда DNS настоящий
apiVersion: cert-manager.io/v1
# noinspection KubernetesUnknownResourcesInspection
kind: ClusterIssuer
metadata:
  name: letsencrypt-staging
spec:
  acme:
    server: https://acme-staging-v02.api.letsencrypt.org/directory
    email: you@example.com
    privateKeySecretRef: {name: le-staging}
    solvers: [{http01: {ingress: {class: nginx}}}]
```

- Dashboard 7.x не выставлять наружу без auth-прокси (Kong gateway + токен);
  Headlamp — тоже только через port-forward/Ingress с OIDC либо allowlist IP.

## Hardening checklist {#hardening}

- [ ] Dashboard/Headlamp/Grafana/ArgoCD недоступны из интернета без TLS+auth.
- [ ] Пароль `argocd-initial-admin-secret` удалён после заведения личного admin:
  `kubectl -n argocd delete secret argocd-initial-admin-secret`.
- [ ] Grafana `admin123` из QuickStart заменён; включён `persistence` при ценности данных.
- [ ] `lab-viewer` (view) — дефолт для просмотра; `cluster-admin` — только bootstrap.
- [ ] `NetworkPolicy` default-deny в `demo`/`monitoring` при экспериментах с CNI
  (minikube default CNI политики не энфорсит — поставьте Calico/Cilium аддоном при нужде).
- [ ] PodSecurity `restricted` для `demo` (ArgoCD sync это переживёт для podinfo).
- [ ] Секреты/токены не лежат в `site/`, скриншотах, экспортах YAML.

## Ресурсы и лимиты (чтобы minikube не лёг) {#resources}

| Компонент | Профиль | Замечание |
|---|---|---|
| Базовый minikube | `--cpus=4 --memory=8192` | минимум под весь стек |
| Prometheus | `retention: 7d` → `3d` при нехватке | самый прожорливый; `retentionSize` как второй тормоз |
| Grafana | defaults достаточно | persistence включайте осознанно |
| ArgoCD | defaults | HA-манифест для лаборатории не нужен |
| metrics-server | аддон, лёгкий | нужен HPA/VPA и `kubectl top` |

```bash
kubectl top nodes
kubectl -n monitoring top pods | sort -k3 -h | tail -5
# пауза вместо удаления, когда лаборатория не нужна:
minikube pause && minikube unpause
```

## Бэкапы {#backups}

| Что | Как | Частота |
|---|---|---|
| etcd (весь кластер) | `minikube ssh -- sudo ...` снапшот либо Velero | перед апгрейдом K8s |
| Значимые namespaces | `kubectl -n demo get all,cm,secret,ing -o yaml > demo-backup.yaml` | перед сносом демо |
| Helm-релизы | `helm get values <rel> -n <ns> > values-<rel>.yaml` + версии чартов | при каждом изменении |
| Grafana дашборды | экспорт JSON / sidecar ConfigMaps из чарта | при изменении |
| kubeconfig | копия `~/.kube/config` в парольном менеджере | при пересоздании |

Velero для minikube — по желанию (minio как backend); для песочницы достаточно
YAML-экспортов через JKubeTerm **Save YAML…** + `helm get values`.

## Апгрейды {#upgrades}

Порядок: minikube → Kubernetes → CNI/аддоны → cert-manager → ingress →
ArgoCD → monitoring → UI.

```bash
# 1. бэкап (см. выше), затем:
minikube start --kubernetes-version=v1.37.1   # patch-минор внутри профиля
# мажорный скачок надёжнее через новый профиль:
minikube start -p lab2 --driver=docker --kubernetes-version=v1.37.0 --cpus=4 --memory=8192
kubectl config use-context lab2
# 2. helm repo update; upgrade релизов по одному с проверкой rollout:
helm upgrade monitoring prometheus-community/kube-prometheus-stack -n monitoring --version 91.x
kubectl -n monitoring rollout status deploy --all --timeout=300s
# 3. ArgoCD — только pin-манифестом новой версии, проверьте release notes 3.x.
```

JKubeTerm при смене контекста: **↻ Config** → **Connect** к новому контексту;
старый клиент закроется только после успешной проверки нового
(`JKubeTermApp.java:103`).

## Зачистка {#cleanup}

| Уровень | Команда | Эффект |
|---|---|---|
| Демо | `kubectl delete -n argocd application podinfo; kubectl delete ns demo` | ArgoCD снесёт syncнутое (prune) |
| Релиз | `helm uninstall <rel> -n <ns>` | остаются CRD чарта — удаляйте руками при нужде |
| Namespace целиком | `kubectl delete ns <name>` | каскадно, без возврата |
| Вся лаборатория | `minikube delete` (или `-p <profile>`) | ВМ/контейнер + диски + профиль; kubeconfig-контекст чистится |
| Docker-хвосты | `docker ps -a \| grep minikube; docker volume prune` | после delete обычно чисто |

## Troubleshooting {#troubleshooting}

| Симптом | Диагноз через JKubeTerm/CLI |
|---|---|
| Pod `CrashLoopBackOff` | **Pods** → **Pod logs** (все контейнеры по очереди) → **Events**; `kubectl describe pod` |
| `ImagePullBackOff` | нет образа/registry-auth; для minikube грузите локально: `minikube image load img.tar` / `minikube image build` |
| Ingress 404 | класс ingress (`ingressClassName: nginx`?), `kubectl -n ingress-nginx get svc`, `kubectl describe ingress` |
| ArgoCD `OutOfSync` | `argocd app get podinfo` / UI → diff; проверьте `targetRevision` и namespace-destination |
| Prometheus OOMKilled | `kubectl top pods -n monitoring`; режьте retention, поднимайте память профиля |
| `helm list` пуст в JKubeTerm | не тот namespace в комбо (дефолт `default`, релизы в своих ns) — раздел Helm releases в [user-guide](user-guide.md) |
| Port-forward висит | процесс умрёт с выходом из JKubeTerm; занятый local-порт — выберите другой |
| `CERTIFICATE_VERIFY_FAILED` | контекст/CA/endpoint; не отключать TLS — [security](../operations/security.md) |

Связанные страницы: [quickstart-minikube](quickstart-minikube.md),
[user-guide](user-guide.md), [security](../operations/security.md),
[external-tools](../architecture/external-tools.md).

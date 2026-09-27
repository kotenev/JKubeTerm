# Модуль 04. Workloads I: Pod, Deployment, rollout

> **PDF:** `04-workloads.pdf` — `tools/export-training-pdf.sh`.

Учимся деплоить, масштабировать, обновлять и откатывать — сначала в CLI,
потом теми же руками через JKubeTerm. Все манифесты — валидный Apply для
`KubernetesService.apply` (нужны `kind`/`apiVersion`/`metadata.name`).

## Pod напрямую (только чтобы понять, почему так не делают)

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: hello-pod
  namespace: demo
spec:
  containers:
    - name: hello
      image: nginx:1.27
      ports: [{containerPort: 80}]
```

```bash
kubectl create ns demo
kubectl apply -f hello-pod.yaml
kubectl -n demo get pods -w
```

Удалите Pod — он не воскреснет: нет контроллера. Вывод: Pod руками — только
отладка. Дальше — Deployment.

## Deployment: desired state

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: demo-nginx
  namespace: demo
spec:
  replicas: 2
  selector:
    matchLabels: {app: demo-nginx}
  template:
    metadata:
      labels: {app: demo-nginx}
    spec:
      containers:
        - name: nginx
          image: nginx:1.27
          ports: [{containerPort: 80}]
          readinessProbe:
            httpGet: {path: /, port: 80}
            periodSeconds: 5
          resources:
            requests: {cpu: 50m, memory: 64Mi}
            limits: {cpu: 200m, memory: 128Mi}
```

```bash
kubectl apply -f demo-nginx.yaml
kubectl -n demo rollout status deploy/demo-nginx
kubectl -n demo get deploy,rs,pods -o wide
```

Что смотреть в JKubeTerm: Namespace `demo` → **Deployments** → строка
`demo-nginx` → YAML справа (replicas, template); **Pods** → два Pod'а с
префиксом имени ReplicaSet; **Events** → `Scheduled`, `Pulled`, `Started`.

`selector.matchLabels` обязан совпадать с `template.metadata.labels` —
иначе Apply упадёт серверной валидацией. `readinessProbe` решает, когда Pod
получает трафик; `resources` понадобятся для HPA в [модуле 07](07-advanced-workloads.md).

## Тот же деплой через JKubeTerm

1. **New YAML** → вставьте манифест выше (namespace можно стереть — подставится
   из комбо `demo`) → **Apply YAML** → confirm с именем контекста.
2. **Deployments** → **Refresh** → строка появилась.
3. Выберите Deployment → **Scale** → `3` → confirm → **Pods** → три Pod'а.
4. Выберите Deployment → **Restart** → confirm → аннотация
   `kubectl.kubernetes.io/restartedAt` на pod-template, Pod'ы пересоздаются
   (kubectl-идиома, `KubernetesService.java:76`).

Ограничение: два документа (Deployment+Service) одним Apply не вставить —
`Serialization.unmarshal(..., HasMetadata.class)` берёт один объект.
Применяйте по очереди.

## Rolling update и откат

```bash
kubectl -n demo set image deploy/demo-nginx nginx=nginx:1.28
kubectl -n demo rollout status deploy/demo-nginx
kubectl -n demo rollout history deploy/demo-nginx
kubectl -n demo rollout undo deploy/demo-nginx
```

Стратегии (`spec.strategy`): `RollingUpdate` (дефолт: `maxUnavailable: 25%`,
`maxSurge: 25%`) — ноль даунтайма; `Recreate` — убить всё, потом поднять
(нужно для ReadWriteOnce-томов, см. [модуль 06](06-config-storage.md)).

В JKubeTerm смена образа — через **Edit YAML** (поменяйте `image:`) →
**Apply YAML** → наблюдайте в **Pods**, как старые Pod'ы гаснут по одному.

## Labels, selectors, аннотации

- Labels — для селекторов (`app: demo-nginx`), фильтров и Service-маршрутизации.
- Аннотации — для людей/тулов (`restartedAt`, prometheus-scrape).
- В JKubeTerm фильтр ищет по **имени**, не по label'ам — для label-запросов
  используйте `kubectl -l app=demo-nginx`.

## Проверь себя в JKubeTerm

- [ ] Задеплойте `demo-nginx` через Apply, отмасштабируйте до 3, откатите образ.
- [ ] Убейте один Pod (`Delete` на Pod'е) — кто его воскресит и за сколько секунд? (Ответ: ReplicaSet.)
- [ ] Поставьте `replicas: 0` через Edit YAML — что покажут Deployments и Pods?
- [ ] Сломайте селектор (поменяйте label в template) — прочитайте серверную ошибку.

## Ловушки

| Ошибка | Что на самом деле |
|---|---|
| `selector does not match template labels` | классика copy-paste — сверьте два блока |
| Редактирую Pod, изменения пропадают | Pod'ом владеет ReplicaSet — правьте Deployment |
| Scale в JKubeTerm «не работает» | вы выбрали не Deployment (нужен Deployment, иначе info-диалог) |
| Два YAML одним Apply | только один документ за раз |

Дальше: [05 — сеть: Service, port-forward, Ingress, TLS](05-services-ingress.md).

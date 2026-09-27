# Модуль 07. Workloads II: StatefulSet, DaemonSet, Job, CronJob, HPA

> **PDF:** `07-advanced-workloads.pdf` — `tools/export-training-pdf.sh`.

Deployment покрывает stateless. Здесь — всё остальное: стабильные имена и диски
(StatefulSet), по Pod'у на узел (DaemonSet), run-to-completion (Job/CronJob),
автомасштаб (HPA) и защита от слишком рьяного drainage (PDB), плюс QoS-классы.

## Выбор контроллера

| Задача | Контроллер | Почему не Deployment |
|---|---|---|
| Веб/stateless API | Deployment | — (он и нужен) |
| БД, Kafka, etcd (stable id + stable storage) | StatefulSet | Pod'ы `web-0, web-1`, свой PVC у каждого |
| Агент на каждом узле (log-collector, exporter) | DaemonSet | сам раскидывает по узлам, переживает добавление узлов |
| Разовая задача (миграция, бэкап) | Job | `completions: 1`, ретраи до `backoffLimit` |
| По расписанию (отчёт каждый час) | CronJob | `schedule: "0 * * * *"` |
| Переменная нагрузка | HPA поверх Deployment | CPU/RAM/custom metrics |

## StatefulSet: стабильная идентичность

```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: web
  namespace: demo
spec:
  serviceName: web-hs        # headless Service (clusterIP: None) — обязателен
  replicas: 2
  selector:
    matchLabels: {app: web}
  template:
    metadata:
      labels: {app: web}
    spec:
      containers:
        - name: nginx
          image: nginx:1.27
          volumeMounts: [{name: data, mountPath: /usr/share/nginx/html}]
  volumeClaimTemplates:
    - metadata: {name: data}
      spec:
        accessModes: [ReadWriteOnce]
        resources: {requests: {storage: 1Gi}}
```

```bash
kubectl apply -f web-sts.yaml
kubectl -n demo get sts,pods,pvc -l app=web
```

Отличия от Deployment, видимые в JKubeTerm (**Pods** в `demo`): Pod'ы строго
`web-0`, `web-1`; создаются и удаляются **по очереди**; у каждого свой PVC
`data-web-0`. Масштаб **Scale** в JKubeTerm — только Deployment: StatefulSet
масштабируйте через Edit YAML (`replicas`) + Apply либо CLI
(`kubectl scale sts web --replicas=3`).

## DaemonSet: по Pod'у на узел

```bash
kubectl -n kube-system get daemonset   # kube-proxy — живой пример
kubectl -n demo apply -f - <<'EOF'
apiVersion: apps/v1
kind: DaemonSet
metadata:
  name: log-agent
  namespace: demo
spec:
  selector:
    matchLabels: {app: log-agent}
  template:
    metadata:
      labels: {app: log-agent}
    spec:
      containers:
        - name: agent
          image: busybox:1.36
          command: ["sh", "-c", "tail -F /var/log/messages 2>/dev/null || sleep 3600"]
EOF
kubectl -n demo get pods -l app=log-agent -o wide   # по одному на узел
```

В JKubeTerm: вид **DaemonSets** (есть в 14). Tolerations для control-plane узлов
в minikube не нужны (всё в одном узле, taints нет).

## Job и CronJob: run-to-completion

```yaml
apiVersion: batch/v1
kind: Job
metadata:
  name: db-migrate
  namespace: demo
spec:
  backoffLimit: 3
  template:
    spec:
      restartPolicy: Never
      containers:
        - name: migrate
          image: busybox:1.36
          command: ["sh", "-c", "echo migrating && sleep 5 && echo done"]
---
apiVersion: batch/v1
kind: CronJob
metadata:
  name: hourly-report
  namespace: demo
spec:
  schedule: "0 * * * *"
  jobTemplate:
    spec:
      template:
        spec:
          restartPolicy: OnFailure
          containers:
            - name: report
              image: busybox:1.36
              command: ["sh", "-c", "date; echo report-ok"]
```

Виды **Jobs** и **CronJobs** есть в JKubeTerm; созданные Job'ом Pod'ы смотрите
в **Pods** (префикс имени Job'а), логи — как обычно (500 строк, один контейнер
на выбор). `restartPolicy` в Job-Pod'ах: только `Never`/`OnFailure`, дефолтный
`Always` Apply отклонит.

## HPA: автомасштаб по CPU

Нужны: metrics-server (аддон включён) + `resources.requests` у контейнеров
(в манифесте [модуля 04](04-workloads.md) они уже есть):

```bash
kubectl -n demo autoscale deploy demo-nginx --cpu-percent=50 --min=2 --max=5
kubectl -n demo get hpa -w
# нагрузка:
kubectl -n demo run load --image=busybox:1.36 --restart=Never -- \
  sh -c 'while true; do wget -q -O- http://demo-nginx; done'
kubectl -n demo top pods
kubectl -n demo delete pod load   # остановить нагрузку
```

HPA-вида в 14 нет — наблюдайте через CLI, а эффект (число Pod'ов) — в JKubeTerm
**Pods**/**Deployments**. Производственный совет: всегда ставьте `requests`,
иначе HPA слеп; VPA (вертикальный) в minikube — только для понимания идеи.

## PDB и QoS: чтобы эвикшн не убил всё

```yaml
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: demo-pdb
  namespace: demo
spec:
  minAvailable: 1
  selector:
    matchLabels: {app: demo-nginx}
```

QoS-класс выводится из `resources`: requests=limits → Guaranteed; только
requests → Burstable; ничего → BestEffort (первый кандидат на eviction).
В JKubeTerm QoS виден в YAML Pod'а (`status.qosClass`).

## Проверь себя в JKubeTerm

- [ ] Поднимите StatefulSet `web`, удалите `web-1` — какое имя у воскресшего Pod'а и сохранился ли его PVC?
- [ ] Создайте Job, дождитесь Complete, прочитайте логи Pod'а Job'а.
- [ ] Повесьте HPA на `demo-nginx`, дайте нагрузку, зафиксируйте рост реплик в таблице Pods.
- [ ] Объясните, почему **Scale** не работает для StatefulSet в JKubeTerm и как обойти.

## Ловушки

| Ошибка | Что на самом деле |
|---|---|
| StatefulSet без headless Service | `serviceName` обязателен — иначе Apply отклонит |
| Job с `restartPolicy: Always` | запрещён — только Never/OnFailure |
| HPA «не масштабирует» | нет metrics-server или нет `requests` у контейнеров |
| DaemonSet не встал на узел | taints/tolerations (в minikube редкость, в проде — норма) |

Дальше: [08 — наблюдаемость: логи, Events, Prometheus, Grafana](08-observability.md).

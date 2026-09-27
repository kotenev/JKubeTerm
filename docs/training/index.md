# Обучение Kubernetes через JKubeTerm

> **PDF:** по одному файлу на модуль — `tools/export-training-pdf.sh`
> (Chromium `--print-to-pdf`, см. раздел PDF внизу).

Полный учебный курс: от «что такое Pod» до production-приёмов — разворачивание
кластера, все виды workload'ов, сеть, хранилища, Helm, GitOps, безопасность,
диагностика и три сквозных примера деплоя. Каждый модуль заканчивается блоком
**Проверь себя в JKubeTerm** — что нажать и что увидеть, и блоком **Ловушки**.

Стенд курса — [QuickStart](../guides/quickstart-minikube.md): minikube
`--driver=docker --cpus=4 --memory=8192`, Kubernetes v1.37. Все команды и
манифесты проверены на этой связке; версии пинов указаны в каждом модуле.
JKubeTerm ставится командой `mvn clean javafx:run` (Java 21, графическая сессия).

```mermaid
flowchart LR
  B["01 Basics"] --> C["02 Cluster"]
  C --> A["03 Access"]
  A --> W["04 Workloads"]
  W --> S["05 Services Ingress"]
  S --> K["06 Config Storage"]
  K --> D["07 Advanced"]
  D --> O["08 Observability"]
  O --> H["09 Helm GitOps"]
  H --> R["10 Security"]
  R --> T["11 Troubleshooting"]
  T --> P["12 Demo apps"]
```

## Карта модулей

| Модуль | Тема | Время | Что будет уметь выпускник |
|---|---|---|---|
| [01](01-basics.md) | Kubernetes за 20 минут: Pod, Service, Deployment, Namespace, kubeconfig | 20 мин | объяснить control plane / worker, найти контекст, подключиться в JKubeTerm |
| [02](02-cluster.md) | Кластер с нуля: Docker → minikube → addons → multi-profile | 30 мин | поднять/остановить/пересоздать кластер, выбрать драйвер и ресурсы |
| [03](03-access.md) | Доступ: kubeconfig, contexts, RBAC, ServiceAccount-токены | 30 мин | склеить kubeconfig, завести view-only пользователя, дать токен UI |
| [04](04-workloads.md) | Workloads I: Pod, Deployment, ReplicaSet, scale/restart, rollout | 45 мин | задеплоить, откатить, поресайзить через JKubeTerm и CLI |
| [05](05-services-ingress.md) | Сеть I: Service (ClusterIP/NodePort/LoadBalancer), port-forward, Ingress + TLS | 45 мин | открыть приложение тремя способами, выпустить сертификат |
| [06](06-config-storage.md) | Конфигурация и данные: ConfigMap, Secret, PVC/PV, StorageClass | 40 мин | отделить конфиг от образа, пережить рестарт данных |
| [07](07-advanced-workloads.md) | Workloads II: StatefulSet, DaemonSet, Job, CronJob, HPA, PDB, QoS | 50 мин | выбрать контроллер под задачу, настроить автомасштаб |
| [08](08-observability.md) | Наблюдаемость: logs, Events, metrics-server, Prometheus/Grafana | 40 мин | найти причину CrashLoop за 5 минут |
| [09](09-helm-gitops.md) | Helm и GitOps: charts, values, ArgoCD Application | 45 мин | поставить чарт, описать desired state в Git |
| [10](10-security.md) | Безопасность: RBAC-матрица, PodSecurity, NetworkPolicy, Secrets | 40 мин | закрыть лабораторию по чеклисту |
| [11](11-troubleshooting.md) | Диагностика: 15 сценариев от Pending до OOMKilled | 40 мин | чинить по таблице «симптом → JKubeTerm → CLI» |
| [12](12-demo-apps.md) | Три сквозных деплоя: nginx, guestbook, podinfo+monitoring | 60 мин | собрать всё в рабочий стек |

Итого ≈ 8 часов с практикой. Порядок важен: модули ссылаются вперёд только
как «заглянем», назад — как «мы уже умеем».

## Как проходить

1. Поднимите стенд по [QuickStart](../guides/quickstart-minikube.md) до раздела
   «Подключение JKubeTerm» — дальше каждый модуль говорит, что доставить.
2. Терминал — для установки (`kubectl`/`helm`/`minikube`); JKubeTerm — для
   наблюдения и точечных операций (Apply, Scale, Logs, Port-forward).
   Разделение осознанное: JKubeTerm — `helm list` только, установок чартов нет
   ([external-tools](../architecture/external-tools.md)).
3. После каждого модуля выполняйте **Проверь себя** — это и есть экзамен.
4. Ломайте стенд смело: `minikube delete` + QuickStart возвращают всё за 15 минут.

## PDF

Каждый модуль — отдельный PDF рядом с источником (`01-basics.pdf` … `12-demo-apps.pdf`):

```bash
mkdocs serve &                       # http://127.0.0.1:8000
tools/export-training-pdf.sh         # все 12 модулей
tools/export-training-pdf.sh --pages 04-workloads,05-services-ingress
```

Скрипт — тот же приём, что `tools/export-guides-pdf.sh` (headless Chromium
`--print-to-pdf-no-header`). PDF — git-игнор, артефакты сборки.

## Связь с остальной документацией

- [QuickStart](../guides/quickstart-minikube.md) — стенд курса, карта port-forward.
- [User guide](../guides/user-guide.md) — все кнопки JKubeTerm построчно.
- [Admin guide](../guides/admin-guide.md) — RBAC, GitOps-раскладка, бэкапы, апгрейды.
- [Resource catalog](../architecture/resources.md) — 14 видов и Fabric8-вызовы.
- [External tools](../architecture/external-tools.md) — почему часть операций только в CLI.

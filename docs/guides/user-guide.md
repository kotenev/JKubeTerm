# Руководство пользователя JKubeTerm

> **PDF:** [user-guide.pdf](user-guide.pdf) — печать этой страницы
> (кнопка доступна после `tools/export-guides-pdf.sh`).

JKubeTerm — десктопный Kubernetes-клиент (JavaFX 21, Fabric8 7.9.0): просмотр
ресурсов, YAML, логи, exec, port-forward, scale/restart, `helm list`.
Установка чартов, интерактивный shell и удаление port-forward кнопкой —
не реализованы осознанно, см. ограничения внизу.

## Запуск и подключение {#connect}

Меню **Tutorial** (рядом с Help) запускает интерактивный тур прямо в приложении:
**Start guided tour** (7 шагов: connect → browse → manifest → apply → pod tools →
deployments), **First deploy drill** (шаблон → правка → namespace → apply → найти →
удалить) и **Debug flow drill** (Pod → logs → console → exec → forward).
Целевые элементы подсвечиваются классом `tutorial-target` (жёлтая рамка +
тень, `jkubeterm.css`), сверху висит подсказка `«Tutorial i/N — шаг»`,
в панели действий появляются **Next/Finish** и **Exit tutorial**.
Выполнение шага (Connect, Refresh, выбор строки, Apply…) само двигает тур
вперёд (`advanceTutorial(event)`); сценарии лежат в `Tutorial.java` без JavaFX,
покрыты `TutorialTest`.

| Шаг | Действие | Что происходит под капотом |
|---|---|---|
| 1 | `mvn clean javafx:run` | окно `1380 x 840`, `JKubeTermApp.start` (`JKubeTermApp.java:40`) |
| 2 | Комбо **Context** | `KubeconfigLoader.paths/contexts` читает `$KUBECONFIG` или `~/.kube/config`, показывает `«name  [filename]»` ([kubeconfig](../architecture/kubeconfig.md)) |
| 3 | **Connect** | на worker строится `KubernetesService`, проверяются `version()` + `namespaces()`, старый клиент закрывается (`JKubeTermApp.java:103`) |
| 4 | Статус `Connected: X \| Kubernetes vY` | дальше все операции идут в этом контексте |
| 5 | **↻ Config** | перечитать kubeconfig-файлы без перезапуска (`JKubeTermApp.java:93`) |

Правила переподключения: повторный **Connect** строит новый клиент и только при
успехе закрывает старый; при ошибке новый закрывается, старый продолжает работать,
показывается диалог ошибки. Выход (`stop`) гасит port-forward процессы →
`worker.shutdownNow()` → `service.close()` (`JKubeTermApp.java:237`).

## Навигация и просмотр {#browse}

Каждый вид слева имеет свою векторную иконку и однострочное объяснение под
фильтром — что это за сущность и зачем она новичку («Under the hood» для
выбранного объекта рассказывает, какие контроллеры им занимаются, а «Best
practices» показывает все wiki-практики вида: красные/жёлтые — нарушения,
зелёные — проходящие; каждая карточка с Fix-подсказкой и кликабельной
doc-ссылкой на kubernetes.io). Best practices свёрнуты в два компактных
TitledPane (⚠️ Issues / ✓ Passing): однострочные кликабельные карточки
`[check-id] заголовок` открывают wiki-диалог (дабл-клик тоже).
Реестр практик — Markdown-база (`Practices Wiki…` в Help: просмотр, правление
`~/.jkubeterm/practices.md`, Reload без перезапуска). Правая колонка — SplitPane
с разделителями (Manifest / Object / Under the hood / Best practices / Output),
позиции и размер окна хранятся в `~/.jkubeterm/config.properties`; меню Settings:
Font (семейство + размер), Zoom in/out/reset (⌘+/⌘-/⌘0), Reset layout. Строки Object view кликабельны (`?` — помощь по
атрибуту со ссылкой на доку, `⤓` — экспорт PEM). «What's new in Kubernetes…»
показывает новинки версии сервера.

Двойной клик по строке таблицы (или правый клик → Drill down…) открывает
связанные ресурсы: Pod → Node/Events/Services/ConfigMap/PVC/Job, Deployment →
свои Pod'ы, Service → Pod'ы за селектором + Ingress'ы, Ingress → backend
Service'ы, PVC ⇄ PV, Node → Pod'ы на нём, Namespace → переключение комбо,
Event → involved object. Строки `⇄ Relations` в Object view тоже кликабельны
(поиск по каталогу видов). Несколько совпадений — диалог выбора, один —
прямой переход с фокусом на строке.

- **Kinds** (слева): 14 видов из `ResourceKind` (`PODS` выбран стартово).
  Кластерные (`Nodes`, `Namespaces`, `PersistentVolumes`) игнорируют namespace;
  остальным пустой namespace = `default` (`KubernetesService.java:25`).
- **Namespace** (комбо): смена триггерит `refresh()`; список приходит с API.
- **Refresh**: перезапрос `service.list()` на worker, строки кэшируются в `currentItems`.
- **Filter**: локальная фильтрация по имени, case-insensitive, без запросов к API.
- **Таблица** (Name / Namespace / Kind / Created): выбор строки грузит YAML
  в правое поле через `service.yaml()` **на FX-потоке** (чисто in-memory),
  сбрасывает `editMode` и `editable=false` (`JKubeTermApp.java:57`).
- **Namespaces / Nodes / PersistentVolumes** требуют cluster-scope read RBAC.

## Manifest: просмотр, правка, применение {#manifest}

| Элемент | Поведение |
|---|---|
| Просмотр | read-only по умолчанию; выбор строки перезаписывает поле |
| **Edit YAML** (чекбокс) | `details.setEditable()`; включается вручную или кнопкой **New YAML** (шаблон ConfigMap) |
| **Apply YAML** | confirm с именем контекста → `service.apply()` → server-side apply → `refresh()` (`JKubeTermApp.java:141`) |
| **New YAML** | вставляет шаблон ConfigMap `example`, включает edit |
| **Save YAML…** | `FileChooser` → `resource.yaml`, только локально (`JKubeTermApp.java:225`) |
| **Delete** | confirm `«Delete Kind / name from context?»` → `service.delete()` → `refresh()` |

Валидация Apply (`KubernetesService.java:58`): YAML обязан иметь `kind`,
`apiVersion`, `metadata.name`; пустой namespace подставляется из комбо для
не-кластерных kind; кластерный allowlist — `Node`, `Namespace`,
`PersistentVolume`, `ClusterRole`, `ClusterRoleBinding`, `CustomResourceDefinition`,
`StorageClass`. Неизвестные кластерные kind упадут серверно — это известное
ограничение без API discovery (roadmap-4).

## Pod: логи и exec {#pod-logs-exec}

**Pod logs** (`JKubeTermApp.java:152`, только Pod):

1. Worker запрашивает `service.containers(ns, pod)`.
2. FX показывает `ChoiceDialog` со списком контейнеров.
3. Выбор → worker читает `service.logs(ns, pod, container, 500)` —
   последние 500 строк, **не streaming**.
4. `console.setText(log)` — поле перезаписывается, не дописывается.
5. Пустой список контейнеров / Cancel — возврат без действий.

**Exec command** (`JKubeTermApp.java:169`, только Pod):

- `TextInputDialog` (дефолт `/bin/sh`), принимается **один executable** —
  shell-парсинга нет, пайпы/редиректы не работают.
- Argv: `kubectl --context … --kubeconfig … --namespace … exec <pod> -- <cmd>`.
- `ExternalTools.run(…, 30)` — 30 с таймаут, `destroyForcibly()` при превышении.
- Результат или `IOException` с выводом инструмента — в консоль/диалог.

## Port-forward {#port-forward}

Только **Pod** или **Service** (`JKubeTermApp.java:180`):

1. Диалог `local:remote` (дефолт `8080:80`).
2. Валидация: regex `[0-9]{1,5}:[0-9]{1,5}`, оба порта 1–65535, иначе info-диалог.
3. `ProcessBuilder(kubectl … port-forward --address 127.0.0.1 kind/name ports)` —
   стартует **на FX-потоке**, процесс кладётся в `portProcesses`.
4. `KUBECONFIG=<context file>` в окружении; бининг строго loopback.
5. Вывод процесса draining на worker, показывается в консоли по завершении.
6. Процесс живёт до выхода из приложения — кнопки «остановить» нет.

Типовые пары для лаборатории: ArgoCD `8080:80`, Grafana `3000:80`,
Prometheus `9090:9090`, см. раздел Access map в [quickstart-minikube](quickstart-minikube.md).

## Deployments: scale и restart {#scale-restart}

| Кнопка | Диалог | Вызов |
|---|---|---|
| **Scale** (только Deployment) | `Set replica count`, неотрицательное int, иначе info; затем confirm `«Scale name to N replicas?»` | `service.scaleDeployment(ns, name, n)` → `refresh()` |
| **Restart** (только Deployment) | confirm `«Roll out a restart of name?»` | `restartDeployment()` — аннотация `kubectl.kubernetes.io/restartedAt = Instant.now()` на pod-template (kubectl-идиома) |

Не-Deployment для этих кнопок (и не-Pod для логов/exec, не-Pod/Service для
форварда) — info-диалог без побочных эффектов.

## Helm releases

Кнопка собирает `helm --kube-context … --kubeconfig … --namespace <ns|default> list`
и выполняет через `ExternalTools.run(…, 30)`; вывод — в консоль. Это **только листинг**:
install/upgrade/rollback делайте в терминале — `helm` обязан быть на `PATH`.

## Minikube addons одной кнопкой

Кнопка **Addons…** включает аддон minikube без терминала: выбор из curated-списка
(ingress, metrics-server, storage-provisioner, dashboard, …), затем профиль
(по умолчанию — имя подключённого контекста), затем confirm с точной командой.
Выполняется `minikube -p <profile> addons enable <addon>` с таймаутом 600 с
(пуллы образов занимают минуты). Во время установки:

- статус-бар показывает **progress bar с динамическим ETA** (`elapsed · ~left (timeout in…)`),
  фазы pull → verify → enable определяются по строкам вывода (`AddonPhases`);
- **каждая строка вывода minikube сразу дописывается в консоль** — видно, на чём висит
  (pull образа, verify версии, enabling), плюс argv, профиль, контекст и kubeconfig в шапке;
- кнопка **Cancel** в статус-баре убивает процесс (`destroyForcibly`, `Cancelled by user after Ns`);
- при провале — transcript + hints (minikube на PATH? профиль существует? Docker запущен?).

После успеха таблица обновляется. `minikube` обязан быть на `PATH`.

## Консоль, статус, ошибки {#console-status}

- **Output / logs** (`console`): `setText()` — всегда последнее сообщение,
  история не накапливается.
- **Status** (внизу): `«N contexts from M kubeconfig file(s)»`,
  `«Connecting to X…»`, `«Connected: X | Kubernetes vY»`,
  `«N Pods | context»`, `«Loading …»`, ошибки `«title: message»`.
- Ошибки worker: `error("Kubernetes operation failed", e)` — диалог + статус.
  `CERTIFICATE_VERIFY_FAILED` — проверяйте контекст/CA/endpoint, **не**
  отключайте проверку сертификатов (см. [security](../operations/security.md)).

## Горячие клавиши документации (сайт, не приложение) {#docs-hotkeys}

На сайте документации: `N` — левая навигация, `T` — ToC, `G` — меню разделов;
на диаграммах: `+/-/0` — зум, `F` — fullscreen, `H` — fit-height, `Esc` — выход.

## Ограничения (честно) {#limits}

| Нет в JKubeTerm | Обходной путь |
|---|---|
| `helm install/upgrade/rollback` | терминал + `helm` |
| Интерактивный exec/TTY | одноразовый executable; для shell — `kubectl exec -it` в терминале |
| Streaming логов | повторный **Pod logs** (снапшот 500 строк) |
| Остановка port-forward | перезапуск приложения гасит все форварды |
| API discovery / CRD-браузер | только 14 видов + apply любых валидных YAML |
| Менеджер контекстов | правьте kubeconfig внешними инструментами, затем **↻ Config** |

Подробнее о потоках: [threading](../architecture/threading.md),
[data-flow](../architecture/data-flow.md); внешніе процессы:
[external-tools](../architecture/external-tools.md); каталог видов:
[resources](../architecture/resources.md).

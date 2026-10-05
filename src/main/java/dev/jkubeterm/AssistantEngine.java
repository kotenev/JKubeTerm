package dev.jkubeterm;

import io.fabric8.kubernetes.api.model.HasMetadata;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Offline AI assistant: deterministic, rule-based answers from local data only
 * ({@link ClusterAdvisor} findings, {@link ResourceInspector} sections,
 * {@link PracticeRegistry} wiki, {@link ClusterVisuals} docs). No network,
 * no telemetry, no new dependencies. Read-only by design: it never applies,
 * deletes, scales or restarts anything — destructive flows stay behind the
 * existing confirmation dialogs. Pure logic, no JavaFX — unit tested.
 */
public final class AssistantEngine {
    private AssistantEngine() {}

    public record Answer(String text, List<String> sources) {}

    private static final int MAX_PRACTICES = 3;
    private static final int MAX_ROWS = 12;

    private static final Set<String> STOPWORDS = Set.of(
        "the", "a", "an", "and", "or", "for", "with", "this", "that", "what",
        "why", "how", "is", "are", "my", "in", "on", "of", "to", "it",
        "это", "что", "как", "почему", "мой", "моя", "мое", "для", "или",
        "подскажите", "подскажи", "пожалуйста");

    // -- entry point -------------------------------------------------------

    public static Answer answer(String question, HasMetadata selected,
                                List<ClusterAdvisor.Finding> findings,
                                List<PracticeRegistry.Practice> registry, String namespace) {
        List<ClusterAdvisor.Finding> safeFindings = findings == null ? List.of() : findings;
        List<PracticeRegistry.Practice> safeRegistry = registry == null ? List.of() : registry;
        String q = question == null ? "" : question.strip();
        boolean ru = isRussian(q);
        String ns = namespace == null || namespace.isBlank() ? "default" : namespace.strip();

        // Order matters: most specific helpful intent first. All branches are
        // text-only (the engine has no execution path), so a "why does delete
        // fail?" question is safely answered with a diagnosis, while a bare
        // "delete this pod" still gets an explicit read-only refusal.
        // App flows beat raw kubectl ("exec command" → in-app Exec limits);
        // explicit kubectl beats generic explain ("kubectl describe" → commands,
        // bare "describe this" → explanation); aggressive write-guard goes last
        // so "what is scale?" explains instead of refusing.
        if (q.isEmpty())
            return selected == null ? capabilities(ru)
                : new Answer(explainSelected(selected, safeFindings, ru), sourcesOf(safeFindings, List.of()));
        if (isDiagnoseIntent(q))
            return selected == null ? noSelection(ru)
                : new Answer(diagnoseSelected(selected, safeFindings, ru), sourcesOf(safeFindings, List.of()));
        String app = appHelp(q, ru);
        if (app != null) return new Answer(app, List.of());
        if (isCommandsIntent(q))
            return selected == null ? generalCommands(ru, ns)
                : new Answer(commandsFor(selected, ns, ru), List.of());
        if (isExplainIntent(q))
            return selected == null ? generalKindHelp(q, ru)
                : new Answer(explainSelected(selected, safeFindings, ru), sourcesOf(safeFindings, List.of()));
        if (isWriteIntent(q))
            return writeGuard(ru, selected);
        List<PracticeRegistry.Practice> hits = search(q, safeRegistry);
        if (!hits.isEmpty()) return formatPractices(hits, ru, selected);
        return selected == null ? capabilities(ru)
            : new Answer(explainSelected(selected, safeFindings, ru) + "\n\n" + capabilitiesText(ru, true),
                sourcesOf(safeFindings, List.of()));
    }

    // -- intent detection (keyword, offline) --------------------------------

    static boolean isRussian(String text) {
        if (text == null) return false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x0400 && c <= 0x04FF) return true;
        }
        return false;
    }

    static boolean isWriteIntent(String q) {
        // Intent verbs, not concept nouns: "what is scale?" explains, while
        // "scale this" / "scale to 5" is a write request. Requires a pronoun,
        // quantifier, target kind or recreation verb nearby to fire.
        String lower = q.toLowerCase(Locale.ROOT);
        boolean verb = lower.contains("delete") || lower.contains("remove it") || lower.contains("apply")
            || lower.contains("scale") || lower.contains("restart") || lower.contains("recreate")
            || lower.contains("kill") || lower.contains("удали") || lower.contains("удалить")
            || lower.contains("примени") || lower.contains("масштаб") || lower.contains("реплик")
            || lower.contains("перезапусти") || lower.contains("пересоздай") || lower.contains("создай");
        if (!verb) return false;
        return lower.contains("this") || lower.contains("that") || lower.contains(" it")
            || lower.contains("pod") || lower.contains("deploy") || lower.contains("service")
            || lower.contains("to ") || lower.matches(".*\\d.*")
            || lower.contains("recreate") || lower.contains("kill") || lower.contains("пересоздай")
            || lower.contains("под") || lower.contains("это") || lower.contains("его");
    }

    static boolean isDiagnoseIntent(String q) {
        String lower = q.toLowerCase(Locale.ROOT);
        return lower.contains("why") || lower.contains("diagnos") || lower.contains("fail")
            || lower.contains("error") || lower.contains("crash") || lower.contains("pending")
            || lower.contains("stuck") || lower.contains("not working") || lower.contains("not ready")
            || lower.contains("problem") || lower.contains(" fix") || lower.startsWith("fix")
            || lower.contains("backoff") || lower.contains("почему") || lower.contains("причин")
            || lower.contains("ошибк") || lower.contains("не работает") || lower.contains("не запускает")
            || lower.contains("падает") || lower.contains("проблем") || lower.contains("почини")
            || lower.contains("исправ") || lower.contains("диагност") || lower.contains("статус");
    }

    static boolean isExplainIntent(String q) {
        String lower = q.toLowerCase(Locale.ROOT);
        return lower.contains("what is") || lower.contains("what are") || lower.contains("explain")
            || lower.contains("describe") || lower.contains("tell me about") || lower.contains("what does")
            || lower.contains("что это") || lower.contains("что такое") || lower.contains("объясни")
            || lower.contains("расскажи") || lower.contains("опиши");
    }

    static boolean isCommandsIntent(String q) {
        // Keep bare "describe"/"command" for explain: only an explicit kubectl
        // mention (or plural "commands") signals a request for CLI snippets.
        String lower = q.toLowerCase(Locale.ROOT);
        return lower.contains("kubectl") || lower.contains("commands")
            || lower.contains("команд") || lower.contains("терминальн");
    }

    // -- explain / diagnose --------------------------------------------------

    public static String explainSelected(HasMetadata resource, List<ClusterAdvisor.Finding> findings, boolean ru) {
        if (resource == null) return capabilitiesText(ru, false);
        List<ClusterAdvisor.Finding> safe = findings == null ? List.of() : findings;
        String kind = resource.getKind() == null ? "?" : resource.getKind();
        String name = resource.getMetadata() == null || resource.getMetadata().getName() == null
            ? "?" : resource.getMetadata().getName();
        String header = ru ? "Объяснение: " + kind + " '" + name + "'." : "Explanation: " + kind + " '" + name + "'.";
        StringBuilder out = new StringBuilder(header).append('\n');
        out.append(ClusterVisuals.explainKind(kind)).append('\n');
        out.append(summarySections(resource)).append('\n');
        long bad = safe.stream().filter(f -> f.severity() != ClusterAdvisor.Severity.INFO).count();
        if (safe.isEmpty()) {
            out.append(ru ? "Best-practices пока не посчитаны — нажмите Refresh, затем спросите «диагностика»."
                : "Best-practices not computed yet — press Refresh, then ask for a diagnosis.");
        } else if (bad == 0) {
            out.append(ru ? "Проблем не найдено (" + safe.size() + " passing-проверок). Спросите «диагностика» для деталей."
                : "No issues found (" + safe.size() + " passing checks). Ask for a diagnosis for details.");
        } else {
            out.append(ru ? "Найдено проблем: " + bad + " из " + safe.size() + ". Топ:\n"
                : "Issues found: " + bad + " of " + safe.size() + ". Top:\n");
            List<ClusterAdvisor.Finding> sorted = sortBySeverity(safe);
            int shown = 0;
            for (ClusterAdvisor.Finding f : sorted) {
                if (f.severity() == ClusterAdvisor.Severity.INFO) continue;
                if (shown >= 2) break;
                out.append("• [").append(f.check()).append("] ").append(f.message())
                    .append(ru ? "\n  Исправление: " : "\n  Fix: ").append(f.fix()).append('\n');
                shown++;
            }
            out.append(ru ? "Полный список — «почему ...?» или панель Best practices."
                : "Full list — ask “why …?” or open the Best practices pane.");
        }
        out.append('\n').append(offlineFooter(ru));
        return out.toString();
    }

    public static String diagnoseSelected(HasMetadata resource, List<ClusterAdvisor.Finding> findings, boolean ru) {
        if (resource == null) return (ru ? "Выберите строку в таблице — диагностика строится по выбранному объекту."
            : "Select a table row first — diagnosis is built from the selected object.") + "\n" + offlineFooter(ru);
        List<ClusterAdvisor.Finding> safe = findings == null ? List.of() : findings;
        String kind = resource.getKind() == null ? "?" : resource.getKind();
        String name = resource.getMetadata() == null || resource.getMetadata().getName() == null
            ? "?" : resource.getMetadata().getName();
        StringBuilder out = new StringBuilder(ru ? "Диагностика: " + kind + " '" + name + "'.\n"
            : "Diagnosis: " + kind + " '" + name + "'.\n");
        if (safe.isEmpty()) {
            out.append(ru ? "Наблюдений нет. Проверьте Events (новейшие сверху) и Pod logs (последние 500 строк) — они точнее любых эвристик."
                : "No observations. Check Events (newest first) and Pod logs (last 500 lines) — they beat any heuristic.");
            return out.append('\n').append(offlineFooter(ru)).toString();
        }
        List<ClusterAdvisor.Finding> sorted = sortBySeverity(safe);
        long bad = sorted.stream().filter(f -> f.severity() != ClusterAdvisor.Severity.INFO).count();
        if (bad == 0) {
            out.append(ru ? "Критичных проблем нет (" + safe.size() + " passing). " : "No critical issues (" + safe.size() + " passing). ");
            out.append(ru ? "Если поведение всё равно странное — смотрите Events и логи: эвристики не видят рантайм."
                : "If behaviour is still odd — read Events and logs: heuristics cannot see runtime.");
            return out.append('\n').append(offlineFooter(ru)).toString();
        }
        out.append(ru ? "Причина (сначала важное):\n" : "Likely causes (most severe first):\n");
        for (ClusterAdvisor.Finding f : sorted) {
            if (f.severity() == ClusterAdvisor.Severity.INFO) continue;
            out.append("• ").append(f.severity() == ClusterAdvisor.Severity.CRITICAL ? "[CRITICAL] " : "[WARN] ")
                .append('[').append(f.check()).append("] ").append(f.message()).append('\n')
                .append(ru ? "  Исправление: " : "  Fix: ").append(f.fix()).append('\n');
            if (!f.docs().isEmpty()) out.append("  Docs: ").append(f.docs().getFirst()).append('\n');
        }
        out.append(ru ? "Дальше: Events newest-first → Pod logs (контейнер) → Drill down по Relations."
            : "Next: Events newest-first → Pod logs (pick container) → Drill down via Relations.");
        return out.append('\n').append(offlineFooter(ru)).toString();
    }

    private static List<ClusterAdvisor.Finding> sortBySeverity(List<ClusterAdvisor.Finding> findings) {
        List<ClusterAdvisor.Finding> sorted = new ArrayList<>(findings);
        sorted.sort(Comparator.comparingInt(f -> switch (f.severity()) {
            case CRITICAL -> 0;
            case WARN -> 1;
            case INFO -> 2;
        }));
        return sorted;
    }

    private static String summarySections(HasMetadata resource) {
        try {
            ResourceInspector.Inspection inspection = ResourceInspector.inspect(resource);
            StringBuilder out = new StringBuilder();
            boolean secret = "Secret".equals(resource.getKind());
            int rows = 0;
            for (ResourceInspector.Section section : inspection.sections()) {
                if (rows >= MAX_ROWS) break;
                out.append("§ ").append(section.title()).append(": ");
                List<String> cells = new ArrayList<>();
                for (ResourceInspector.Row row : section.rows()) {
                    if (rows >= MAX_ROWS) break;
                    // Never echo secret values — field names only.
                    cells.add(secret ? row.field() : row.field() + (row.value().isEmpty() ? "" : "=" + truncate(row.value(), 80)));
                    rows++;
                }
                out.append(String.join(", ", cells)).append('\n');
            }
            if (!inspection.relations().isEmpty()) {
                out.append("Relations: ").append(inspection.relations().size()).append(" (");
                List<String> links = new ArrayList<>();
                for (ResourceInspector.Relation r : inspection.relations().subList(0, Math.min(3, inspection.relations().size())))
                    links.add(r.label() + "→" + r.to());
                out.append(String.join(", ", links)).append(")\n");
            }
            out.append("Docs: ").append(ClusterVisuals.docsUrl(resource.getKind()));
            return out.toString().strip();
        } catch (Exception e) {
            return "Docs: " + ClusterVisuals.docsUrl(resource.getKind());
        }
    }

    // -- read-only kubectl ----------------------------------------------------

    public static String commandsFor(HasMetadata resource, String namespace, boolean ru) {
        String ns = namespace == null || namespace.isBlank() ? "default" : namespace.strip();
        String kind = resource.getKind() == null ? "pod" : resource.getKind().toLowerCase(Locale.ROOT);
        String name = resource.getMetadata() == null || resource.getMetadata().getName() == null
            ? "<name>" : resource.getMetadata().getName();
        StringBuilder out = new StringBuilder(ru ? "Только чтение — команды для " + kind + " '" + name + "' (ns=" + ns + "):\n"
            : "Read-only commands for " + kind + " '" + name + "' (ns=" + ns + "):\n");
        out.append("kubectl get ").append(kind).append(' ').append(name).append(" -n ").append(ns).append(" -o yaml\n");
        out.append("kubectl describe ").append(kind).append(' ').append(name).append(" -n ").append(ns).append('\n');
        switch (kind) {
            case "pod" -> {
                out.append("kubectl logs ").append(name).append(" -n ").append(ns).append(" --tail=500\n");
                out.append("kubectl get events -n ").append(ns).append(" --sort-by=.lastTimestamp\n");
            }
            case "deployment" -> {
                out.append("kubectl rollout status deployment/").append(name).append(" -n ").append(ns).append('\n');
                out.append("kubectl get events -n ").append(ns).append(" --sort-by=.lastTimestamp\n");
            }
            case "service" -> out.append("kubectl get endpoints ").append(name).append(" -n ").append(ns).append('\n');
            case "ingress" -> out.append("kubectl get ingress ").append(name).append(" -n ").append(ns).append(" -o wide\n");
            default -> out.append("kubectl get events -n ").append(ns).append(" --sort-by=.lastTimestamp\n");
        }
        out.append(ru ? "Изменения — только через кнопки Apply/Delete/Scale/Restart с подтверждением."
            : "Writes go only through Apply/Delete/Scale/Restart buttons with confirmation.");
        return out.append('\n').append(offlineFooter(ru)).toString();
    }

    private static Answer generalCommands(boolean ru, String ns) {
        String text = (ru ? "Только чтение — базовые команды (подставьте имя и ns=" + ns + "):\n"
            : "Read-only basics (substitute name, ns=" + ns + "):\n")
            + "kubectl get pods -n " + ns + "\n"
            + "kubectl describe pod <name> -n " + ns + "\n"
            + "kubectl logs <pod> -n " + ns + " --tail=500\n"
            + "kubectl get events -n " + ns + " --sort-by=.lastTimestamp\n"
            + "\n" + offlineFooter(ru);
        return new Answer(text, List.of());
    }

    // -- app help / write guard / capabilities ---------------------------------

    private static String appHelp(String q, boolean ru) {
        String lower = q.toLowerCase(Locale.ROOT);
        boolean app = lower.contains("connect") || lower.contains("refresh") || lower.contains("namespace")
            || lower.contains("filter") || lower.contains("manifest") || lower.contains("apply yaml")
            || lower.contains("port-forward") || lower.contains("port forward") || lower.contains("exec")
            || lower.contains("helm") || lower.contains("addon") || lower.contains("diagnose")
            || lower.contains("tutorial") || lower.contains("подключ") || lower.contains("контекст")
            || lower.contains("неймспейс") || lower.contains("пространство имен") || lower.contains("фильтр")
            || lower.contains("манифест") || lower.contains("проброс") || lower.contains("портов")
            || lower.contains("аддон") || lower.contains("диагностик") || lower.contains("обучен");
        if (!app) return null;
        if (ru) return "JKubeTerm: выберите контекст → Connect → Namespace → Refresh. "
            + "Фильтр — локальный. Строка → YAML справа, Object — разбор, Under the hood — механика, "
            + "Best practices — проверки с исправлениями. Pod logs — 500 строк, Exec — один исполняемый файл (30 с), "
            + "Port forward — только 127.0.0.1, Helm — только list, Addons — minikube. Диагностика — кнопка Diagnose. "
            + "Доки: http://127.0.0.1:8000/guides/ .\n" + offlineFooter(true);
        return "JKubeTerm: pick a context → Connect → Namespace → Refresh. "
            + "Filter is local. Row → YAML on the right, Object breakdown, Under the hood, "
            + "Best practices with fixes. Pod logs — 500 lines, Exec — single executable (30 s), "
            + "Port forward — 127.0.0.1 only, Helm — list only, Addons — minikube. Diagnose button for connection report. "
            + "Docs: http://127.0.0.1:8000/guides/ .\n" + offlineFooter(false);
    }

    private static Answer writeGuard(boolean ru, HasMetadata selected) {
        String target = selected == null || selected.getMetadata() == null ? ""
            : " (" + selected.getKind() + " '" + selected.getMetadata().getName() + "')";
        String text = ru
            ? "Только чтение: я ничего не меняю в кластере" + target + ". "
            + "Для изменений используйте кнопки с подтверждением: Apply YAML (server-side apply), "
            + "Delete, Scale, Restart. Я могу объяснить последствия и подсказать безопасный порядок."
            : "Read-only: I change nothing in the cluster" + target + ". "
            + "Use the confirmation-guarded buttons for writes: Apply YAML (server-side apply), "
            + "Delete, Scale, Restart. I can explain consequences and a safe order.";
        return new Answer(text + "\n" + offlineFooter(ru), List.of());
    }

    private static Answer noSelection(boolean ru) {
        String text = ru ? "Выберите строку в таблице — отвечу по конкретному объекту (объяснение, диагностика, kubectl). "
            + "Или спросите общее: «что такое Service», «как устроен ingress tls», «как подключиться»."
            : "Select a table row — I answer for the concrete object (explain, diagnose, kubectl). "
            + "Or ask generally: “what is a Service”, “ingress tls”, “how to connect”.";
        return new Answer(text + "\n" + offlineFooter(ru), List.of());
    }

    private static Answer generalKindHelp(String q, boolean ru) {
        String lower = q.toLowerCase(Locale.ROOT);
        for (String kindName : List.of("Pod", "Deployment", "StatefulSet", "DaemonSet", "Service",
            "ConfigMap", "Secret", "Job", "CronJob", "Ingress",
            "PersistentVolumeClaim", "PersistentVolume", "Node", "Namespace", "Event")) {
            if (lower.contains(kindName.toLowerCase(Locale.ROOT))) {
                String text = (ru ? "Объяснение: " + kindName + ". " : "Explanation: " + kindName + ". ")
                    + ClusterVisuals.explainKind(kindName)
                    + "\nDocs: " + ClusterVisuals.docsUrl(kindName)
                    + "\n" + offlineFooter(ru);
                return new Answer(text, List.of(ClusterVisuals.docsUrl(kindName)));
            }
        }
        return capabilities(ru);
    }

    private static Answer capabilities(boolean ru) {
        return new Answer(capabilitiesText(ru, false), List.of());
    }

    private static String capabilitiesText(boolean ru, boolean appended) {
        String head = ru ? "Оффлайн-помощник (без сети, только локальная wiki). Умею:\n"
            : "Offline assistant (no network, local wiki only). I can:\n";
        String body = ru
            ? """
                • Объяснить выбранный объект («что это?», «объясни»)
                • Найти причину («почему Pod в CrashLoop?», «диагностика»)
                • Подсказать read-only kubectl («какие команды?»)
                • Ответить из best-practices wiki («seccomp», «probes», «tls»)
                • Подсказать по приложению («как подключиться»)
                Выберите строку для контекста. Не применяю и не удаляю — только объясняю."""
            : """
                • Explain the selected object (“what is this?”, “explain”)
                • Diagnose (“why is this Pod in CrashLoop?”, “diagnose”)
                • Suggest read-only kubectl (“which commands?”)
                • Answer from the best-practices wiki (“seccomp”, “probes”, “tls”)
                • Help with the app (“how to connect”)
                Select a row for context. I never apply or delete — explanations only.""";
        // When appended after an explanation the head is redundant.
        return (appended ? body : head + body) + "\n" + offlineFooter(ru);
    }

    private static String offlineFooter(boolean ru) {
        return ru ? "— Оффлайн: только локальные данные, без сети. Только чтение: изменения — через кнопки с подтверждением."
            : "— Offline: local data only, no network. Read-only: writes go through confirmation-guarded buttons.";
    }

    // -- wiki search -----------------------------------------------------------

    static List<PracticeRegistry.Practice> search(String query, List<PracticeRegistry.Practice> registry) {
        Set<String> tokens = tokensOf(query);
        if (tokens.isEmpty() || registry.isEmpty()) return List.of();
        Map<PracticeRegistry.Practice, Integer> scores = new LinkedHashMap<>();
        for (PracticeRegistry.Practice p : registry) {
            int score = 0;
            Set<String> idTokens = tokensOf(p.id().replace('-', ' ').replace('_', ' '));
            Set<String> titleTokens = tokensOf(p.title());
            for (String t : tokens) {
                if (idTokens.contains(t)) score += 3;
                if (titleTokens.contains(t)) score += 2;
                for (String kind : p.kinds())
                    if (kind.toLowerCase(Locale.ROOT).contains(t)) { score += 2; break; }
            }
            if (score > 0) scores.put(p, score);
        }
        return scores.entrySet().stream()
            .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
            .limit(MAX_PRACTICES).map(Map.Entry::getKey).toList();
    }

    static Set<String> tokensOf(String text) {
        Set<String> tokens = new HashSet<>();
        if (text == null) return tokens;
        for (String part : text.toLowerCase(Locale.ROOT).split("[^\\p{L}0-9]+")) {
            if (part.length() >= 3 && !STOPWORDS.contains(part)) tokens.add(part);
        }
        return tokens;
    }

    private static Answer formatPractices(List<PracticeRegistry.Practice> hits, boolean ru, HasMetadata selected) {
        StringBuilder out = new StringBuilder(ru ? "Из локальной wiki (best-practices):\n" : "From the local wiki (best-practices):\n");
        List<String> sources = new ArrayList<>();
        for (PracticeRegistry.Practice p : hits) {
            out.append("• [").append(p.id()).append("] ").append(p.title()).append('\n');
            if (!p.why().isEmpty()) out.append("  ").append(truncate(p.why(), 240)).append('\n');
            out.append(ru ? "  Исправление: " : "  Fix: ").append(truncate(p.fix(), 240)).append('\n');
            if (!p.docs().isEmpty()) {
                out.append("  Docs: ").append(p.docs().getFirst()).append('\n');
                sources.add(p.docs().getFirst());
            }
        }
        if (selected != null && selected.getKind() != null)
            out.append(ru ? "Привязка к выбору: " : "Selected context: ")
                .append(selected.getKind()).append(" '")
                .append(selected.getMetadata() == null ? "?" : String.valueOf(selected.getMetadata().getName()))
                .append("'.\n");
        out.append(offlineFooter(ru));
        return new Answer(out.toString(), List.copyOf(sources));
    }

    private static List<String> sourcesOf(List<ClusterAdvisor.Finding> findings, List<String> extra) {
        Set<String> urls = new java.util.LinkedHashSet<>(extra);
        for (ClusterAdvisor.Finding f : findings) urls.addAll(f.docs());
        return List.copyOf(urls.stream().limit(5).toList());
    }

    private static String truncate(String text, int max) {
        if (text == null) return "";
        String flat = text.replace('\n', ' ').strip();
        return flat.length() <= max ? flat : flat.substring(0, max) + "…";
    }
}

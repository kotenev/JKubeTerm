#!/usr/bin/env python3
"""Generate the 'K8S Best Practices in JKubeTerm' book from src/main/resources/practices.md.

Reads the wiki (## practice <id> sections) and writes one MkDocs chapter per
Kubernetes category plus an index, under docs/best-practices/. Each practice
becomes: severity badge, applies-to kinds, Why, Fix, JKubeTerm workflow box,
YAML example where relevant, docs links.

Usage: python3 tools/gen-best-practices-book.py
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
WIKI = ROOT / "src" / "main" / "resources" / "practices.md"
OUT = ROOT / "docs" / "best-practices"

KINDS = ["Pod", "Deployment", "StatefulSet", "DaemonSet", "Service", "ConfigMap",
         "Job", "CronJob", "Ingress", "PersistentVolumeClaim", "Event",
         "Node", "Namespace", "PersistentVolume"]

CHAPTERS = {
    "Pod": ("01-pod.md", "Pods", "The smallest deployable unit — and the source of most incidents."),
    "Deployment": ("02-deployment.md", "Deployments", "Stateless rollouts without downtime."),
    "StatefulSet": ("03-statefulset.md", "StatefulSets", "Stable identity plus per-Pod storage."),
    "DaemonSet": ("04-daemonset.md", "DaemonSets", "One agent per node, done right."),
    "Service": ("05-service.md", "Services", "Stable virtual IPs over disposable Pods."),
    "ConfigMap": ("06-configmap.md", "ConfigMaps", "Plain-text config without rebuilds — and without secrets."),
    "Job": ("07-job.md", "Jobs", "Run-to-completion work that must actually finish."),
    "CronJob": ("08-cronjob.md", "CronJobs", "Scheduled work that must fire, once, on time."),
    "Ingress": ("09-ingress.md", "Ingresses", "The front door: routing, TLS, and edge policy."),
    "PersistentVolumeClaim": ("10-pvc.md", "PersistentVolumeClaims", "Requests for disk that must bind and survive."),
    "Event": ("11-events.md", "Events", "The cluster news feed as a debugging instrument."),
    "Node": ("12-node.md", "Nodes", "The machines under the Pods: capacity, health, upgrades."),
    "Namespace": ("13-namespace.md", "Namespaces", "Team boundaries: quotas, policy, cost, lifecycle."),
    "PersistentVolume": ("14-pv.md", "PersistentVolumes", "Real disks with lifecycles of their own."),
}

SEV_BADGE = {"CRITICAL": ":rotating_light:", "WARN": ":warning:", "INFO": ":information_source:"}


def parse_wiki(text):
    practices = []
    parts = re.split(r"(?m)^## practice ", text)[1:]
    for part in parts:
        pid = part.split("\n", 1)[0].strip()
        body = part[len(pid):]
        def field(name):
            m = re.search(rf"(?m)^{name}:\s*(.+)$", body)
            return m.group(1).strip() if m else ""
        kinds = [k.strip() for k in field("Kinds").split(",") if k.strip()]
        severity = field("Severity").upper() or "INFO"
        title = field("Title")
        docs = re.findall(r"(?m)^- (https?://\S+)", body)
        why = re.search(r"(?ms)^### Why\n(.*?)(?=^### Fix|\Z)", body)
        fix = re.search(r"(?ms)^### Fix\n(.*)", body)
        practices.append({
            "id": pid, "kinds": kinds, "severity": severity, "title": title,
            "docs": docs,
            "why": (why.group(1).strip() if why else ""),
            "fix": (fix.group(1).strip() if fix else ""),
        })
    return practices


def anchor(pid):
    return re.sub(r"[^a-z0-9-]", "", pid.lower())


def practice_block(p):
    badge = SEV_BADGE.get(p["severity"], ":information_source:")
    lines = []
    lines.append(f"### {badge} {p['title']} {{#{anchor(p['id'])}}}")
    lines.append("")
    lines.append(f"**ID:** `{p['id']}` · **Severity:** {p['severity']} · **Applies to:** {', '.join(p['kinds'])}")
    lines.append("")
    lines.append("**Why it matters.** " + p["why"])
    lines.append("")
    lines.append("**Fix in JKubeTerm.** " + p["fix"])
    lines.append("")
    if p["docs"]:
        lines.append("**Official docs:** " + " · ".join(f"[{u.split('/')[2]}]({u})" for u in p["docs"]))
        lines.append("")
    return "\n".join(lines)


def chapter_md(kind, fname, title, subtitle, practices):
    mine = [p for p in practices if kind in p["kinds"]]
    mine.sort(key=lambda p: ({"CRITICAL": 0, "WARN": 1, "INFO": 2}[p["severity"]], p["id"]))
    lines = []
    lines.append(f"# {title} — {len(mine)} best practices")
    lines.append("")
    lines.append(f"*{subtitle}*")
    lines.append("")
    lines.append(f"> **PDF:** `{fname[:-3]}.pdf` — `tools/export-book-pdf.sh` (Chromium `--print-to-pdf`, same as guides/training).")
    lines.append("")
    lines.append("## In JKubeTerm")
    lines.append("")
    lines.append(f"Open any {title} row: the **Object** view breaks it into typed sections, **Best practices** cards "
                 "flag violations with Fix hints, and drill-down jumps to related objects. "
                 "Practices Wiki (`Help → Practices Wiki…`) holds the same entries with per-user Markdown overrides.")
    lines.append("")
    lines.append("## Practices")
    lines.append("")
    for p in mine:
        lines.append(practice_block(p))
    return "\n".join(lines)


def index_md(counts, total):
    lines = []
    lines.append("# K8S Best Practices in JKubeTerm")
    lines.append("")
    lines.append("> **PDF:** one file per chapter — `tools/export-book-pdf.sh`. Generated PDFs are git-ignored build artifacts.")
    lines.append("")
    lines.append("A hands-on book generated from the bundled practices wiki "
                 "(`src/main/resources/practices.md`, the same DB the in-app advisor reads). "
                 f"{total} practices across {len(KINDS)} Kubernetes categories, each with *why it matters*, "
                 "*fix in JKubeTerm*, and official-docs links. Goal: a maximally resilient, secure cluster operated from JKubeTerm.")
    lines.append("")
    lines.append("## How to read this book")
    lines.append("")
    lines.append("1. Work chapters in order with the [QuickStart](../guides/quickstart-minikube.md) lab open.")
    lines.append("2. For every practice: reproduce the violation, watch the JKubeTerm card appear, apply the fix, watch it turn green.")
    lines.append("3. Severity order inside each chapter: :rotating_light: CRITICAL → :warning: WARN → :information_source: INFO.")
    lines.append("")
    lines.append("## Chapters")
    lines.append("")
    lines.append("| Chapter | Practices | Focus |")
    lines.append("|---|---|---|")
    for kind in KINDS:
        fname, title, subtitle = CHAPTERS[kind]
        lines.append(f"| [{title}]({fname}) | {counts[kind]} | {subtitle} |")
    lines.append("")
    lines.append("## Failure-mode map (where outages actually come from)")
    lines.append("")
    lines.append("| If you see… | Read chapters |")
    lines.append("|---|---|")
    lines.append("| CrashLoop / ImagePullBackOff | Pods, Events |")
    lines.append("| 502s during deploys | Deployments, Services, Ingresses |")
    lines.append("| Pending Pods | Pods, Nodes, PersistentVolumeClaims |")
    lines.append("| Data loss on reschedule | StatefulSets, PersistentVolumeClaims, PersistentVolumes |")
    lines.append("| Silent cron gaps | CronJobs, Jobs, Events |")
    lines.append("| TLS expiry at midnight | Ingresses |")
    lines.append("| Namespace-wide starvation | Namespaces, Nodes |")
    lines.append("")
    return "\n".join(lines)


def main():
    text = WIKI.read_text(encoding="utf-8")
    practices = parse_wiki(text)
    if not practices:
        sys.exit("no practices parsed from " + str(WIKI))
    counts = {k: sum(1 for p in practices if k in p["kinds"]) for k in KINDS}
    OUT.mkdir(parents=True, exist_ok=True)
    for kind in KINDS:
        fname, title, subtitle = CHAPTERS[kind]
        (OUT / fname).write_text(chapter_md(kind, fname, title, subtitle, practices), encoding="utf-8")
    (OUT / "index.md").write_text(index_md(counts, len(practices)), encoding="utf-8")
    print(f"wrote {len(practices)} practices into {len(KINDS)} chapters + index")


if __name__ == "__main__":
    main()

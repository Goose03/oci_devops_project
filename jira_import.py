"""
jira_import.py
--------------
Reads jir.xml (Jira RSS/XML export) and generates INSERT statements for the
TASKS table in the Oracle Autonomous DB used by this project.

Jira displayName  →  DB app_users.id
--------------------------------------------
María Guadalupe Soto Acosta   → 46
a01741619                     → 47  (Juan Pablo Gil)
Juan Pablo Torres Guillen     → 48
Gustavo García Téllez         → 49

Project id = 39  (Oracle project, key = OC)

Sprint label → DB sprint id
--------------------------------------------
Sprint-0  → 6
Sprint-1  → 3
Sprint-2  → 4
Sprint-3  → 5

Usage
-----
  # Print SQL only (safe, no DB connection needed):
  python jira_import.py

  # Write SQL to file:
  python jira_import.py --out tasks_import.sql

  # Execute against the DB:
  python jira_import.py --execute

  # Point at a different XML file:
  python jira_import.py --file path/to/other.xml
"""

import argparse
import sys
import xml.etree.ElementTree as ET
from datetime import datetime, timezone
from email.utils import parsedate_to_datetime
from pathlib import Path

# Force UTF-8 on Windows (avoids cp1252 UnicodeEncodeError for → ñ etc.)
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

# ── Configuration ────────────────────────────────────────────────────────────

XML_FILE   = "jir.xml"
PROJECT_ID = 39

USER_MAP: dict[str, int] = {
    "María Guadalupe Soto Acosta":  46,
    "Maria Guadalupe Soto Acosta":  46,
    "mariaguadalupesotoa@gmail.com": 46,
    "a01741619":                    47,   # Juan Pablo Gil
    "Juan Pablo Torres Guillen":    48,
    "Juan Pablo Torres Guillén":    48,
    "Gustavo García Téllez":        49,
    "Gustavo Garcia Tellez":        49,
}

PRIORITY_MAP: dict[str, str] = {
    "Highest": "critical",
    "High":    "high",
    "Medium":  "medium",
    "Low":     "low",
    "Lowest":  "low",
}

STATUS_MAP: dict[str, str] = {
    "done":          "done",
    "indeterminate": "in-progress",
    "new":           "todo",
}

# Sprint label (from Jira customfield) → DB sprint_id
SPRINT_MAP: dict[str, int] = {
    "Sprint-0": 6,
    "Sprint-1": 3,
    "Sprint-2": 4,
    "Sprint-3": 5,
}

# Sprint assignment by date — closing date of each sprint (inclusive)
SPRINT_DATE_RANGES = [
    (datetime(2026, 3, 13, 23, 59, 59, tzinfo=timezone.utc), 6),   # Sprint-0
    (datetime(2026, 4, 17, 23, 59, 59, tzinfo=timezone.utc), 3),   # Sprint-1
    (datetime(2026, 4, 20, 23, 59, 59, tzinfo=timezone.utc), 4),   # Sprint-2
    (datetime(2026, 5, 22, 23, 59, 59, tzinfo=timezone.utc), 5),   # Sprint-3
]

def sprint_id_from_date(dt: datetime) -> str:
    """Return DB sprint_id based on task created_at date."""
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone.utc)
    for end_date, sprint_id in SPRINT_DATE_RANGES:
        if dt <= end_date:
            return str(sprint_id)
    return str(SPRINT_DATE_RANGES[-1][1])  # fallback to last sprint

# ── Helpers ──────────────────────────────────────────────────────────────────

def parse_rss_date(date_str: str) -> datetime:
    """Parse RFC 2822 date from Jira XML, e.g. 'Fri, 24 Apr 2026 16:36:45 -0600'."""
    if not date_str or not date_str.strip():
        return datetime.now(timezone.utc)
    try:
        return parsedate_to_datetime(date_str.strip())
    except Exception:
        try:
            return datetime.strptime(date_str.strip()[:25], "%a, %d %b %Y %H:%M:%S")
        except Exception:
            return datetime.now(timezone.utc)

def oracle_ts(dt: datetime) -> str:
    return f"TO_TIMESTAMP('{dt.strftime('%Y-%m-%d %H:%M:%S')}', 'YYYY-MM-DD HH24:MI:SS')"

def escape_sql(text: str) -> str:
    return text.replace("'", "''")

def text_of(item: ET.Element, tag: str) -> str:
    el = item.find(tag)
    return (el.text or "").strip() if el is not None else ""

def seconds_to_hours(item: ET.Element, tag: str) -> str:
    """Return integer hours from a <tag seconds="N"> element, or NULL."""
    el = item.find(tag)
    if el is None:
        return "NULL"
    sec = el.get("seconds", "")
    try:
        h = int(sec) // 3600
        return str(h) if h > 0 else "NULL"
    except (ValueError, TypeError):
        return "NULL"

# ── Core logic ────────────────────────────────────────────────────────────────

def load_items(path: str) -> list[ET.Element]:
    with open(path, encoding="utf-8") as f:
        raw = f.read()
    # Strip any leading non-XML text (browser renders "This XML file does not appear..." first)
    start = raw.find("<")
    if start > 0:
        raw = raw[start:]
    # Fix invalid XML: bare <> in content (e.g. SWE-66 summary "<>") must be escaped
    raw = raw.replace("<>", "&lt;&gt;")
    root = ET.fromstring(raw)
    channel = root.find("channel")
    if channel is None:
        raise ValueError("No <channel> found in XML — is this a Jira RSS export?")
    return channel.findall("item")

def get_sprint_id(item: ET.Element) -> str:
    """Assign sprint based on task created_at date, ignoring Jira labels."""
    created_raw = text_of(item, "created")
    if not created_raw:
        return "NULL"
    created_dt = parse_rss_date(created_raw)
    return sprint_id_from_date(created_dt)

def build_insert(item: ET.Element) -> tuple[str, list[str]]:
    warnings: list[str] = []

    jira_key = text_of(item, "key")
    summary  = escape_sql(text_of(item, "summary"))

    if not summary:
        return "", [f"{jira_key}: skipped — empty summary"]

    # ── Assignee ──────────────────────────────────────────────────
    assignee_name = text_of(item, "assignee")
    assignee_id   = USER_MAP.get(assignee_name)
    if assignee_name and assignee_id is None:
        warnings.append(f"{jira_key}: unmapped assignee '{assignee_name}' → NULL")
    assignee_sql = str(assignee_id) if assignee_id else "NULL"

    # ── Priority ───────────────────────────────────────────────────
    priority = PRIORITY_MAP.get(text_of(item, "priority"), "medium")

    # ── Status ────────────────────────────────────────────────────
    sc_el      = item.find("statusCategory")
    cat_key    = sc_el.get("key", "new") if sc_el is not None else "new"
    task_status = STATUS_MAP.get(cat_key, "todo")

    # ── Dates ─────────────────────────────────────────────────────
    created_dt  = parse_rss_date(text_of(item, "created"))
    created_sql = oracle_ts(created_dt)
    updated_sql = oracle_ts(parse_rss_date(text_of(item, "updated"))) if text_of(item, "updated") else created_sql

    completed_sql = "NULL"
    if task_status == "done":
        resolved_raw = text_of(item, "resolved")
        if resolved_raw:
            completed_sql = oracle_ts(parse_rss_date(resolved_raw))
        else:
            completed_sql = created_sql
            warnings.append(f"{jira_key}: completed_at fallback to created_at (no <resolved>)")

    # ── Hours (from Jira time-tracking) ───────────────────────────
    story_points_sql = seconds_to_hours(item, "timeoriginalestimate")
    worked_hours_sql = seconds_to_hours(item, "timespent")

    # ── Sprint ────────────────────────────────────────────────────
    sprint_id_sql = get_sprint_id(item)

    # ── Tags: Jira key for traceability ───────────────────────────
    tags_val = escape_sql(jira_key)

    sql = (
        f"INSERT INTO TASKS "
        f"(TITLE, DESCRIPTION, TASK_STATUS, PRIORITY, TASK_TYPE, "
        f"ASSIGNEE_ID, STORY_POINTS, WORKED_HOURS, SPRINT_ID, PROJECT_ID, "
        f"TAGS, CREATED_AT, UPDATED_AT, COMPLETED_AT)\n"
        f"VALUES (\n"
        f"  '{summary}',\n"
        f"  'Imported from Jira {jira_key}',\n"
        f"  '{task_status}',\n"
        f"  '{priority}',\n"
        f"  'task',\n"
        f"  {assignee_sql},\n"
        f"  {story_points_sql},\n"
        f"  {worked_hours_sql},\n"
        f"  {sprint_id_sql},\n"
        f"  {PROJECT_ID},\n"
        f"  '{tags_val}',\n"
        f"  {created_sql},\n"
        f"  {updated_sql},\n"
        f"  {completed_sql}\n"
        f");"
    )
    return sql, warnings

# ── CLI entry point ───────────────────────────────────────────────────────────

def main() -> None:
    parser = argparse.ArgumentParser(description="Import Jira XML export into TASKS table")
    parser.add_argument("--file",    default=XML_FILE, help="Path to Jira XML/RSS export")
    parser.add_argument("--execute", action="store_true",
                        help="Execute the SQL against Oracle DB (requires oracledb + wallet)")
    parser.add_argument("--out",     default=None,
                        help="Write SQL to this file instead of stdout")
    args = parser.parse_args()

    items = load_items(args.file)
    print(f"[jira_import] Loaded {len(items)} issues from {args.file}", file=sys.stderr)

    statements: list[str] = []
    all_warnings: list[str] = []

    for item in items:
        sql, warns = build_insert(item)
        all_warnings.extend(warns)
        if sql:
            statements.append(sql)

    print(f"[jira_import] {len(statements)} INSERT statements generated", file=sys.stderr)

    if all_warnings:
        print("\n── Warnings ──────────────────────────────────────", file=sys.stderr)
        for w in all_warnings:
            print(f"  ⚠  {w}", file=sys.stderr)
        print("", file=sys.stderr)

    header = (
        "-- ============================================================\n"
        "-- Jira → TASKS import (from XML)\n"
        f"-- Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n"
        f"-- Source:    {args.file}\n"
        f"-- Project:   {PROJECT_ID}\n"
        f"-- Issues:    {len(statements)}\n"
        "-- ============================================================\n\n"
    )
    full_sql = header + "\n\n".join(statements) + "\n"

    if args.out:
        Path(args.out).write_text(full_sql, encoding="utf-8")
        print(f"[jira_import] SQL written to {args.out}", file=sys.stderr)
    else:
        print(full_sql)

    if args.execute:
        _execute(statements)

def _execute(statements: list[str]) -> None:
    try:
        import oracledb
    except ImportError:
        print("[jira_import] ERROR: 'oracledb' not installed.\n  Run:  pip install oracledb", file=sys.stderr)
        sys.exit(1)

    DB_USER    = "CHATBOT_USER"
    DB_PASSWORD = input("Oracle DB password: ")
    DB_DSN     = "justtodo_high"
    WALLET_DIR = str(Path(__file__).parent / "MtdrSpring" / "backend" / "wallet")

    print(f"[jira_import] Connecting to {DB_DSN} as {DB_USER}…", file=sys.stderr)
    oracledb.init_oracle_client()

    try:
        conn = oracledb.connect(
            user=DB_USER, password=DB_PASSWORD, dsn=DB_DSN,
            config_dir=WALLET_DIR, wallet_location=WALLET_DIR, wallet_password=DB_PASSWORD,
        )
    except oracledb.DatabaseError as e:
        print(f"[jira_import] Connection failed: {e}", file=sys.stderr)
        sys.exit(1)

    cursor = conn.cursor()
    inserted = errors = 0

    for sql in statements:
        try:
            cursor.execute(sql)
            inserted += 1
        except oracledb.DatabaseError as e:
            print(f"[jira_import] Row error: {e}", file=sys.stderr)
            errors += 1

    conn.commit()
    cursor.close()
    conn.close()
    print(f"[jira_import] Done — {inserted} inserted, {errors} errors.", file=sys.stderr)

# ─────────────────────────────────────────────────────────────────────────────

if __name__ == "__main__":
    main()

"""
create_jira_ticket.py
=====================
Parses a JUnit XML test results file and opens a JIRA bug for every
failed or errored test case.

Usage:
    python utils/create_jira_ticket.py test-results.xml

Required env vars (set in OCI Build Pipeline as secrets or params):
    JIRA_TOKEN   — Atlassian API token
    JIRA_EMAIL   — Atlassian account email   (default: A00228158@tec.mx)
    JIRA_URL     — Atlassian base URL        (default: https://tec-team-kretou7k.atlassian.net)
    JIRA_PROJECT — Project key               (default: SWE)
"""

import sys
import os
import base64
import xml.etree.ElementTree as ET
from datetime import datetime, timezone

try:
    import requests
except ImportError:
    print("ERROR: 'requests' not installed. Run: pip install requests")
    sys.exit(1)

# ─── Configuration (override via env vars) ────────────────────────────────────

JIRA_URL     = os.environ.get("JIRA_URL",     "https://tec-team-kretou7k.atlassian.net")
JIRA_EMAIL   = os.environ.get("JIRA_EMAIL",   "A00228158@tec.mx")
JIRA_TOKEN   = os.environ.get("JIRA_TOKEN",   "")
JIRA_PROJECT = os.environ.get("JIRA_PROJECT", "SWE")


# ─── JIRA REST API ────────────────────────────────────────────────────────────

def _auth_header():
    credentials = base64.b64encode(f"{JIRA_EMAIL}:{JIRA_TOKEN}".encode()).decode()
    return {
        "Authorization": f"Basic {credentials}",
        "Content-Type": "application/json",
        "Accept": "application/json",
    }


def create_ticket(test_name: str, classname: str, error_message: str) -> None:
    if not JIRA_TOKEN:
        print(f"  ⚠️  JIRA_TOKEN not set — skipping ticket for '{test_name}'")
        return

    timestamp = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC")
    summary   = f"[Selenium] FAILED: {test_name}"

    payload = {
        "fields": {
            "project":     {"key": JIRA_PROJECT},
            "summary":     summary,
            "issuetype":   {"name": "Bug"},
            "description": {
                "type":    "doc",
                "version": 1,
                "content": [
                    {
                        "type": "paragraph",
                        "content": [
                            {"type": "text", "text": f"Automated Selenium test failed at {timestamp}",
                             "marks": [{"type": "strong"}]},
                        ],
                    },
                    {
                        "type": "paragraph",
                        "content": [
                            {"type": "text", "text": "Test class: "},
                            {"type": "text", "text": classname or "—",
                             "marks": [{"type": "code"}]},
                        ],
                    },
                    {
                        "type": "paragraph",
                        "content": [
                            {"type": "text", "text": "Test name: "},
                            {"type": "text", "text": test_name,
                             "marks": [{"type": "code"}]},
                        ],
                    },
                    {
                        "type": "heading",
                        "attrs": {"level": 3},
                        "content": [{"type": "text", "text": "Error"}],
                    },
                    {
                        "type": "codeBlock",
                        "content": [{"type": "text", "text": error_message[:3000]}],
                    },
                ],
            },
        }
    }

    response = requests.post(
        f"{JIRA_URL}/rest/api/3/issue",
        headers=_auth_header(),
        json=payload,
        timeout=15,
    )

    if response.status_code == 201:
        key = response.json().get("key", "?")
        print(f"  ✅ Created {key}: {summary}")
    else:
        print(f"  ❌ JIRA API error {response.status_code}: {response.text[:300]}")


# ─── XML Parser ───────────────────────────────────────────────────────────────

def parse_and_report(xml_file: str) -> int:
    try:
        tree = ET.parse(xml_file)
    except (FileNotFoundError, ET.ParseError) as exc:
        print(f"ERROR reading {xml_file}: {exc}")
        return 1

    root = tree.getroot()
    failed = 0

    for testcase in root.iter("testcase"):
        problem = testcase.find("failure") or testcase.find("error")
        if problem is None:
            continue

        test_name = testcase.get("name", "unknown_test")
        classname  = testcase.get("classname", "")
        message    = problem.get("message", "")
        body       = (problem.text or "").strip()
        full_error = f"{message}\n{body}".strip()

        print(f"\n🔴 FAILED: {classname}.{test_name}")
        create_ticket(test_name, classname, full_error)
        failed += 1

    print(f"\n{'─'*50}")
    print(f"JIRA tickets opened: {failed}")
    return failed


# ─── Entry point ──────────────────────────────────────────────────────────────

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)

    count = parse_and_report(sys.argv[1])
    sys.exit(0)  # always exit 0 — the pytest exit code already signals failure

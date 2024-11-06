#!/usr/bin/env python3

from pathlib import Path
from collections import defaultdict
import json
import csv
from pprint import pprint

LEVELS = ("class", "hyrts", "proposed")
ROOT = Path(".").resolve().parent / "run"

selected_rows = defaultdict(lambda: [0, 0, 0])
for result_file in ROOT.glob("*-cache/**/*.json"):
    project_name = result_file.parent.name[:-6]
    commit_id = result_file.name[:-5]

    selected = selected_rows[project_name]
    with open(result_file, 'r') as fh:
        result = json.load(fh)

    for idx, method_name in enumerate(LEVELS):
        tests = result[method_name]
        selected[idx] += len(tests)

pprint(selected_rows)

print("no added nor deleted")
selected_rows = defaultdict(lambda: [0, 0, 0, 0])
for result_file in ROOT.glob("*-cache/**/*.json"):
    project_name = result_file.parent.name[:-6]
    commit_id = result_file.name[:-5]

    selected = selected_rows[project_name]
    with open(result_file, 'r') as fh:
        result = json.load(fh)
    
    method_changes = result["methods"]
    if "Added" in method_changes or "Deleted" in method_changes:
        continue

    selected[3] += 1
    for idx, method_name in enumerate(LEVELS):
        tests = result[method_name]
        selected[idx] += len(tests)

pprint(selected_rows)

print("added or deleted")
selected_rows = defaultdict(lambda: [0, 0, 0, 0])
for result_file in ROOT.glob("*-cache/**/*.json"):
    project_name = result_file.parent.name[:-6]
    commit_id = result_file.name[:-5]

    selected = selected_rows[project_name]
    with open(result_file, 'r') as fh:
        result = json.load(fh)

    method_changes = result["methods"]
    if not ("Added" in method_changes or "Deleted" in method_changes):
        continue

    selected[3] += 1
    for idx, method_name in enumerate(LEVELS):
        tests = result[method_name]
        selected[idx] += len(tests)

pprint(selected_rows)

time_rows = {}
for dir in ROOT.iterdir():
    name = dir.name

    time_table = dir / "result.csv"
    if not time_table.exists():
        continue

    all_time = {key: list() for key in LEVELS}
    all_analyze = {key: list() for key in LEVELS}
    content = time_table.read_text()

    first = None
    for row in csv.reader(content.splitlines()):
        if not row:
            continue

        commit_id, level, t_all, t_analyze = row
        if not first:
            first = commit_id

        all_time[level].append(float(t_all))
        all_analyze[level].append(float(t_analyze))

    x = time_rows[name] = {
        "total": len(all_time[level]),
        "head": first[:7]
    }
    for level in LEVELS:
        x[level] = (name, sum(all_time[level]) / len(all_time[level]), sum(all_analyze[level]))

pprint(time_rows)
#!/usr/bin/env python3

import shelve
import json
import subprocess
import sys
from typing import Callable
import traceback
from strategy import Strategy
from util import run, run_tool
from repository import Repository, clone, parse_head_id, next_changed_commit, ChangedFileSet, parse_commit_id, EmptyClassDirException
from surefire import SurefireConnector
import logging

LOGGER = logging.getLogger(__name__)

class Unit:
    def __init__(self, repo: Repository, commit_id):
        self.repo = repo
        self.commit_id = parse_commit_id(repo, commit_id)
        self.test_connector = None
        self.compiled_classes_dir = None

    def generate_class_files(self):
        self.repo.checkout(self.commit_id)
        self.compiled_classes_dir = self.repo.compile()

    def generate_test_runner(self):
        self.repo.checkout(self.commit_id)
        self.test_connector = SurefireConnector(self.repo.root)

def main(url):
    repo_name = url.split('/')[-1]
    result_file, cache_root, first, second = clone(repo_name, url)

    strategies = [Strategy(repo_name, type) for type in ("proposed", "hyrts", "class")]
    change_commits = 0
    next_head_id = 'HEAD'
    head = None
    with shelve.open(cache_root / f"change_map") as visited:
        while True:
            head = Unit(first, next_head_id)
            next_head_id = f'{head.commit_id}~'
            if head.commit_id in visited:
                result = visited[head.commit_id]
                if result == "no_changes":
                    print("No change")
                    continue
                elif result == "build_error":
                    print("Build error")
                    continue
                elif result == "test_error":
                    print("Test error")
                    continue
                elif not (cache_root / (head.commit_id + ".json")).exists():
                    continue

            print(f"Latest commit: {head.commit_id}")
            head.generate_test_runner()
            break

    print("Head:", head.commit_id)

    for method in strategies:
        for count in range(30):
            print(f"{method.method_type}: Round {count}")
            method.prepare(head.test_connector)

    with open(result_file.parent / "runtime.csv", "w") as fh:
        for method in strategies:
            fh.write(",".join([method.method_type, *map(str, method.result_groups())]))
            fh.write("\n")

try:    
    import logging
    logging.basicConfig(format='%(asctime)s %(message)s', level=logging.INFO)
    # run(".", "mvn", "install")
    main(sys.argv[1])
except subprocess.CalledProcessError as e:
    print(e.stdout)
    print(e.stderr)
    print(traceback.format_exc())
    
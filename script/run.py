#!/usr/bin/env python3

from abc import ABCMeta
import json
import subprocess
import sys
from lib.strategy import Strategy
from lib.util import FailureCounter, run, run_tool
from lib.repository import FileSet, Repository, clone
import logging

from script.lib.surefire import SurefireConnector

LOGGER = logging.getLogger(__name__)


class Benchmark:
    pass


class Node(ABCMeta):
    def __init__(self, repo: Repository, connector=None):
        self.repo = repo
        self.connector = connector

    def resolve(self, commit_id=None):
        if not self.connector:
            repo = self.repo
            compile = FailureCounter(repo.compile, False)
            while True:
                files = repo.resolve()
                if self._is_target_files(files) and compile():
                    try:
                        self.connector = repo.create_connector()
                    except Exception:
                        print("Could not create repository")
        
        return self.connector

    def has_difference(self, another: 'Node'):
        proc = run_tool("compare", ":".join(self.repo.classpaths), ":".join(another.repo.classpaths))
        data = proc.json()
        return data["classes"] or data["methods"]


class Head(Node):
    def __init__(self, repo, connector=None):
        super().__init__(repo, connector)

    def _is_target_files(self, files: FileSet):
        return files.has_java_only

class Parent(Node):
    def __init__(self, repo: Repository):
        super().__init__(repo)

    def _is_target_files(self, files: FileSet):
        return files.has_changes


def main(url):
    repo_name = url.split('/')[-1]
    
    result_file, cache_root, head, parent = clone(repo_name, url)
    head = Head(head)
    parent = Parent(parent)
    head_cache = None

    found = 0
    warnings = set()

    
    while found < 30:
        head_connector = head.resolve()
        parent_connector = parent.resolve(head.repo.commit_id)

        METHODS = ("hyrts", "class", "proposed")
print("Head:", head.commit_id)
        print("Parent:", parent.commit_id)

        try:
            base_connector = SurefireConnector(parent.root)
        except Exception:
            print("Skip this parent commit: test failed")
            continue

        try:
            with count_failure():
                head_connector = head.create_connector()
        except Exception:
            print("Skip this head: test failed")
            next(files)
            continue

        methods = [Strategy(type) for type in self.METHODS]
        for method in methods:
            print()
            print(method.method_type)
            run_all_time = method.prepare(base_connector)
            analysis_time = method.analyze(head, current, head_connector.rundir.root)
            execution_time = method.run(head_connector)
            collection_time = method.collect(head_connector)
            print(f"{method.method_type}: {run_all_time}, {analysis_time}, {execution_time}, {collection_time}")
            changes[method.method_type] = list(method.selected)

        with open(result_file, "a") as fh:
            for method in methods:
                fh.write(",".join([head.commit_id, method.method_type, len(method.selected), *map(str, method.result_groups())]))
                fh.write("\n")

        changes["parent"] = current.commit_id
        changes["head"] = head.commit_id
        changes["warnings"] = list(warnings)
        head_cache.write_text(json.dumps(data, indent=4))

        found += 1
        print(f"Found {found}\n")
        
        if can_be_reuse:
            return Head(parent, head)
        else:
            return UnknownHead(head, parent)

try:
    run(".", "mvn", "package")
    main(sys.argv[1])
except subprocess.CalledProcessError as e:
    print(e.stdout)
    print(e.stderr)
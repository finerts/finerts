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

    def is_finished():
        nonlocal change_commits
        return change_commits >= 20

    def has_lc(data):
        print(f"Changes: {data["methods"]}")
        return "Added" in data["methods"] or "Deleted" in data["methods"]

    def has_changes(data):
        # return has_lc(data)
        return data["classes"] or data["methods"]

    def dump_error(commit_id, reason, e: subprocess.CalledProcessError):
        nonlocal cache_root
        out = cache_root / (commit_id + f"_{reason}_stdout.txt")
        out.write_text(e.stdout)

        out = cache_root / (commit_id + f"_{reason}_stderr.txt")
        out.write_text(e.stderr)

    def dump_build_error(commit_id, e: subprocess.CalledProcessError):
        dump_error(commit_id, "build", e)

    strategies = [Strategy(repo_name, type) for type in ("proposed", "hyrts", "class")]
    change_commits = 0
    next_head_id = 'HEAD'
    with shelve.open(cache_root / f"change_map") as visited:
        def update_count(data):
            nonlocal change_commits, head
            changed = False
            method_level = False
            if has_changes(data):
                change_commits += 1
                changed = True
            if has_lc(data):
                method_level = True

            visited[head.commit_id] = (method_level, changed)

            return is_finished()

        while not is_finished():
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
                    continue # retry
                else:
                    method, normal = result
                    change_commits += normal

                    print((change_commits))
                    if (cache_root / (head.commit_id + ".json")).exists():
                        continue

                    print(f"Replay: {head.commit_id}")

            def skip_commit(reason, skip_type):
                nonlocal head, visited
                visited[head.commit_id] = skip_type
                print(f"Skip commit {head.commit_id}: {reason}")

            ###################################
            # Check the changes in Java files at the head commit
            ###################################
            head_changes = ChangedFileSet(head.repo, head.commit_id)
            if not head_changes.has_java_changes:
                skip_commit("no Java files are changed", "no_changes")
                continue

            ###################################
            # Generate the classes at the head and base commits
            # Note: The commit is checked out at this step.
            ###################################
            try:
                head.generate_class_files()
            except subprocess.CalledProcessError as e:
                dump_error(head.commit_id, "compile", e)
                skip_commit("build failed in the target commit", "build_error")
                continue

            base = Unit(second, next_head_id)
            try:
                base.generate_class_files()
            except subprocess.CalledProcessError as e:
                dump_error(base.commit_id, "compile", e)
                skip_commit("build failed in the base commit", "build_error")
                next_head_id = f'{next_head_id}~'
                continue

            ###################################
            # Ensure binary-level changes on the class files
            ###################################
            proc = run_tool("compare", ":".join(head.compiled_classes_dir), ":".join(base.compiled_classes_dir))
            stdout = proc.stdout
            data = json.loads(stdout)
            if not has_changes(data):
                skip_commit("detects no binary-level changes", "no_changes")
                continue

            ###################################
            # Ensure the test causes no error
            ###################################
            try:
                head.generate_test_runner()
            except subprocess.CalledProcessError as e:
                dump_build_error(head.commit_id, e)
                print(e.stdout[-3000:])
                print(e.stderr[-3000:])
                traceback.print_exc()
                skip_commit("test failed in the target commit", "test_error")
                continue

            try:
                base.generate_test_runner()
            except subprocess.CalledProcessError as e:
                dump_build_error(base.commit_id, e)
                print(e.stdout[-3000:])
                print(e.stderr[-3000:])
                traceback.print_exc()
                skip_commit("test failed in the base commit", "test_error")
                next_head_id = f'{next_head_id}~'
                continue

            print("Head:", head.commit_id)
            print("Source:", base.commit_id)
            for method in strategies:
                method.reset()
                logging.info(f"{method.method_type}: prepare base coverages")
                run_all_time = method.prepare(base.test_connector)

                logging.info(f"{method.method_type}: select tests")
                analysis_time = method.analyze(head, base, head.test_connector.runtime_root)

                print(f"{method.method_type}: {run_all_time}, {analysis_time}, #{len(method.selected)}")
                data[method.method_type] = list(method.selected)

            with open(result_file, "a") as fh:
                for method in strategies:
                    fh.write(",".join([head.commit_id, method.method_type, *map(str, method.result_groups())]))
                    fh.write("\n")

            data["parent"] = base.commit_id
            data["head"] = head.commit_id

            for method in strategies:
                data[method.method_type] = list(method.selected)

            data["warnings"] = []
            (cache_root / (head.commit_id + ".json")).write_text(json.dumps(data, indent=4))

            if update_count(data):
                break

try:    
    import logging
    logging.basicConfig(format='%(asctime)s %(message)s', level=logging.INFO)
    # run(".", "mvn", "install")
    main(sys.argv[1])
except subprocess.CalledProcessError as e:
    print(e.stdout)
    print(e.stderr)
    print(traceback.format_exc())
    
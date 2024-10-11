from subprocess import CalledProcessError
from util import run
import re
from pathlib import Path
import json

SOURCE_REGEX = re.compile(r"^\[INFO\] Compiling \d+ source files to (.+)$", re.DOTALL)
POTENTIAL_LIST = {
    "pom.xml"
}

class Repository:
    def __init__(self, root):
        self.root = root

    def next(self, commit_id=None):
        if commit_id:
            head, count = commit_id, 1
        else:
            head, count = "HEAD", 0

        while True:
            try:
                proc = run(self.root, "git", "diff-tree", f"{head}~{count}", "--name-only", "-r", "--")
            except CalledProcessError:
                raise StopIteration

            try:
                commit_id, *files = proc.lines()
                files = FileSet(files)
            except ValueError:
                count += 1
                continue

            try:
                proc = run(self.root, "mvn", "test-compile", "-B")
            except CalledProcessError:
                pass
            else:
                cp = map(SOURCE_REGEX.match, proc.lines())
                cp = list(filter(None, cp))

                return commit_id, cp, files


class FileSet:
    def __init__(self, files):
        if not files:
            raise ValueError

        self.files = set(files)
        self.java_files = set(line for line in files if line.endswith(".java"))
        self.potential_files = POTENTIAL_LIST & self.files

    @property
    def has_java_only(self):
        return self.java_files and not self.potential_files

    @property
    def warnings(self):
        return self.files - self.java_files - self.potential_files

    @property
    def has_changes(self):
        return self.files


def clone(name, url):
    root = Path("..")
    if not (root / name).is_dir():
        run(root, "git", "clone", "--bare", url, name)

    root = root / name
    changes = root / "changes"
    changes.mkdir(exist_ok=True)

    result_file = root / "result.csv"
    
    branches = set(s[2:] for s in run(root, "git", "branch", "-a").stdout.splitlines())
    for branch in branches:
        if any(branch.endswith(name) for name in {"master", "main", "trunk"}):
            break

    commit_id = run(root, "git", "rev-list", branch, "-n1").stdout
    commit_id = find_head(changes, commit_id)

    first = add_worktree(root, "first", commit_id)
    second = add_worktree(root, "second", commit_id)
    return [result_file, changes, Repository(first), Repository(second)]

def add_worktree(root, name, branch):
    ret = root / name
    if ret.is_dir():
        run(ret, "git", "checkout", "-f", branch)
        run(ret, "git", "clean", "-f")
    else:
        run(root, "git", "worktree", "add", name, branch)

    return ret

def find_head(cache_root, head):
    try:
        file = next(Path(cache_root).iterdir())
    except StopIteration:
        return head

    commit_id = head
    while True:
        try:
            data = json.loads(file.read_text())
        except FileNotFoundError:
            return commit_id
        else:
            commit_id = data["parent"]
            file = cache_root / (commit_id + ".json")
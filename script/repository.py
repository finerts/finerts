from util import run
import re
from surefire import SurefireConnector
from pathlib import Path
import shutil

BUILD_TARGET_DIR_REGEX = re.compile(r"^\[INFO\] Building (.+)$", re.DOTALL)
SOURCE_REGEX = re.compile(r"^\[INFO\] Compiling \d+ source files (.+)$", re.DOTALL)

class EmptyClassDirException(Exception):
    pass

class Repository:
    def __init__(self, root):
        self.root = root
        self.classpaths = None
        self.connector = None

    def checkout(self, commit_id=None):
        if commit_id is None:
            commit_id = self.commit_id
        else:
            self.commit_id = commit_id

        run(self.root, "git", "checkout", commit_id, "-f")
        self.head = "HEAD"
        self.count = 1
        self.connector = None

    def compile(self):
        proc = run(self.root, "mvn", "clean", "package", "-DskipTests")
        classpaths = []
        target_root = None
        next_is_pom = False
        for line in proc.stdout.splitlines():
            if m := BUILD_TARGET_DIR_REGEX.match(line):
                next_is_pom = True
            elif next_is_pom:
                next_is_pom = False
                target_pom = Path(self.root) / line.split()[-1]
                target_root = target_pom.parent 
            elif m := SOURCE_REGEX.match(line):
                words = m.group(1).split()
                if words[-2] != 'to':
                    raise EmptyClassDirException

                dir = Path(words[-1])
                if not dir.is_absolute():
                    dir = target_root / dir

                if not dir.exists():
                    print(dir)
                    raise EmptyClassDirException

                classpaths.append(str(dir.resolve()))

        if not classpaths:
            raise EmptyClassDirException

        return classpaths

    def create_connector(self) -> SurefireConnector:
        if not self.connector:
            self.connector = SurefireConnector(self.root)

        return self.connector

    @staticmethod
    def iterate_surefire_property(content):
        for line in content.splitlines():
            if line[0] == "#":
                continue

            key, value = line.split("=", 1)
            names = key.split(".")
            if len(names) == 1:
                yield key, None, value
            else:
                yield names[0], int(names[1]), value


class ChangedFileSet:
    def __init__(self, repo, commit_id):
        proc = run(repo.root, "git", "diff-tree", commit_id, "--name-only", "-r", "--")
        stdout = proc.stdout
        if stdout:
            files = stdout.splitlines()
            files.pop(0)
            self.files = set(files)
        else:
            self.files = set()

        self.java_files = set(line for line in self.files if line.endswith(".java"))

    @property
    def has_java_only(self):
        return len(self.java_files) == len(self.files)

    @property
    def has_java_changes(self):
        return self.java_files

    @property
    def warnings(self):
        return self.files - self.java_files

    @property
    def has_changes(self):
        return self.files


def parse_commit_id(repo: Repository, name):
    proc = run(repo.root, "git", "rev-parse", name)
    return proc.stdout.strip()

def parse_head_id(repo: Repository):
    return parse_commit_id('HEAD')

def next_changed_commit(repo: Repository, head = 'HEAD'):
    count = 0
    while True:
        proc = run(repo.root, "git", "diff-tree", f"{head}~{count}", "--name-only", "-r", "--")
        stdout = proc.stdout
        if not stdout:
            count += 1
            continue
        
        lines = stdout.splitlines()
        commit_id = lines.pop(0) 
        return commit_id, FileSet(lines)


def clone(name, url):
    root = Path("..") / "run"
    root.mkdir(exist_ok=True)
    if not (root / name).is_dir():
        run(root, "git", "clone", "--bare", url, name)

    cache = root / f"{name}-cache"
    cache.mkdir(exist_ok=True)

    root = root / name
    result_file = root / "result.csv"
    
    branches = run(root, "git", "branch", "-a", "--format=%(refname:short)", "--sort=-committerdate").stdout.splitlines()
    branch = next(filter(lambda name: name in {"dev", "main", "trunk", "master"}, branches))

    if "finerts-follow" not in branches:
        run(root, "git", "branch", "finerts-follow", branch)

    print("Main:", branch)

    first = add_worktree(root, "first", branch)
    second = add_worktree(root, "second", "finerts-follow")
    return [result_file, cache, Repository(first), Repository(second)]

def add_worktree(root, name, branch):
    ret = root / name
    if ret.is_dir():
        run(ret, "git", "checkout", "-f", branch)
        run(ret, "git", "clean", "-f")
        run(ret, "mvn", "clean")
    else:
        run(root, "git", "worktree", "add", name, branch)

    return ret
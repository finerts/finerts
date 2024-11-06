from pathlib import Path
from util import run_tool
from repository import Repository

from surefire import SurefireConnector

class Strategy:
    def __init__(self, repo_name, method_type):
        self.repo_name = repo_name
        self.method_type = method_type
        self.times = []
        self.selected = []

    def prepare(self, connector: SurefireConnector):
        t = connector.run(self.method_type)
        self.times.append(t)
        return t

    def analyze(self, head: Repository, base: Repository, runtime_root: Path):
        ret = run_tool("select", self.method_type, ":".join(head.compiled_classes_dir), ":".join(base.compiled_classes_dir), str(runtime_root.resolve()))
        self.selected = set(ret.stdout.splitlines())
        self.times.append(ret.elapsed)
        return ret.elapsed

    def reset(self):
        self.times = []
        self.selected = []

    def run(self, head_connector: SurefireConnector):
        if self.selected:
            t = head_connector.run(self.method_type, selected=self.selected, disable_agent=True)
        else:
            t = 0

        self.times.append(t)
        return t

    def collect(self, head_connector: SurefireConnector):
        avg = []
        if self.selected:
            for _ in range(3):
                avg.append(head_connector.run(self.method_type, self.selected, dryrun=True))

            t = sum(avg) / len(avg)
        else:
            t = 0

        self.times.append(t)
        return t

    def result_groups(self):
        return self.times

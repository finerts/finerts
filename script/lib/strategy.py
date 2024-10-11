from pathlib import Path
from util import run_tool
from repository import Repository

from surefire import SurefireConnector

class Strategy:
    def __init__(self, method_type):
        self.method_type = method_type

    def prepare(self, base_connector: SurefireConnector):
        t = base_connector.run(self.method_type)
        self.times = [t]
        self.selected = []
        return t

    def analyze(self, head: Repository, base: Repository, test_file: Path):
        ret = run_tool("select", self.method_type, ":".join(head.classpaths), ":".join(base.classpaths), str(test_file.resolve()))
        self.selected = set(ret.stdout.splitlines())
        self.selected |= head.connector.tests - base.connector.tests
        print(self.selected)
        self.times.append(ret.time)
        return ret.time

    def run(self, head_connector: SurefireConnector):
        t = head_connector.run(None, self.selected)
        self.times.append(t)
        return t

    def collect(self, head_connector: SurefireConnector):
        t = head_connector.run(self.method_type, self.selected, dryrun=True)
        self.times.append(t)
        return t

    def result_groups(self):
        return self.times

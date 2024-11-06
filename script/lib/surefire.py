import base64
from contextlib import contextmanager
import itertools
import shutil
import subprocess
import re
import tempfile
from pathlib import Path
from time import time
from util import run, run_tool

SUREFIRE_VERSION_REGEX = re.compile("maven-surefire-plugin:(\d+)\.([M\d\.\-]+)")
FORK_SCRIPT_REGEX = re.compile(r"Forking command line: /bin/sh -c cd ([^&]+) && (.+)$", re.DOTALL)
AGENT_RT_JAR = Path("lib", "agent-rt.jar").resolve()
EMPTY_AGENT_RT_JAR = Path("lib", "empty-agent-rt.jar").resolve()
JUNIT_VERSION_REGEX = re.compile(r"junit/junit/([\d\.]+)")
MANIFEST = "META-INF/MANIFEST.MF"
JUNIT4_LISTENER_NAME = "kr.ac.korea.rts.JUnit4Listener"
JUNIT5_VERSION_REGEX = re.compile(r"org/junit/platform/junit-platform-launcher/([\d\.]+)")

class DefaultSurefireController:
    BYE_FLAG = "Z"
    BYE_ACK = bytes([0, 0, 0, 5, 0, 0, 0, 0]).decode("utf8")
    SKIP = {"1", "2", "5", "6", "9", "G", "I"}
    ERROR = {"7", "8", "N", "S", "X", "3", "4", "H"}
    PRINT = {"H", "D", "W"}

    def __init__(self, major, minor):
        print(major, minor)
        if major == '2':
            minor = minor.split(".")[0]
            if int(minor) < 18:
                raise Exception()

        self.warnings = set()

    def parse(self, proc):
        error = False
        for line in proc.stdout:
            try:
                flag, data = line.strip().split(",", 1)
            except ValueError:
                error = True
                print(line)
                continue

            if flag == self.BYE_FLAG:
                break
            if flag in self.SKIP:
                continue
            if flag in self.ERROR:
                error = True
            elif flag not in self.PRINT:
                error = True

            print(data)

        try:
            proc.communicate(self.BYE_ACK, timeout=2)
        except subprocess.TimeoutExpired:
            print(f"Test timeout with the last line: {line}")

        return error

class LatestSurefireController:
    SKIP = { "std-err-stream-new-line", "sys-prop", "test-skipped", "testset-completed", "testset-starting", "test-starting", "test-succeeded", "test-starting" }
    PRINT = {}
    ERROR = { "test-failed", "test-error", "console-error-log" }
    BYE_ACK = ":maven-surefire-command:bye-ack:"
    BYE_COMMAND = "bye"

    def __init__(self):
        self.warnings = set()

    def parse(self, proc):
        error = False
        for line in proc.stdout:
            try:
                _, magic, command, data = line.strip().split(":", 3)
            except ValueError:
                error = True
                print(line)
                continue

            if command == self.BYE_COMMAND:
                break
            if command in self.SKIP:
                continue
            if command in self.ERROR:
                error = True
            elif command not in self.PRINT:
                if command not in self.warnings:
                    self.warnings.add(command)
                    print("UNKNOWN", line)
                continue

            mode, encoding, *data, _ = data.split(":")
            print(mode, encoding, data)
            consoles = map(lambda x: str(base64.b64decode(x).strip()), filter(lambda x: x != '-', data))
            print(" ".join(consoles))

        try:
            proc.communicate(self.BYE_ACK, timeout=2)
        except subprocess.TimeoutExpired:
            print(f"Test timeout with the last line: {line}")

        return error




class SurefireConnector:
    def __init__(self, root):
        proc = run(root, "mvn", "test", "-Dmvn.main.skip=true", "-B", "-X", "-Djacoco.skip=true", "-DforkCount=1")
        for line in proc.stdout.splitlines():
            if m := SUREFIRE_VERSION_REGEX.search(line):
                major, minor = m.groups()
                if minor == "0.0-M4" or minor == "0.0-M5":
                    self.controller = LatestSurefireController()
                else:
                    self.controller = DefaultSurefireController(major, minor)
            elif m := FORK_SCRIPT_REGEX.search(line):
                cwd, command = m.groups()
                params = command.split(" ")
                jar = params[-5]

                with manifest(jar) as (classpath, update):
                    listener = prepare_junit_listener(classpath)
                    update(listener)

                self.property = SurefireProperty(params)
                classes, tests, libs = self.property.init(listener)
                self.rundir = SurefireRunDir(root, classes, tests, libs)

                params.insert(2, "")
                self.command = params
                self.cwd = cwd
                self.tests = tests
                self.classes = classes
                break

    def run(self, method_type, tests=None, dryrun=False):
        self.property.write_testlist(tests if tests else self.tests)

        command = self.command
        if method_type is None:
            command[2] = f"-javaagent:{EMPTY_AGENT_RT_JAR}"
        else:
            runtime_root = self.rundir.root.resolve()
            runtime_root.mkdir(exist_ok=True)
            command[2] = f"-javaagent:{AGENT_RT_JAR}={method_type},{runtime_root},{str(dryrun).lower()}"

        print(" ".join(command))
        start = time()
        proc = subprocess.Popen(command, stdout=subprocess.PIPE, stdin=subprocess.PIPE, stderr=subprocess.STDOUT, cwd=self.cwd, universal_newlines=True)
        error = self.controller.parse(proc)
        done = time() - start
        if error:
            raise Exception()

        return done    


class SurefireRunDir:
    def __init__(self, root, classes, tests, libs):
        root = self.root = root.parent / "runtime"
        shutil.rmtree(str(root), ignore_errors=True)
        self.root.mkdir()

        run_tool("signature", str(root / "signature.set"), *map(str, libs))
        self.classes_file = Path(root, "classes.lst")
        self.classes_file.write_text("\n".join(classes))

        self.tests_file = Path(root, "tests.lst")
        self.tests_file.write_text("\n".join(tests))

class SurefireProperty:
    def __init__(self, params):
        file = Path(params[-2])
        if not file.exists():
            file = Path(params[-4], params[-2])
            if not file.exists():
                raise Exception(params)

        self.file = file
        self.constants = [f"listener={JUNIT4_LISTENER_NAME}"]

    def init(self, listener):
        constants = self.constants
        tests = set()
        classes = set()
        libs = set()
        num = -1
        
        for line in self.file.read_text().splitlines():
            if line.startswith("tc."):
                tests.add(line.split("=", 1)[1])
            else:
                constants.append(line)
                if line.startswith("classPathUrl."):
                    num += 1
                    value = Path(line.split("=", 1)[1])
                    if value.is_dir():
                        for item in Path(value).rglob("*.class"):
                            classes.add(str(item.relative_to(value))[:-6])
                    else:
                        libs.add(value)

        constants.append(f"classPathUrl.{num + 1}={listener}")

        return classes, tests, libs

    def write_testlist(self, tests):
        content = "\n".join([*self.constants, *map(lambda p: f"tc.{p[0]}={p[1]}", enumerate(tests))])
        self.file.write_text(content)
    

def prepare_junit_listener(classpath):
    junit4 = "4.13.2"
    junit5 = "1.8.2"

    if m := JUNIT_VERSION_REGEX.search(classpath):
        junit4 = m.group(1)
    if m := JUNIT5_VERSION_REGEX.search(classpath):
        junit5 = m.group(1)
                
    listener = Path("lib", f"junit-listener-{junit4}-{junit5}.jar").resolve()
    if not listener.exists():
        run(".", "mvn", "-pl", "junit-listener", "install", f"-Djunit4.version={junit4}", f"-Djunit5.version={junit5}")

    if not listener.exists():
        raise Exception(listener)

    return listener


@contextmanager
def manifest(jar):
    with tempfile.TemporaryDirectory() as tmpdir:
        manifest = Path(tmpdir, MANIFEST)

        def _read():
            run(tmpdir, "jar", "xf", jar, MANIFEST)
            lines = manifest.read_text().splitlines()
            lines = itertools.dropwhile(lambda line: not line.startswith("Class-Path: "), lines)
            lines = [next(lines), itertools.takewhile(lambda x: x[0] == ' ', lines)]
            lines = map(lambda x: x.lstrip(), lines)
            return "".join(lines)

        def _update(listener):
            updated = " ".join([content, str(listener)])
            wrapped = [updated[:72], *(" " + updated[i:71 + i] for i in range(72, len(updated), 71))]
            wrapped = "\n".join(wrapped)
            manifest.write_text(wrapped + "\n")

            run(tmpdir, "jar", "ufm", jar, MANIFEST)

            written = _read()
            if written != updated:
                print(updated)
                print(written)
                raise Exception()

        content = _read()

        yield content, _update
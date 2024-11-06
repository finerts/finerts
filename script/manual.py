#!/usr/bin/env python3

from contextlib import contextmanager
import itertools
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile

from surefire import LatestSurefireController

SUREFIRE_VERSION_REGEX = re.compile("maven-surefire-plugin:(\d+)\.([M\d\.\-]+)")
FORK_SCRIPT_REGEX = re.compile(r"Forking command line: /bin/sh -c cd ([^&]+) && (.+)$", re.DOTALL)
JUNIT_VERSION_REGEX = re.compile(r"junit/junit/([\d\.]+)")
JUNIT4_LISTENER_NAME = "kr.ac.korea.rts.JUnit4Listener"
JUNIT5_VERSION_REGEX = re.compile(r"org/junit/platform/junit-platform-launcher/([\d\.]+)")
MANIFEST = "META-INF/MANIFEST.MF"
EXP_ROOT = str(Path(__file__).parent.parent)
AGENT_RT_JAR = Path(EXP_ROOT, "lib", "agent-rt.jar").resolve()
EMPTY_AGENT_RT_JAR = Path(EXP_ROOT, "lib", "empty-agent-rt.jar").resolve()

def prepare_junit_listener(classpath):
    junit4 = "4.13.2"
    junit5 = "1.8.2"

    if m := JUNIT_VERSION_REGEX.search(classpath):
        junit4 = m.group(1)
    if m := JUNIT5_VERSION_REGEX.search(classpath):
        junit5 = m.group(1)
                
    listener = Path(EXP_ROOT, "lib", f"junit-listener-{junit4}-{junit5}.jar").resolve()
    run(EXP_ROOT, "mvn", "-pl", "junit-listener", "install", f"-Djunit4.version={junit4}", f"-Djunit5.version={junit5}").wait();

    if not listener.exists():
        raise Exception(listener)

    return listener

def run(dir, *commands):
    print(" ".join(commands))
    env = os.environ.copy()
    # env["AGENT_TRACE"] = "FINE"
    proc = subprocess.Popen(commands, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, cwd=str(dir), env=env, text=True)
    return proc

@contextmanager
def manifest(jar):
    with tempfile.TemporaryDirectory() as tmpdir:
        manifest = Path(tmpdir, MANIFEST)

        def _read():
            run(tmpdir, "jar", "xf", jar, MANIFEST).wait()
            lines = manifest.read_text().splitlines()
            lines = itertools.dropwhile(lambda line: not line.startswith("Class-Path: "), lines)
            header = next(lines)
            lines = [header, *map(lambda x: x[1:], itertools.takewhile(lambda x: x[0] == ' ', lines))]
            return "".join(lines)

        def _update(listener):
            updated = " ".join([content, str(listener)])
            wrapped = [updated[:72], *(" " + updated[i:71 + i] for i in range(72, len(updated), 71))]
            wrapped = "\n".join(wrapped)
            manifest.write_text(wrapped + "\n")

            run(tmpdir, "jar", "ufm", jar, MANIFEST).wait()

            written = _read()
            if written != updated:
                print(updated)
                print(written)
                raise Exception()

        content = _read()

        yield content, _update


params = sys.argv[1].split(" ")
jar = params[-5]
cwd = Path(params[-4]).parent.parent
root = str(cwd.parent / "runtime")

with manifest(jar) as (classpath, update):
    listener = prepare_junit_listener(classpath)
    update(listener)

for idx in range(len(params)):
    if params[idx].startswith("-Xmx"):
        params[idx] = "-Xmx2G"

params.insert(2, f"-javaagent:{AGENT_RT_JAR}={sys.argv[2]},{root},false")

controller = LatestSurefireController()
controller.parse(run(str(cwd), *params))

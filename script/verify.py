#!/usr/bin/env python3

import pathlib
import shutil
import subprocess
import sys
import base64
import os
import shutil
from surefire import SurefireConnector

env = os.environ.copy()
# env['AGENT_TRACE'] = 'FINE'
fork_cwd = pathlib.Path(sys.argv[-4])
cwd = fork_cwd.parent.parent
shutil.copy(str(fork_cwd / "classes.lst"), str(cwd / "classes.lst"))
shutil.copy(str(fork_cwd / "tests.lst"), str(cwd / "tests.lst"))
try:
    proc = subprocess.Popen(sys.argv[1:], cwd=str(cwd), stdout=subprocess.PIPE, stdin=subprocess.PIPE, stderr=subprocess.STDOUT, universal_newlines=True, env=env, errors="replace")
    warnings = set()
    for line in proc.stdout:
        try:
            _, magic, command, data = line.strip().split(":", 3)
        except ValueError:
            print(line)
            continue

        if command == "bye":
            break
        if command in SurefireConnector.SKIP_EVENT:
            continue
        if command in SurefireConnector.ERROR_EVENT:
            error = True
        elif command not in SurefireConnector.PRINT_EVENT:
            if command not in warnings:
                warnings.add(command)
                print("UNKNOWN", line)
            continue

        mode, encoding, *data, _ = data.split(":")
        consoles = map(lambda x: "  " + base64.b64decode(x).strip().decode("utf8"), filter(lambda x: x != '-', data))
        print(command, "\n", "\n".join(consoles))

    try:
        proc.communicate(":maven-surefire-command:bye-ack:", timeout=2)
    except subprocess.TimeoutExpired:
        print(f"Test timeout with the last line: {line}")
finally:
    shutil.rmtree("verify", ignore_errors=True)
    pathlib.Path("verify").mkdir()

    shutil.move(str(cwd / "classes.lst"), "verify/classes.lst")
    shutil.move(str(cwd / "tests.lst"), "verify/tests.lst")
    shutil.move((cwd / "agent.log"), "verify/agent.log")
    shutil.move((cwd / "agent"), "verify/cov")

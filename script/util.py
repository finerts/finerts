import subprocess
from time import time
from collections import namedtuple
import os
import logging

sub_env = os.environ.copy()
sub_env['JAVA_HOME'] = "/usr/lib/jvm/openjdk-bin-11"
sub_env['MAVEN_ARGS'] = "-B -Daether.dependencyCollector.impl=bf -Dmaven.gitcommitid.skip=true -Djacoco.skip=true -Dmaven.gitcommitid.skip=true -Dlicense.skip"
sub_env['PATH'] = ':'.join(['/home/mingwan/apache-maven-3.9.6/bin', sub_env['PATH']])


RunResult = namedtuple("RunResult", ["stdout", "stderr", "code", "elapsed"])

def run(dir, *commands):
    logging.info(str(dir) + "> " + " ".join(commands))
    start = time()
    proc = subprocess.run(commands, text=True, capture_output=True, cwd=str(dir), env=sub_env, check=True)
    elapsed = time() - start
    return RunResult(proc.stdout, proc.stderr, proc.returncode, elapsed)

def run_tool(command, *params):
    return run(".", "java", "-jar", "lib/tools.jar", command, *params)

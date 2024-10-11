import json
import traceback
import subprocess
import subprocess
from time import time

class RunResult:
    def __init__(self, proc, time):
        self.proc = proc
        self.time = time

    @property
    def stdout(self):
        return self.proc.stdout

    def lines(self):
        return self.proc.stdout.splitlines()

    def json(self):
        return json.loads(self.stdout)

    @property
    def stderr(self):
        return self.proc.stderr

def run(dir, *commands):
    print(" ".join(commands))
    start = time()
    proc = subprocess.run(commands, universal_newlines=True, check=True, stderr=subprocess.PIPE, stdout=subprocess.PIPE, cwd=str(dir))
    end = time() - start
    return RunResult(proc, end)

def run_tool(command, *params):
    return run(".", "java", "-jar", "lib/tools.jar", command, *params)

class FailureCounter:
    failed = 0

    def __init__(self, func, print_exec_err=True):
        self.func = func
        self.print_exec_err = print_exec_err

    def __call__(self, *args, **kwargs):
        try:
            self.func(*args, **kwargs)
        except subprocess.CalledProcessError as e:
            traceback.print_exc()
            if self.print_exec_err:
                print(e.stdout)
                print(e.stderr)
            raise
        except Exception as e:
            traceback.print_exc()
            raise
        else:
            failed = -1
        finally:
            failed += 1
            if failed > 10:
                raise TooManyFailureException()

class TooManyFailureException(Exception):
    pass
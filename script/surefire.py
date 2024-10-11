import base64
import binascii
from contextlib import contextmanager
import itertools
from subprocess import Popen
import asyncio
import shutil
import subprocess
import re
import threading
import tempfile
from pathlib import Path
from time import time
from util import run, run_tool
import logging

AGENT_RT_JAR = Path("lib", "agent-rt.jar").resolve()
EMPTY_AGENT_RT_JAR = Path("lib", "empty-agent-rt.jar").resolve()
MANIFEST = "META-INF/MANIFEST.MF"

class SurefireException(Exception):
    pass

class PropetyValidator:
    def __init__(self):
        self._classes = set()
        self._libs = set()
        self._tests = set()
        self._current = set()
        self.dependencies = set()
        self._file = None

    def validate(self, property_file):
        if self._file != property_file:
            found, tests = self._read_properties(property_file)
            self._validate_properties(found, tests)

    def _read_properties(self, surefire_property):
        found = set()
        tests = []
        for props in surefire_property.read_text().splitlines():
            if any(props.startswith(t) for t in SurefireConnector.EXCLUDED_PROPERTY_KEYS):
                continue
            elif props.startswith("classPathUrl."):
                value = Path(props.split("=", 1)[1]).resolve()
                self.dependencies.add(str(value))
                if value.is_dir():
                    for item in Path(value).rglob("*.class"):
                        self._classes.add(str(item.relative_to(value))[:-6])
                elif value.exists():
                    self._libs.add(str(value))
            elif props.startswith("tc."):
                value = props.split("=", 1)[1]
                tests.append(value)
            else:
                found.add(props)

        return found, tests

    def _validate_properties(self, found, found_tests):
        properties = self._current
        tests = self._tests
        if properties is None:
            self._current = found
            for t in found_tests:
                if t in tests:
                    logging.warn("Same test name: " + t)
                tests.add(t)
        elif properties != found:
            logging.error("1")
            for item in properties - found:
                logging.error(item)

            logging.error("2")
            for item in found - properties:
                logging.error(item)

            raise Exception()

    def reset(self):
        self._current = None
        self.dependencies.clear()

    def write(self, runtime_root):
        run_tool("signature", str(runtime_root / "signature.set"), *map(str, self._libs))
        (runtime_root / "classes.lst").write_text("\n".join(self._classes))
        tests = (runtime_root / "tests.lst")
        tests.write_text("\n".join(self._tests))
        return tests


class SurefireConnector:
    MODULE_REGEX = re.compile(r"[\-]+\< [^\:]+\:([^ ]+) \>[\-]+", re.DOTALL)
    FORK_SCRIPT_REGEX = re.compile(r"Forking command line: /bin/sh -c cd ([^&]+) && (.+)$", re.DOTALL)
    EXCLUDE_FILE = str(Path("errortest.txt").resolve())
    EXCLUDED_PROPERTY_KEYS = ["forkNumber", "forkNodeConnectionString", "forkTestSet", "#"]

    def __init__(self, root: Path):
        regex = SurefireConnector.FORK_SCRIPT_REGEX
        module = SurefireConnector.MODULE_REGEX
        excluded = SurefireConnector.EXCLUDE_FILE
        modules = self._modules = dict()

        proc = run(root, "mvn", "clean", "package", f"-Dsurefire.excludesFile={excluded}", "-X")
        validator = PropetyValidator()
        name = None
        
        for line in proc.stdout.splitlines():
            if m := module.search(line):
                logging.info(m)
                name = m.group(1)
                validator.reset()
            elif m := regex.search(line):
                print(line)
                current = modules[name] = {"name": name}
                cwd, commands = m.groups()
                cwd = cwd.strip("\'")
                logging.info("working directory: " + cwd)
                current["cwd"] = str(cwd)

                commands = [t.strip("\'") for t in commands.split(" ")[1:]]
                try:
                    idx = commands.index("-jar")
                    current["jvmArgs"] = commands[:idx]
                except ValueError:
                    for v in commands:
                        if v.startswith("@"):
                            lll = []
                            for s in Path(v[1:]).read_text().splitlines()[:-1]:
                                s = s.replace('"', "")
                                lll.append(s)
                            current["jvmArgs"] = lll
                            break

                logging.info("jvmArgs: " + " ".join(current["jvmArgs"]))
                surefire_property = SurefireConnector._parse_params(commands)
                current["properties"] = surefire_property
                validator.validate(surefire_property)
                current["dependencies"] = ":".join(validator.dependencies)

        self._root = runtime_root = root.parent / "runtime"
        if runtime_root.exists():
            shutil.rmtree(str(runtime_root))

        runtime_root.mkdir()
        self.test_file = validator.write(runtime_root)

    @property
    def runtime_root(self):
        return self._root

    @staticmethod
    def _parse_params(params):
        surefire_property = Path(params[-2])
        if not surefire_property.exists():
            surefire_property = Path(params[-4], params[-2])
            if not surefire_property.exists():
                raise Exception(params)

        return surefire_property


    def run(self, method_type, selected=None, dryrun=False, disable_agent=False):
        runtime_root = self._root.resolve()
        elapsed = 0
        for module in self._modules.values():
            logging.info(module["name"])
            module_root = self._root / module["name"] 
            module_root.mkdir(exist_ok=True)

            selected_file = None
            if selected is not None:
                selected_file = module_root /  f"selected_{method_type}.lst"
                selected_file.write_text("\n".join(selected))
                selected_file = selected_file.resolve()

            commands = ["java", *module["jvmArgs"]]
            agent = f"-javaagent:{EMPTY_AGENT_RT_JAR}"
            if not disable_agent and method_type:
                agent = f"-javaagent:{AGENT_RT_JAR}={method_type},{runtime_root},{str(dryrun).lower()}"

            runner = str(Path("runner/runner.jar").resolve())
            commands.append(agent)
            commands.append("-classpath")
            commands.append(":".join([runner, module['dependencies']]))
            commands.append("kr.ac.korea.rts.ExperimentalLauncher")
            commands.append(str(module["properties"]))
            if selected_file:
                commands.append(str(selected_file))

            # Remove test-related files.
            mybatis_file = Path(module["cwd"], 'ibderby')
            if mybatis_file.exists():
                shutil.rmtree(mybatis_file.absolute())

            ret = run(module["cwd"], *commands)
            if ret.code != 0:
                raise Exception(ret.stdout)

            elapsed += ret.elapsed
        
        return elapsed
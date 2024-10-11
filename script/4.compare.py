#!/usr/bin/env python3

import numpy
from scipy import stats
from pathlib import Path
from collections import defaultdict
import json
import csv
from pprint import pprint

ROOT = Path(".").absolute().parent / "run"

alltime = {
    'zxing': 47.50,
    'mybatis-3': 29.88,
    'HikariCP': 192.29,
    'commons-configuration': 19.14,
    'commons-pool': 361.38
}

for project in ROOT.iterdir():
    name = project.name
    runtime_file = (project / "runtime.csv")
    if not runtime_file.exists():
        continue

    print(name)
    table = {}
    allt = alltime[name]
    for row in csv.reader(runtime_file.read_text().splitlines()):
        method_name = row[0]
        row = numpy.array(list(map(float, row[1:])))
        row = row / allt

        table[method_name] = row

        t, p = stats.kstest(row, 'norm', args=(row.mean(), row.var()**0.5))
        print(f'[{method_name}]  t-statistics: {t}  p-value:{p}')

    for m1, m2 in [("proposed", "hyrts"), ("proposed", "class"), ("hyrts", "class")]:
        x1 = table[m1]
        x2 = table[m2]
        #t, p = stats.bartlett(x1, x2)
        result = stats.ttest_ind(x1, x2, equal_var = False)
        print(f'[{m1}: {x1.mean()} vs. {m2}: {x2.mean()}] {result}')
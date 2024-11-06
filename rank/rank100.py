import requests
import json
import subprocess
import shutil
import os

BLACKLIST_FILE = "blacklist.txt"
RANK_SNAPSHOT = "rank100.txt"
JAVA_HOME="/usr/lib/jvm/default-runtime"

# Replace with your GitHub personal access token
ACCESS_TOKEN = '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'

sub_env = os.environ.copy()
sub_env['JAVA_HOME'] = JAVA_HOME

blacklist = None
with open(BLACKLIST_FILE) as fh:
    blacklist = {name.strip().split()[0] for name in fh}

data = None
if not os.path.exists(RANK_SNAPSHOT):
    headers = {
        'Authorization': f'token {ACCESS_TOKEN}'
    }

    graphql_url = 'https://api.github.com/graphql'
    graphql_query = """
    {
        search(query: "language:Java stars:>1000 sort:stars", type: REPOSITORY, first: 100) {
            edges {
                node {
                    ... on Repository {
                        name
                        url
                        stargazers {
                            totalCount
                        }
                    }
                }
            }
        }
    }
    """

    # Send a POST request to the GitHub GraphQL API
    response = requests.post(graphql_url, headers=headers, json={'query': graphql_query})
    response.raise_for_status()
    data = response.json()
    data = data['data']['search']['edges']
    with open(RANK_SNAPSHOT, 'w') as fh:
        json.dump(data, fh)
else:
    with open(RANK_SNAPSHOT, 'r') as fh:
        data = json.load(fh)

count = 0
for repo in data:
    repo_data = repo['node']
    url = repo_data["url"]
    name = repo_data["name"]

    if name in blacklist:
        continue

    out = f'repos/{name}'

    if not os.path.exists(out):
        result = subprocess.run(['git', 'clone', '--depth', '1', url, out], check=True, env=sub_env)

    if 'pom.xml' not in os.listdir(out):
        shutil.rmtree(out)
        blacklist.add(f'{name} # no pom.xml')
        continue

    subprocess.run(['git', 'fetch', '--unshallow'], cwd=out, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, env=sub_env)
    result = subprocess.run(['git', 'rev-list', '--count', '--first-parent', '--since="2023-01-01"', '--until="2023-09-30"', 'HEAD'], capture_output=True, text=True, check=True, cwd=out, env=sub_env)
    
    commits_count = int(result.stdout.strip())
    if commits_count < 10:
        shutil.rmtree(out)
        blacklist.add(f'{name} # not enough commits')
        continue

    test_result_file = f'repos/{name}_testable.txt'
    if not os.path.exists(test_result_file):
        with open(test_result_file, 'w') as fh, open(f'repos/{name}_testable_error.txt', 'w') as err:
            subprocess.run(['mvn', 'package'], cwd=out, text=True, stdout=fh, stderr=err, env=sub_env)

    result = subprocess.run(['git', 'rev-parse', 'HEAD'], capture_output=True, text=True, check=True, cwd=out, env=sub_env)
    head = result.stdout.strip()

    test_count = 0
    for path, dirs, files in os.walk(out):
        if not path.endswith('surefire-reports'):
            continue

        for file in files:
            if file.endswith('.xml'):
                test_count += 1

    count += 1
    print(f'{repo_data["url"]} & {head[:7]} & {test_count} & - \\\\')
        
with open(BLACKLIST_FILE, 'w') as fh:
    fh.write('\n'.join(blacklist))
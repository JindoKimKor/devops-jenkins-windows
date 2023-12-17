import os
import sys
import requests
import json
import argparse

# Command-line arguments:
parser = argparse.ArgumentParser(description="Arguments for retrieving a full commit hash from Bitbucket.", formatter_class=argparse.ArgumentDefaultsHelpFormatter)
parser.add_argument("pr-commit", help="The short hash for the PR's commit.")
args = vars(parser.parse_args())

pr_commit = args["pr-commit"]

# Environment variables:
access_token = os.getenv('BITBUCKET_ACCESS_TOKEN')
pr_repo = os.getenv('JOB_REPO')

# Global variables:
url = f'{pr_repo}/commit/{pr_commit}/?fields=hash'

headers = {
    "Accept": "application/json",
    "Authorization": "Bearer " + access_token
}

# Retrieving the commit data from Bitbucket Cloud API.
response = requests.get(url, headers=headers)
response_json_content = json.dumps(response.json())
response_values = json.loads(response_json_content)

# Printed to the console so the pipeline script can retrieve it.
sys.stdout.write(response_values["hash"])
import os
import sys
import requests
import json
import argparse

# Command-line arguments:
parser = argparse.ArgumentParser(description="Arguments for sending Bitbucket build statuses.", formatter_class=argparse.ArgumentDefaultsHelpFormatter)
parser.add_argument("pr-commit", help="The full SHA hash of the commit where the build status will be sent.")
parser.add_argument("pr-status", choices=['SUCCESSFUL', 'FAILED', 'STOPPED', 'INPROGRESS'])
args = vars(parser.parse_args())

# Environment variables:
access_token = os.getenv('BITBUCKET_ACCESS_TOKEN')
pr_repo = os.getenv('JOB_REPO')
build_id = os.getenv('BUILD_ID')
build_url = os.getenv('BUILD_URL')

# Global variables:
url = f'{pr_repo}/commit/{args["pr-commit"]}/statuses/build'

headers = {
    "Accept": "application/json",
    "Content-Type": "application/json",
    "Authorization": "Bearer " + access_token
}

# Sending the build status to Bitbucket Cloud API.
build_status = json.dumps( {
    "key": build_id,
    "state": args['pr-status'],
    "description": args['pr-status'],
    "url": build_url
} )

response = requests.post(url, data=build_status, headers=headers)
response.raise_for_status()
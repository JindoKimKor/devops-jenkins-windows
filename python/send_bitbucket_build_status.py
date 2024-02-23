import os
import sys
import requests
import json
import argparse

# Command-line arguments:
parser = argparse.ArgumentParser(description="Arguments for sending Bitbucket build statuses.", formatter_class=argparse.ArgumentDefaultsHelpFormatter)
parser.add_argument("pr-commit", help="The full SHA hash of the commit where the build status will be sent.")
parser.add_argument("pr-status", choices=['SUCCESSFUL', 'FAILED', 'STOPPED', 'INPROGRESS'])
parser.add_argument("-desc", "--description", help="An optional argument for adding additional information to the build description.")
args = vars(parser.parse_args())

# Environment variables:
access_token = os.getenv('BITBUCKET_ACCESS_TOKEN')
pr_repo = os.getenv('JOB_REPO')
build_id = os.getenv('BUILD_ID')
build_url = os.getenv('BUILD_URL')

# Global variables:
url = f'{pr_repo}/commit/{args["pr-commit"]}/statuses/build'
description = f"{args['pr-status']}: {args['description']}" if (args['description'] != None) else args['pr-status']

headers = {
    "Accept": "application/json",
    "Content-Type": "application/json",
    "Authorization": "Bearer " + access_token
}

# Sending the build status to Bitbucket Cloud API.
build_status = json.dumps( {
    "key": build_id,
    "state": args['pr-status'],
    "description": description,
    "url": build_url
} )

try:
    response = requests.post(url, data=build_status, headers=headers)
    response.raise_for_status()
except requests.exceptions.RequestException as e:
    print(f"Initial Request: {e.request.body}")
    print(f"Response Error: {json.dumps(e.response.json())}")
    exit(1)
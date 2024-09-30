import json
import sys
import os
import argparse
import requests

# Function definition to parse string from report
def count_errors(json_file):
    # Normalize the path
    normalizedPath = os.path.normpath(json_file)

    if not os.path.isfile(normalizedPath):
        raise FileNotFoundError(f"File not found: {normalizedPath}")

    # Load file
    with open(normalizedPath, 'r') as f:
        data = json.load(f)

    # Counters
    total_errors = 0
    file_error_count= {} # To add error count per file

    # Get the num of errors and errors per file
    for object in data:
        if 'FileName' in object and 'FileChanges' in object: # FileName and FileChanges are key in the JSON report made by dotnet format
            file_name = object['FileName']

            # Add error count for file, the report appears to make objects for each error so each mention of filename = 1 error
            if file_name in file_error_count:
                file_error_count[file_name] += 1
            else:
                file_error_count[file_name] = 1

            total_errors += 1

    # Print results (for dubgging remove later probably)
    print(f"Total number of errors: {total_errors}")
    for file, errors in file_error_count.items():
        print(f"{file} errors = {errors}")

    # Build string to return
    retstr = f"Total number of errors: {total_errors}"
    for file, errors in file_error_count.items():
        retstr += (f"\n{file} errors = {errors}")

    return retstr

# Command-line arguments: 
parser = argparse.ArgumentParser(description="Arguments for Bitbucket test reports.", formatter_class=argparse.ArgumentDefaultsHelpFormatter)

parser.add_argument("lint-report-path", help="The path in the Jenkins workspace where the linting report is located.")
parser.add_argument("commit", help="The commit hash the report will be sent to.")
parser.add_argument("Result", choices=['Pass', 'Fail'], help="pass or fail, indicating what type of report.")

args = vars(parser.parse_args())

# Environment variables:
access_token = os.getenv('BITBUCKET_ACCESS_TOKEN')
ticket_number = os.getenv('TICKET_NUMBER')
pr_repo = os.getenv('JOB_REPO')
folder_name = os.getenv('FOLDER_NAME')

# Global variables:
url = f'{pr_repo}/commit/{args["commit"]}/reports/lint-test-report'

headers = {
    "Accept": "application/json",
    "Content-Type": "application/json",
    "Authorization": "Bearer " + access_token
}

result = "PASSED" if args["Result"] == "Pass" else "FAILED"
details = "0 Formatting errors" if args["Result"] == "Pass" else "Formatting Errors Detected"
datastring = "No report" if args["Result"] == "Pass" else count_errors(args["lint-report-path"])

# Sending the report to Bitbucket Cloud API.
report = json.dumps( {
    "title": f"Linting Report",
    "details": f"{details}", 
    "report_type": "TEST",
    "reporter": "Jenkins",
    "result": f"{result}",
    #"link": f"",# Do we want a link to the json? in which case may have to scp the report over to the apache server
    "data": [
        {
            "type": "TEXT",
            "title": "Report Details",
            "value": datastring
        },
        {
            "type": "BOOLEAN",
            "title": "Linting check passed?",
            "value": True  if args["Result"] == "Pass" else False 
        }
    ]
} )

print(f"URL: {url}")
print(f"Headers: {headers}")
print(f"Body: {report}")
try:
    response = requests.put(url, data=report, headers=headers)
    response.raise_for_status()
except requests.exceptions.RequestException as e:
    print(f"Initial Request: {e.request.body}")
    print(f"Response Error: {json.dumps(e.response.json())}")
    exit(1)

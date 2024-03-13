import os
import sys
import requests
import json
import xml.etree.ElementTree as ET
import argparse
import urllib.parse

# Command-line arguments:
parser = argparse.ArgumentParser(description="Arguments for Bitbucket coverage reports.", formatter_class=argparse.ArgumentDefaultsHelpFormatter)

parser.add_argument("commit", help="The commit hash the report will be sent to.")
parser.add_argument("coverage-results-path", help="The path in the Jenkins workspace where the coverage results are located.")

args = vars(parser.parse_args())

# Environment variables:
access_token = os.getenv('BITBUCKET_ACCESS_TOKEN')
ticket_number = os.getenv('TICKET_NUMBER')
pr_repo = os.getenv('JOB_REPO')
folder_name = os.getenv('FOLDER_NAME')

# Global variables:
url = f'{pr_repo}/commit/{args["commit"]}/reports/coverage-report'
results_file_name = "Summary.xml"

headers = {
    "Accept": "application/json",
    "Content-Type": "application/json",
    "Authorization": "Bearer " + access_token
}

# Parses the line coverage percentage from the code coverage HTML report.
def get_line_coverage(result_file):
    tree_root = ET.parse(result_file).getroot()
    line_coverage = tree_root.find('Summary').find('Linecoverage').text
    return line_coverage

result = get_line_coverage(f'{args["coverage-results-path"]}/{results_file_name}')
result_float = float(result)

# Sending the report to Bitbucket Cloud API.
report = json.dumps( {
    "title": f"{ticket_number}: Code Coverage",
    "details": "*Only includes line coverage.",
    "report_type": "COVERAGE",
    "reporter": "Jenkins",
    "link": f"http://dlx-webhost.canadacentral.cloudapp.azure.com/{folder_name}/Reports/{ticket_number}/CodeCoverage-report/index.html",
    "data": [
        {
            "type": "PERCENTAGE",
            "title": "Line Coverage",
            "value": result_float
        }
    ]
} )

try:
    response = requests.put(url, data=report, headers=headers)
    response.raise_for_status()
except requests.exceptions.RequestException as e:
    print(f"Initial Request: {e.request.body}")
    print(f"Response Error: {json.dumps(e.response.json())}")
    exit(1)
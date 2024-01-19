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
build_id = os.getenv('BUILD_ID')
pr_repo = os.getenv('JOB_REPO')
job_name = urllib.parse.quote(os.getenv('JOB_NAME')).split("/")

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
    "title": f"{build_id}: Code Coverage",
    "details": "*Only includes line coverage.",
    "report_type": "COVERAGE",
    "reporter": "Jenkins",
    "link": f"http://jenkins.varlab.org/job/{job_name[0]}/job/{job_name[1]}/Reports/",
    "data": [
        {
            "type": "PERCENTAGE",
            "title": "Line Coverage",
            "value": result_float
        }
    ]
} )

response = requests.put(url, data=report, headers=headers)
response.raise_for_status()
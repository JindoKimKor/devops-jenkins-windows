import os
import sys
import requests
import json
import xml.etree.ElementTree as ET
import argparse

# Command-line arguments: 
parser = argparse.ArgumentParser(description="Arguments for Bitbucket test reports.", formatter_class=argparse.ArgumentDefaultsHelpFormatter)

parser.add_argument("commit", help="The commit hash the report will be sent to.")
parser.add_argument("test-results-path", help="The path in the Jenkins workspace where the test results are located.")
parser.add_argument("test-mode", choices=['EditMode', 'PlayMode'], help="The mode to run the Unity tests in.")

args = vars(parser.parse_args())

# Environment variables:
access_token = os.getenv('BITBUCKET_ACCESS_TOKEN')
build_id = os.getenv('BUILD_ID')
pr_repo = os.getenv('JOB_REPO')
folder_name = os.getenv('FOLDER_NAME')

# Global variables:
url = f'{pr_repo}/commit/{args["commit"]}/reports/{args["test-mode"]}-test-report'
results_file_name = "editmode" if (args["test-mode"] == 'EditMode') else "playmode"

headers = {
    "Accept": "application/json",
    "Content-Type": "application/json",
    "Authorization": "Bearer " + access_token
}

# Parses the number of tests failed from the results XML file.
def get_number_of_tests_failed(result_file):
    tree_root = ET.parse(result_file).getroot()
    total_tests = tree_root.attrib['total']
    total_failed = tree_root.attrib['failed']
    results = {
        "total_tests": total_tests,
        "total_failed": total_failed
    }
    return results

# Request variables:
failed_tests = get_number_of_tests_failed(f'{args["test-results-path"]}/{results_file_name}-results.xml')
dataBool = True if (int(failed_tests['total_failed']) == 0) else False
result = "PASSED" if (int(failed_tests['total_failed']) == 0) else "FAILED"

# Sending the report to Bitbucket Cloud API.
report = json.dumps( {
    "title": f"{build_id}: {args['test-mode']} Tests",
    "details": f"{failed_tests['total_failed']}/{failed_tests['total_tests']} tests failed.",
    "report_type": "TEST",
    "reporter": "Jenkins",
    "result": f"{result}",
    "link": f"http://dlx-webhost.canadacentral.cloudapp.azure.com/{folder_name}/Reports/{build_id}/{results_file_name}-report/TestReport.html",
    "data": [
        {
            "type": "BOOLEAN",
            "title": "All tests passed?",
            "value": dataBool
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
import os
import argparse
import requests
import sys
from jinja2 import Environment, FileSystemLoader

#Environment variables:
jenkins_token = os.getenv('JENKINS_API_KEY')
build_url = os.getenv('BUILD_URL')
working_dir = os.getenv('WORKING_DIR')
ticket = os.getenv('TICKET_NUMBER')
workspace = os.getenv('WORKSPACE')

#Global variables
user_pass = jenkins_token.split(":")

def get_log_lines(path):
    log = []
    if (os.path.isfile(path)):
        with open(path, 'r') as test_log:
            log = test_log.readlines()
        
    return log

editmode_log = get_log_lines(f"{working_dir}/test_results/EditMode-tests.log")
playmode_log = get_log_lines(f"{working_dir}/test_results/PlayMode-tests.log")
unity_build_log = get_log_lines(f"{working_dir}/build.log")

jenkins_log = requests.get(f"{build_url}consoleText", auth=(user_pass[0], user_pass[1]))

environment = Environment(loader=FileSystemLoader(f"{workspace}/python/log-template/"))
template = environment.get_template("logs.html")

logs_file = f"{working_dir}/logs.html"
content = template.render(
    ticket=ticket,
    jenkins=jenkins_log.iter_lines(),
    editMode=editmode_log,
    playMode=playmode_log,
    build=unity_build_log
)

with open(logs_file, mode="w", encoding="utf-8") as logs:
    logs.write(content)
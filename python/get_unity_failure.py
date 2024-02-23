import os
import sys
import argparse

# Command-line arguments:
parser = argparse.ArgumentParser(description="Arguments for parsing a build's error logs", formatter_class=argparse.ArgumentDefaultsHelpFormatter)
parser.add_argument("log", help="The path to the log to parse.")
args = vars(parser.parse_args())

#Global variables:
errors = []
found_error = ""

with open('../logErrors.txt', 'r') as error_file:
    errors = error_file.readlines()

with open(args["log"], 'r') as log_file:
    if any(error in line for error in errors):
        found_error = line

sys.stdout.write(found_error)
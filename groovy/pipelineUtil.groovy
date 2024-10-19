//This function will check all the initial subdirectories 1 level under the project folder and 
//if it finds a package.json file, it will then CD and install in the directory, before exiting it
//will change the directory back to the one it was passed
def installNPMInSubDirs(projectFolder){
    echo "Project directory: ${projectFolder}"

    // Command to list immediate subdirectories with package.json
    def command = "for /d %%d in (\"${projectFolder}\\*\") do @if exist \"%%d\\package.json\" echo \"%%d\""
    echo "Executing command: ${command}"

    // List the immediate subdirectories and check for package.json files
    def subDirs = bat(script: command, returnStdout: true).trim()

    if (subDirs) {
        // Split the output into an array of directory paths
        def packageJsonDirs = subDirs.split("\\r?\\n")

        // Loop through each directory path and run npm install
        for (def dir : packageJsonDirs) {
            echo "Installing dependencies in directory: ${dir}"

            // Prepare the command for npm install
            def npmCommand = "cd \"${dir}\" && npm install"
            echo "Running command: ${npmCommand}"

            // Run npm install in the directory containing the package.json
            bat npmCommand
        }
    } else {
        echo "No package.json files found in the immediate subdirectories of ${projectFolder}."
    }

    // Change back to PROJECT_DIR
    bat "cd \"${projectFolder}\""
}

// Checks whether a branch is up to date with the destination branch by seeing if it is an ancestor of the destination.
// This is in its own method to avoid pipeline failure if the branch needs updating.
def isBranchUpToDate(destinationBranch) {
    return sh (script: "git merge-base --is-ancestor origin/${destinationBranch} @", returnStatus: true)
}

// Attempts to merge the destination branch into the current branch.
// This is in its own method to avoid automatic pipeline failure if there are merge errors. We want to alert the user of the merge errors.
def tryMerge(destinationBranch) {
    return sh (script: "git merge origin/${destinationBranch}", returnStatus: true)
}

// Retrieves the full commit hash from Bitbucket Cloud API, since the webhook only gives us the short version.
def getFullCommitHash(workspace, shortCommit) {
    return sh (script: "python \'${workspace}/python/get_bitbucket_commit_hash.py\' ${shortCommit}", returnStdout: true)
}

// Retrieves the latest commit hash of the current local repository.
def getCurrentCommitHash(){
    return sh (script: "git rev-parse HEAD", returnStdout: true).trim() 
}

// Checks if the currentHash from the local repository and the commitHash from the remote repository are equal.
def isEqualCommitHash(currentHash, commitHash){
    return currentHash.equals(commitHash)
}

// Sends a build status to Bitbucket Cloud API.
def sendBuildStatus(workspace, state, commitHash, deployment = "") {
    sh "python \'${workspace}/python/send_bitbucket_build_status.py\' ${commitHash} ${state} ${deployment}"
}

// Sends a test report to Bitbucket Cloud API. Testmode can either be EditMode or PlayMode.
def sendTestReport(workspace, reportDir, commitHash, testMode) {
    sh "python \'${workspace}/python/create_bitbucket_test_report.py\' \'${commitHash}\' \'${reportDir}/test_results\' \'${testMode}\'"
}

// Sends a code coverage report to Bitbucket Cloud API.
def sendCoverageReport(workspace, reportDir, commitHash) {
    sh "python \'${workspace}/python/create_bitbucket_codecoverage_report.py\' \'${commitHash}\' \'${reportDir}/coverage_results/Report\'"
}

// Parses the given log for any errors recorded in a text file of known errors. Not currently in use.
def parseLogsForError(logPath) {
    return sh (script: "python \'${workspace}/python/get_unity_failure.py\' \'${logPath}\'", returnStdout: true)
}

def checkIfFileIsLocked(filePath) {
    return bat (script: """2>nul (
            >>\"${filePath}\" (call )
        ) && (exit 0) || (exit 1)""", returnStatus: true)
}

// Checks if an exit code thrown during a test stage should fail the PR Pipeline. ExitCode 2 means failing tests, which we want to report back to Bitbucket
// without failing the entire pipeline.
def checkIfTestStageExitCodeShouldExit(workspace, exitCode) {
    if (exitCode == 3 || exitCode == 1) {
        sh "exit ${exitCode}"
    }
}

// Checks if a Unity executable exists and returns its path. Downloads the missing Unity version if not installed.
def getUnityExecutable(workspace, projectDir) {
    def unityExecutable = "${sh (script: "python \'${workspace}/python/get_unity_version.py\' \'${projectDir}\' executable-path", returnStdout: true)}"

    if (!fileExists(unityExecutable)) {
        def version = sh (script: "python \'${workspace}/python/get_unity_version.py\' \'${projectDir}\' version", returnStdout: true)
        def revision = sh (script: "python \'${workspace}/python/get_unity_version.py\' \'${projectDir}\' revision", returnStdout: true)

        echo "Missing Unity Editor version ${version}. Installing now..."
        sh "\"C:\\Program Files\\Unity Hub\\Unity Hub.exe\" -- --headless install --version ${version} --changeset ${revision}"

        echo "Installing WebGL Build Support..."
        sh "\"C:\\Program Files\\Unity Hub\\Unity Hub.exe\" -- --headless install-modules --version ${version} -m webgl"
    }

    return unityExecutable
}

// Runs a Unity project's tests of a specified type, while also allowing optional code coverage and test reporting.
def runUnityTests(unityExecutable, reportDir, projectDir, testType, enableReporting, deploymentBuild) {
    //setup for commands/executable

    def logFile = "${reportDir}/test_results/${testType}-tests.log"

    def reportSettings = (enableReporting) ? """ \
        -testResults \"${reportDir}/test_results/${testType}-results.xml\" \
        -debugCodeOptimization \
        -enableCodeCoverage \
        -coverageResultsPath \"${reportDir}/coverage_results\" \
        -coverageOptions \"generateAdditionalMetrics;generateHtmlReport;useProjectSettings\"""" : ""

    def flags = "-runTests \
        -batchmode \
        -testPlatform ${testType} \
        -projectPath \"${projectDir}\" \
        -logFile \"${logFile}\"${reportSettings}"

    // Allows only PlayMode to run with graphics enabled
    if(testType == "EditMode"){
        flags += "-nographics"
    }

    if(testType == "PlayMode"){
        flags += " -testCategory BuildServer"
    }

    echo "Flags set to: ${flags}"

    def exitCode = sh (script: """\"${unityExecutable}\" \
        ${flags}""", returnStatus: true)

    if ((exitCode != 0)) {
        if(deploymentBuild){
            error("Test failed with exit code ${exitCode}. Check the log file for more details.")
            sh "exit ${exitCode}"
        }
        catchError(buildResult: 'FAILURE', stageResult: 'FAILURE'){
            error("Test failed with exit code ${exitCode}. Check the log file for more details.")
        }
    }
}

// Converts a Unity test result XML file to an HTML file.
def convertTestResultsToHtml(reportDir, testType) {
    // Writing the console output to a file because Jenkins' returnStdout still causes the pipeline to fail if the exit code != 0
    def exitCode = sh (script: """dotnet C:/UnityTestRunnerResultsReporter/UnityTestRunnerResultsReporter.dll \
        --resultsPath=\"${reportDir}/test_results\" \
        --resultXMLName=${testType}-results.xml \
        --unityLogName=${testType}-tests.log \
        --reportdirpath=\"${reportDir}/test_results/${testType}-report\" > UnityTestRunnerResultsReporter.log""", returnStatus: true)
    
    // This Unity tool throws an error if any tests are failing, yet still generates the report.
    // If the tests are failing, we want to avoid failing the pipeline so we can access the report.
    if (exitCode != 0) {
        consoleOutput = readFile("UnityTestRunnerResultsReporter.log")
        def testReportGenerated = consoleOutput =~ /Test report generated:/

        if (!testReportGenerated) {
            println "Error: Test report was not generated for ${testType}. Exit code: ${exitCode}"
            println "Log Output: ${consoleOutput}"
            sh "exit 1"
        }
    }
}

// Parses a Jira ticket number from the branch name.
def parseTicketNumber(branchName) {
    def patternMatches = branchName =~ /[A-Za-z]+-[0-9]+/
    
    if (patternMatches) {
        return patternMatches[0]
    }
}

// Publishes a test result HTML file to the VARLab's remote web server for hosting.
def publishTestResultsHtmlToWebServer(remoteProjectFolderName, ticketNumber, reportDir, reportType) {
     sh """ssh vconadmin@dlx-webhost.canadacentral.cloudapp.azure.com \
    \"sudo mkdir -p /var/www/html/${remoteProjectFolderName}/Reports/${ticketNumber}/${reportType}-report \
    && sudo chown vconadmin:vconadmin /var/www/html/${remoteProjectFolderName}/Reports/${ticketNumber}/${reportType}-report \
    && sudo chmod -R 755 /var/www/html/${remoteProjectFolderName}/Reports \""""

    sh "scp -i C:/Users/ci-catherine/.ssh/vconkey1.pem -rp \"${reportDir}/*\" \
    \"vconadmin@dlx-webhost.canadacentral.cloudapp.azure.com:/var/www/html/${remoteProjectFolderName}/Reports/${ticketNumber}/${reportType}-report\""
}

// Deletes a branch's reports from the web server after it has been merged.
def cleanMergedBranchReportsFromWebServer(remoteProjectFolderName, ticketNumber) {
    sh """ssh vconadmin@dlx-webhost.canadacentral.cloudapp.azure.com \
    \"sudo rm -r -f /var/www/html/${remoteProjectFolderName}/Reports/${ticketNumber}\""""
}

// Builds a Unity project.
def buildProject(reportDir, projectDir, unityExecutable) {
    def logFile = "${reportDir}/build.log"

    def exitCode = sh (script:"""\"${unityExecutable}\" \
        -quit \
        -batchmode \
        -nographics \
        -projectPath \"${projectDir}\" \
        -logFile \"${logFile}\" \
        -buildTarget WebGL \
        -executeMethod Builder.BuildWebGL""", returnStatus: true)

    if (exitCode != 0) {
        sh "exit ${exitCode}"
    }
}

// A method for post-build PR actions.
// Creates a log report for Unity logs and Jenkins logs, then publishes it to the web server,
// and lastly sends the build status to Bitbucket.
def postBuild(status) {
    sh "python -u \'${env.WORKSPACE}/python/create_log_report.py\'"

    sh """ssh vconadmin@dlx-webhost.canadacentral.cloudapp.azure.com \
    \"sudo mkdir -p /var/www/html/${env.FOLDER_NAME}/Reports/${env.TICKET_NUMBER} \
    && sudo chown vconadmin:vconadmin /var/www/html/${env.FOLDER_NAME}/Reports/${env.TICKET_NUMBER}\""""

    if (fileExists("${env.REPORT_DIR}/logs.html")) {
        sh "scp -i C:/Users/ci-catherine/.ssh/vconkey1.pem -rp \"${env.REPORT_DIR}/logs.html\" \
    \"vconadmin@dlx-webhost.canadacentral.cloudapp.azure.com:/var/www/html/${env.FOLDER_NAME}/Reports/${env.TICKET_NUMBER}\""
    }

    sendBuildStatus(env.WORKSPACE, status, env.COMMIT_HASH)
}

// A method for post-build PR actions. 
// This one is specific to our JS pipelines as there are no Unity Logs
def postBuildJS(status) {
    sendBuildStatus(env.WORKSPACE, status, env.COMMIT_HASH)
}
return this
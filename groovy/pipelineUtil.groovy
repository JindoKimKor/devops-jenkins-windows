// Checks whether a branch is up to date with the destination branch by seeing if it is an ancestor of the destination.
// This is in its own method to avoid pipeline failure if the branch needs updating.
def isBranchUpToDate(destination) {
    return sh (script: "git merge-base --is-ancestor origin/${destination} @", returnStatus: true)
}

// Attempts to merge the destination branch into the current branch.
// This is in its own method to avoid automatic pipeline failure if there are merge errors. We want to alert the user of the merge errors.
def tryMerge(destination) {
    return sh (script: "git merge origin/${destination}", returnStatus: true)
}

// Retrieves the full commit hash from Bitbucket Cloud API, since the webhook only gives us the short version.
def getFullCommitHash(workspace, shortCommit) {
    return fullCommitHash = sh(script: "python \'${workspace}/python/get_bitbucket_commit_hash.py\' ${shortCommit}", returnStdout: true)
}

// Sends a build status to Bitbucket Cloud API.
def sendBuildStatus(workspace, state, commitHash) {
    sh "python \'${workspace}/python/send_bitbucket_build_status.py\' ${commitHash} ${state}"
}

// Sends a test report to Bitbucket Cloud API. Testmode can either be EditMode or PlayMode.
def sendTestReport(workspace, workingDir, commitHash, testMode) {
    sh "python \'${workspace}/python/create_bitbucket_test_report.py\' \'${commitHash}\' \'${workingDir}/test_results\' \'${testMode}\'"
}

// Sends a code coverage report to Bitbucket Cloud API.
def sendCoverageReport(workspace, workingDir, commitHash) {
    sh "python \'${workspace}/python/create_bitbucket_codecoverage_report.py\' \'${commitHash}\' \'${workingDir}/coverage_results/Report\'"
}

// Checks if an exit code thrown during a test stage should fail the PR Pipeline. ExitCode 2 means failing tests, which we want to report back to Bitbucket
// without failing the entire pipeline.
def checkIfTestStageExitCodeShouldExit(exitCode) {
    if (exitCode == 3 || exitCode == 1) {
        sh "exit ${exitCode}"
    }
}

def getUnityExecutable(workspace, workingDir) {
    def unityExecutable = "${sh (script: "python \'${workspace}/python/get_unity_version.py\' \'${workingDir}\' executable-path", returnStdout: true)}"

    if (!fileExists(unityExecutable)) {
        def version = sh (script: "python \'${workspace}/python/get_unity_version.py\' \'${workingDir}\' version", returnStdout: true)
        def revision = sh (script: "python \'${workspace}/python/get_unity_version.py\' \'${workingDir}\' revision", returnStdout: true)

        echo "Missing Unity Editor version ${version}. Installing now..."
        sh "\"C:\\Program Files\\Unity Hub\\Unity Hub.exe\" -- --headless install --version ${version} --changeset ${revision}"

        echo "Installing WebGL Build Support..."
        sh "\"C:\\Program Files\\Unity Hub\\Unity Hub.exe\" -- --headless install-modules --version ${version} -m webgl"
    }

    return unityExecutable
}

def runUnityTests(unityExecutable, workingDir, testType, enableReporting) {
    def reportSettings = (enableReporting) ? """ \
        -testPlatform ${testType} \
        -testResults \"${workingDir}/test_results/${testType}-results.xml\" \
        -debugCodeOptimization \
        -enableCodeCoverage \
        -coverageResultsPath \"${workingDir}/coverage_results\" \
        -coverageOptions \"generageAdditionalMetrics;dontClear\"""" : ""

    def exitCode = sh (script: """\"${unityExecutable}\" \
        -runTests \
        -batchmode \
        -projectPath . \
        -logFile \"${workingDir}/test_results/${testType}-tests.log\"${reportSettings}""", returnStatus: true)

    return exitCode
}

def convertTestResultsToHtml(workingDir, testType) {
    sh (script: """dotnet C:/UnityTestRunnerResultsReporter/UnityTestRunnerResultsReporter.dll \
        --resultsPath=\"${workingDir}/test_results\" \
        --resultXMLName=${testType}-results.xml \
        --unityLogName=${testType}-tests.log \
        --reportdirpath=\"${workingDir}/test_results/${testType}-report\"""", returnStatus: true)
}

def publishTestResultsHtmlToWebServer(remoteProjectFolderName, buildId, reportDir, reportType) {
    sh """ssh vconadmin@dlx-webhost.canadacentral.cloudapp.azure.com \
    \"sudo mkdir -p /var/www/html/${remoteProjectFolderName}/Reports/${buildId}/${reportType}-report \
    && sudo chown vconadmin:vconadmin /var/www/html/${remoteProjectFolderName}/Reports/${buildId}/${reportType}-report\""""

    sh "scp -i C:/Users/ci-catherine/.ssh/vconkey1.pem -rp \"${reportDir}/*\" \
    \"vconadmin@dlx-webhost.canadacentral.cloudapp.azure.com:/var/www/html/${remoteProjectFolderName}/Reports/${buildId}/${reportType}-report\""
}

def buildProject(unityExecutable) {
    sh """\"${unityExecutable}\" \
        -quit \
        -batchmode \
        -nographics \
        -buildTarget WebGL \
        -executeMethod Builder.BuildWebGL"""
}
return this
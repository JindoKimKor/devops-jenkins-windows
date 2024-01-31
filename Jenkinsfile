// This will be populated by getFullCommitHash().
// It's global so the rest of the Bitbucket API request python scripts can access it.
def fullCommitHash = ""

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
def getFullCommitHash(workspace) {
    fullCommitHash = sh(script: "python \'${workspace}/python/get_bitbucket_commit_hash.py\' ${PR_COMMIT}", returnStdout: true)
    echo fullCommitHash
}

// Sends a build status to Bitbucket Cloud API.
def sendBuildStatus(workspace, state) {
    sh "python \'${workspace}/python/send_bitbucket_build_status.py\' ${fullCommitHash} ${state}"
}

// Sends a test report to Bitbucket Cloud API. Testmode can either be EditMode or PlayMode.
def sendTestReport(workspace, testMode) {
    sh "python \'${workspace}/python/create_bitbucket_test_report.py\' \'${fullCommitHash}\' \'${WORKING_DIR}/test_results\' \'${testMode}\'"
}

// Sends a code coverage report to Bitbucket Cloud API.
def sendCoverageReport(workspace) {
    sh "python \'${workspace}/python/create_bitbucket_codecoverage_report.py\' \'${fullCommitHash}\' \'${WORKING_DIR}/coverage_results/Report\'"
}

// Checks if an exit code thrown during a test stage should fail the PR Pipeline. ExitCode 2 means failing tests, which we want to report back to Bitbucket
// without failing the entire pipeline.
def checkIfTestStageExitCodeShouldExit(exitCode) {
    if (exitCode == 3 || exitCode == 1) {
        sh "exit ${exitCode}"
    }
}


pipeline {
    agent any

    environment { 
        WORKING_DIR = "${WORKSPACE}/PRJob/${PR_BRANCH}"
        JOB_REPO = "${PR_REPO_HTML}"
        BITBUCKET_ACCESS_TOKEN = credentials('bitbucket-access-token')
    }   

    stages {
        // Prepares the workspace for the build by telling Bitbucket the build is in progress, cleaning the working directory,
        // pulling the PR branch and checking if it is up to date, attempting to merge the destination branch into the PR branch if it is not,
        // and finally indentifying which version of the Unity editor the project uses and downloading it to the VM if it is not already installed.
        stage('Prepare Workspace') {
            environment {
                REPO_SSH = "git@bitbucket.org:${PR_PROJECT}.git"
                DESTINATION_BRANCH = "${PR_DESTINATION_BRANCH}"
            }
            steps {
                echo "Sending \'In Progress\' status to Bitbucket..."
                script {
                    getFullCommitHash(WORKSPACE)
                    sendBuildStatus(WORKSPACE, "INPROGRESS")
                }

                echo "Cleaning workspace..."
                sh "rm -rf \"${WORKING_DIR}\""

                echo "Pulling PR branch..."
                sh "git clone ${REPO_SSH} \"${WORKING_DIR}\""
                dir ("${WORKING_DIR}") {
                    sh "git checkout ${PR_BRANCH}"
                
                    echo "Checking if branch is up to date..."
                    script {
                        if (isBranchUpToDate(DESTINATION_BRANCH) == 0) {
                            echo "Branch is up to date."
                        }
                        else {
                            echo "Branch needs to be updated. Merging destination branch into main..."
                        
                            if (tryMerge(DESTINATION_BRANCH) == 0) {
                                echo "Merge successful."
                            }
                            else {
                                echo "Merge errors in your branch. Aborting merge..."
                                sh "git merge --abort"
                                error("Merge aborted.")
                            }
                        }
                    }
                }   

                echo "Identifying Unity version..."
                script {
                    env.UNITY_EXECUTABLE = "${sh (script: "python \'${WORKSPACE}/python/get_unity_version.py\' \'${WORKING_DIR}\' executable-path", returnStdout: true)}"

                    if (!fileExists(UNITY_EXECUTABLE)){
                        def version = sh (script: "python \'${WORKSPACE}/python/get_unity_version.py\' \'${WORKING_DIR}\' version", returnStdout: true)
                        def revision = sh (script: "python \'${WORKSPACE}/python/get_unity_version.py\' \'${WORKING_DIR}\' revision", returnStdout: true)

                        echo "Missing Unity Editor version ${version}. Installing now..."
                        sh "\"C:\\Program Files\\Unity Hub\\Unity Hub.exe\" -- --headless install --version ${version} --changeset ${revision}"

                        echo "Installing WebGL Build Support..."
                        sh "\"C:\\Program Files\\Unity Hub\\Unity Hub.exe\" -- --headless install-modules --version ${version} -m webgl"
                    }
                }
            }
        }
        // Runs the project's EditMode tests, and then generates a test report and a code coverage report.
        // Sends the test results to Bitbucket once the tests complete.
        stage('EditMode Tests') {
            steps {
                echo "Running EditMode tests..."
                dir ("${WORKING_DIR}") {
                    sh "mkdir test_results"
                    sh "mkdir coverage_results"
                    script {
                        def exitCode = sh (script: """\"${UNITY_EXECUTABLE}\" \
                        -runTests \
                        -batchmode \
                        -projectPath . \
                        -testPlatform EditMode \
                        -testResults \"${WORKING_DIR}/test_results/editmode-results.xml\" \
                        -logFile \"${WORKING_DIR}/test_results/editmode-tests.log\" \
                        -debugCodeOptimization \
                        -enableCodeCoverage \
                        -coverageResultsPath \"${WORKING_DIR}/coverage_results\" \
                        -coverageOptions \"generateAdditionalMetrics;dontClear\"""", returnStatus: true)

                        checkIfTestStageExitCodeShouldExit(exitCode)
                    }
                    
                }

                echo "Sending EditMode test results to Bitbucket..."
                sendTestReport(WORKSPACE, "EditMode")
            }
        }
        // Runs the project's PlayMode tests, and then generates a code coverage report.
        // PlayMode tests need to be run once in the editor to generate the overall coverage report.
        stage('PlayMode Tests in Editor') {
            steps {
                echo "Running PlayMode tests in Editor environment..."
                dir ("${WORKING_DIR}") {
                    retry (5) {
                        script {
                            def exitCode = sh (script: """\"${UNITY_EXECUTABLE}\" \
                            -runTests \
                            -batchmode \
                            -projectPath . \
                            -testPlatform PlayMode \
                            -testResults \"${WORKING_DIR}/test_results/playmode-results.xml\" \
                            -logFile \"${WORKING_DIR}/test_results/editor-playmode-tests.log\" \
                            -debugCodeOptimization \
                            -enableCodeCoverage \
                            -coverageResultsPath \"${WORKING_DIR}/coverage_results\" \
                            -coverageOptions \"generateAdditionalMetrics;dontClear\"""", returnStatus: true)

                            checkIfTestStageExitCodeShouldExit(exitCode)
                        }
                    }
                }

                echo "Sending PlayMode test results to Bitbucket..."
                sendTestReport(WORKSPACE, "PlayMode")
            }
        }
        // Merges the two coverage reports from the EditMode and PlayMode (editor) reports into one.
        // Then sends a coverage report to Bitbucket.
        stage('Generate Code Coverage Report') {
            steps {
                echo "Generating code coverage report..."
                dir("${WORKING_DIR}") {
                    sh """\"${UNITY_EXECUTABLE}\" \
                    -batchmode \
                    -logFile \"${WORKING_DIR}/coverage_results/coverage_report.log\" \
                    -projectPath . \
                    -debugCodeOptimization \
                    -enableCodeCoverage \
                    -coverageResultsPath \"${WORKING_DIR}/coverage_results\" \
                    -coverageOptions \"generateHtmlReport;generateHtmlReportHistory;generateBadgeReport;generateAdditionalMetrics\" \
                    -quit"""

                    publishHTML(target: [allowMissing: false,
                        alwaysLinkToLastBuild: true,
                        keepAll: true,
                        reportDir: "coverage_results/Report",
                        reportFiles: 'index.html',
                        reportName: 'Reports',
                        reportTitles: 'Code Coverage'])
                }
                
                echo "Sending code coverage report to Bitbucket..."
                sendCoverageReport(WORKSPACE)
            }
        }
        // Builds the project and saves it.
        stage('Build Project') {
            steps {
                echo "Building Unity project..."
                sh "mv Builder.cs \"${WORKING_DIR}/Assets/Editor/\""
                dir("${WORKING_DIR}") {
                    sh """\"${UNITY_EXECUTABLE}\" \
                    -quit \
                    -batchmode \
                    -nographics \
                    -buildTarget WebGL \
                    -executeMethod Builder.BuildWebGL"""
                }
            }
        }
    }

    // When the pipeline finishes, sends the build status to Bitbucket.
    post {
        success {
            sendBuildStatus(WORKSPACE, "SUCCESSFUL")
        }
        failure {
            sendBuildStatus(WORKSPACE, "FAILED")
        }
        aborted {
            sendBuildStatus(WORKSPACE, "STOPPED")
        }
    }
}
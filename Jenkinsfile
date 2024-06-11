def editMode = "EditMode"
def playMode = "PlayMode"
def util

pipeline {
    agent any

    tools {
        dotnetsdk 'dotnet-core-2.1.202'
    }

    parameters {
        string(name: 'PR_BRANCH', defaultValue: '', description: '')
        string(name: 'PR_DESTINATION_BRANCH', defaultValue: '', description: '')
        string(name: 'PR_REPO_HTML', defaultValue: '', description: '')
        string(name: 'PR_REPO_NAME', defaultValue: '', description: '')
        string(name: 'PR_COMMIT', defaultValue: '', description: '')
        string(name: 'PR_PROJECT', defaultValue: '', description: '')
        string(name: 'PR_STATE', defaultValue: '', description: '')
    }

    triggers {
        GenericTrigger(
            genericVariables: [
                [key: 'PR_BRANCH', value: '$.pullrequest.source.branch.name'],
                [key: 'PR_DESTINATION_BRANCH', value: '$.pullrequest.destination.branch.name'],
                [key: 'PR_REPO_HTML', value: '$.repository.links.self.href'],
                [key: 'PR_REPO_NAME', value: '$.repository.name'],
                [key: 'PR_COMMIT', value: '$.pullrequest.source.commit.hash'],
                [key: 'PR_PROJECT', value: '$.repository.full_name'],
                [key: 'PR_STATE', value: '$.pullrequest.state']
            ],

            tokenCredentialId: 'trigger-token',
            regexpFilterText: '$PR_STATE',
            regexpFilterExpression: 'OPEN'
        )
    }

    environment {
        ORIGINAL_PROJECT_DIR = "${WORKSPACE}/Original" 
        WORKING_DIR = "${WORKSPACE}/PRJob/${PR_BRANCH}"
        JOB_REPO = "${PR_REPO_HTML}"
        BITBUCKET_ACCESS_TOKEN = credentials('bitbucket-access-token')
        JENKINS_API_KEY = credentials('jenkins-api-key')
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
                script {
                    util = load("${WORKSPACE}/groovy/pipelineUtil.groovy")

                    echo "Sending \'In Progress\' status to Bitbucket..."
                    env.COMMIT_HASH = util.getFullCommitHash(WORKSPACE, PR_COMMIT)
                    util.sendBuildStatus(WORKSPACE, "INPROGRESS", COMMIT_HASH)
                    env.TICKET_NUMBER = util.parseTicketNumber(PR_BRANCH)
                    env.FOLDER_NAME = "${JOB_NAME}".split('/').first()
                }

                script {
                    echo "Directory Checking if it original project exists"
                    if (!fileExists("${ORIGINAL_PROJECT_DIR}")) {
                        echo "Cloning repository..."
                        sh "git clone ${REPO_SSH} \"${ORIGINAL_PROJECT_DIR}\""
                        
                    } else { // if ORIGINAL_PROJECT_DIR exist
                        if (fileExists("${ORIGINAL_PROJECT_DIR}/.git")) {
                            sh "rm -f '${ORIGINAL_PROJECT_DIR}/.git/index.lock'"

                            echo "Fetching latest changes..."
                            dir ("${ORIGINAL_PROJECT_DIR}") {                            
                            sh "git fetch origin"
                            sh "git reset --hard origin/${PR_BRANCH}"
                            }
                        } else{
                            echo "Cleaning workspace..."
                            sh "rm -rf '${ORIGINAL_PROJECT_DIR}'"
                            echo "Cloning repository..."
                            sh "git clone ${REPO_SSH} \"${ORIGINAL_PROJECT_DIR}\""   
                        }
                    }
                }

                dir ("${ORIGINAL_PROJECT_DIR}") {
                    script {                                           
                        echo "Checking if branch is up to date..."
                        if (util.isBranchUpToDate(DESTINATION_BRANCH) == 0) {
                            echo "Branch is up to date."
                        }
                        else {
                            echo "Branch needs to be updated. Merging destination branch into main..."
                        
                            if (util.tryMerge(DESTINATION_BRANCH) == 0) {
                                echo "Merge successful."
                            }
                            else {
                                sh "git merge --abort"
                                env.FAILURE_REASON = "Merge errors found. Merge aborted."
                                error(env.FAILURE_REASON)
                            }
                        }
                    }
                }   

                echo "Identifying Unity version..."
                script {
                    env.UNITY_EXECUTABLE = util.getUnityExecutable(WORKSPACE, ORIGINAL_PROJECT_DIR)
                }
                    
                echo "Running Unity in batch mode to setup initial files..."
                dir ("${ORIGINAL_PROJECT_DIR}") {
                    script {
                        sh "git checkout ${PR_BRANCH}"
                        def logFile = "${ORIGINAL_PROJECT_DIR}/batch_mode_execution.log"
                        def flags = "-batchmode -nographics -projectPath \"${ORIGINAL_PROJECT_DIR}\" -logFile \"${logFile}\" -quit"
                        
                        echo "Flags set to: ${flags}"
                        
                        // Execute Unity in batch mode
                        def exitCode = sh(script: """\"${env.UNITY_EXECUTABLE}\" ${flags}""", returnStatus: true)
                        
                        // Handle exit code
                        if (exitCode != 0) {
                            sh "exit ${exitCode}"
                        }
                    }
                }
                echo "Copying original project to working directory..."
                dir ("${ORIGINAL_PROJECT_DIR}") {
                    script {
                        sh "git checkout ${PR_BRANCH}"
                        def src = "${ORIGINAL_PROJECT_DIR}/"
                        def dst = "${WORKING_DIR}/"
                        
                        // Use `cp` command with correct syntax for directories
                        sh "cp -r \"${src}\" \"${dst}\""
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
                    sh "mkdir -p test_results/EditMode-report"
                    sh "mkdir -p coverage_results"
                    script {
                        util.runUnityTests(UNITY_EXECUTABLE, WORKING_DIR, editMode, true, false)

                        // For some reason, Jenkins doesn't always want to wait until the test log is finished being written to.
                        // If it doesn't wait, then the convertTestResultsToHtml function will always fail,
                        // because the file is currently open elsewhere.
                        waitUntil {
                            def fileAvailable = util.checkIfFileIsLocked("${WORKING_DIR}/test_results/EditMode-tests.log")

                            fileAvailable == 0
                        }

                        util.convertTestResultsToHtml(WORKING_DIR, editMode)
                        util.publishTestResultsHtmlToWebServer(FOLDER_NAME, TICKET_NUMBER, "${WORKING_DIR}/test_results/${editMode}-report", editMode)

                        echo "Sending EditMode test results to Bitbucket..."
                        util.sendTestReport(WORKSPACE, WORKING_DIR, COMMIT_HASH, editMode)
                    }
                }
            }
        }
        // Runs the project's PlayMode tests, and then generates a code coverage report.
        // PlayMode tests need to be run once in the editor to generate the overall coverage report.
        stage('PlayMode Tests in Editor') {
            steps {
                echo "Running PlayMode tests in Editor environment..."
                dir ("${WORKING_DIR}") {
                    sh "mkdir -p test_results/PlayMode-report"
                    retry (5) {
                        script {
                            util.runUnityTests(UNITY_EXECUTABLE, WORKING_DIR, playMode, true, false)

                            waitUntil {
                                def fileAvailable = util.checkIfFileIsLocked("${WORKING_DIR}/test_results/PlayMode-tests.log")
                                fileAvailable == 0
                            }   

                            util.convertTestResultsToHtml(WORKING_DIR, playMode)
                            util.publishTestResultsHtmlToWebServer(FOLDER_NAME, TICKET_NUMBER, "${WORKING_DIR}/test_results/${playMode}-report", playMode)

                            echo "Sending PlayMode test results to Bitbucket..."
                            util.sendTestReport(WORKSPACE, WORKING_DIR, COMMIT_HASH, playMode)
                        }
                    }
                }
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
                    -buildTarget WebGL \
                    -logFile \"${WORKING_DIR}/coverage_results/coverage_report.log\" \
                    -projectPath . \
                    -debugCodeOptimization \
                    -enableCodeCoverage \
                    -coverageResultsPath \"${WORKING_DIR}/coverage_results\" \
                    -coverageOptions \"generateHtmlReport;generateHtmlReportHistory;generateBadgeReport;generateAdditionalMetrics;useProjectSettings\" \
                    -quit"""

                    script {
                        util.publishTestResultsHtmlToWebServer(FOLDER_NAME, TICKET_NUMBER, "${WORKING_DIR}/coverage_results/Report", "CodeCoverage")

                        echo "Sending code coverage report to Bitbucket..."
                        util.sendCoverageReport(WORKSPACE, WORKING_DIR, COMMIT_HASH)
                    }   
                }
            }
        }
        //Builds the project and saves it.
        stage('Build Project') {
            steps {
                echo "Building Unity project..."
                sh "mkdir -p \"${WORKING_DIR}/Assets/Editor/\"" //The following line assumed this folder exists in every project, adding check to ensure it does.
                sh "cp Builder.cs \"${WORKING_DIR}/Assets/Editor/\""

                retry (5) {
                    script {
                        util.buildProject(WORKING_DIR, UNITY_EXECUTABLE)
                    }
                }
            }
        }
    }

    // When the pipeline finishes, sends the build status to Bitbucket.
    post {
        success {
            script {
                util.postBuild("SUCCESSFUL")
            }
        }
        failure {
            script {
                util.postBuild("FAILED")
            }
        }
        aborted {
            script {
                util.postBuild("STOPPED")
            }
        }
    }
}
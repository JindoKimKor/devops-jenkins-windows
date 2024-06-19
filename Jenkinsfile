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
        PROJECT_DIR = "${WORKSPACE}/Unity_Project" 
        REPORT_DIR = "${WORKSPACE}/PRJob/${PR_BRANCH}"
        JOB_REPO = "${PR_REPO_HTML}"
        BITBUCKET_ACCESS_TOKEN = credentials('bitbucket-access-token')
        JENKINS_API_KEY = credentials('jenkins-api-key')

        // Soft-Version of Memory Settings: This configuration moderately increases memory block sizes and bucket counts to reduce CPU usage without significantly increasing RAM consumption. It is suitable for scenarios where a balanced performance is needed without dramatically impacting RAM usage.
        // MEMORY_SETTING = """
        // -memorysetup-bucket-allocator-granularity=32 \
        // -memorysetup-bucket-allocator-bucket-count=16 \
        // -memorysetup-bucket-allocator-block-size=67108864 \
        // -memorysetup-bucket-allocator-block-count=16 \
        // -memorysetup-main-allocator-block-size=33554432 \
        // -memorysetup-thread-allocator-block-size=33554432 \
        // -memorysetup-gfx-main-allocator-block-size=33554432 \
        // -memorysetup-gfx-thread-allocator-block-size=33554432 \
        // -memorysetup-cache-allocator-block-size=8388608 \
        // -memorysetup-typetree-allocator-block-size=4194304 \
        // -memorysetup-profiler-bucket-allocator-granularity=32 \
        // -memorysetup-profiler-bucket-allocator-bucket-count=16 \
        // -memorysetup-profiler-bucket-allocator-block-size=67108864 \
        // -memorysetup-profiler-bucket-allocator-block-count=16 \
        // -memorysetup-profiler-allocator-block-size=33554432 \
        // -memorysetup-profiler-editor-allocator-block-size=2097152 \
        // -memorysetup-temp-allocator-size-main=33554432 \
        // -memorysetup-job-temp-allocator-block-size=4194304 \
        // -memorysetup-job-temp-allocator-block-size-background=2097152 \
        // -memorysetup-job-temp-allocator-reduction-small-platforms=524288 \
        // -memorysetup-allocator-temp-initial-block-size-main=524288 \
        // -memorysetup-allocator-temp-initial-block-size-worker=524288 \
        // -memorysetup-temp-allocator-size-background-worker=65536 \
        // -memorysetup-temp-allocator-size-job-worker=524288 \
        // -memorysetup-temp-allocator-size-preload-manager=67108864 \
        // -memorysetup-temp-allocator-size-nav-mesh-worker=131072 \
        // -memorysetup-temp-allocator-size-audio-worker=131072 \
        // -memorysetup-temp-allocator-size-cloud-worker=65536 \
        // -memorysetup-temp-allocator-size-gi-baking-worker=524288 \
        // -memorysetup-temp-allocator-size-gfx=524288
        // """.trim().replaceAll("\n", " ")

        // Enhanced-Version of Memory Settings: This configuration aggressively increases memory block sizes and bucket counts to minimize CPU usage as much as possible, resulting in a significant increase in RAM consumption. It is ideal for high-performance scenarios where reducing CPU overhead is critical, and ample RAM is available.
        MEMORY_SETTING = """
        -memorysetup-bucket-allocator-granularity=64 \
        -memorysetup-bucket-allocator-bucket-count=32 \
        -memorysetup-bucket-allocator-block-size=134217728 \
        -memorysetup-bucket-allocator-block-count=32 \
        -memorysetup-main-allocator-block-size=67108864 \
        -memorysetup-thread-allocator-block-size=67108864 \
        -memorysetup-gfx-main-allocator-block-size=67108864 \
        -memorysetup-gfx-thread-allocator-block-size=67108864 \
        -memorysetup-cache-allocator-block-size=16777216 \
        -memorysetup-typetree-allocator-block-size=8388608 \
        -memorysetup-profiler-bucket-allocator-granularity=64 \
        -memorysetup-profiler-bucket-allocator-bucket-count=32 \
        -memorysetup-profiler-bucket-allocator-block-size=134217728 \
        -memorysetup-profiler-bucket-allocator-block-count=32 \
        -memorysetup-profiler-allocator-block-size=67108864 \
        -memorysetup-profiler-editor-allocator-block-size=4194304 \
        -memorysetup-temp-allocator-size-main=67108864 \
        -memorysetup-job-temp-allocator-block-size=8388608 \
        -memorysetup-job-temp-allocator-block-size-background=4194304 \
        -memorysetup-job-temp-allocator-reduction-small-platforms=1048576 \
        -memorysetup-allocator-temp-initial-block-size-main=1048576 \
        -memorysetup-allocator-temp-initial-block-size-worker=1048576 \
        -memorysetup-temp-allocator-size-background-worker=131072 \
        -memorysetup-temp-allocator-size-job-worker=1048576 \
        -memorysetup-temp-allocator-size-preload-manager=134217728 \
        -memorysetup-temp-allocator-size-nav-mesh-worker=262144 \
        -memorysetup-temp-allocator-size-audio-worker=262144 \
        -memorysetup-temp-allocator-size-cloud-worker=131072 \
        -memorysetup-temp-allocator-size-gi-baking-worker=1048576 \
        -memorysetup-temp-allocator-size-gfx=1048576
        """.trim().replaceAll("\n", " ")
    }

    stages {
        // Prepare Workspace: Environment Setup, Workspace Preparation(Branch Management), Unity Setup, Initial running the project on Unity Editor
        stage('Prepare Workspace') {
            // Environment Setup
            environment {
                REPO_SSH = "git@bitbucket.org:${PR_PROJECT}.git"
                DESTINATION_BRANCH = "${PR_DESTINATION_BRANCH}"
            }
            steps {
                //send 'In Progress' status to Bitbucket
                script {
                    util = load("${WORKSPACE}/groovy/pipelineUtil.groovy")
                    echo "Sending \'In Progress\' status to Bitbucket..."
                    env.COMMIT_HASH = util.getFullCommitHash(WORKSPACE, PR_COMMIT)
                    util.sendBuildStatus(WORKSPACE, "INPROGRESS", COMMIT_HASH)
                    env.TICKET_NUMBER = util.parseTicketNumber(PR_BRANCH)
                    env.FOLDER_NAME = "${JOB_NAME}".split('/').first()
                }
                
                // Workspace Preparation 
                script {
                    // Scenario 1: Project directory does not exist on the VM
                    // Action: Clone the repository to initialize the project directory.
                    echo "Directory Checking if it original project exists"
                    if (!fileExists("${PROJECT_DIR}")) {
                        echo "Cloning repository..."
                        sh "git clone ${REPO_SSH} \"${PROJECT_DIR}\""
                        
                    } else {
                        // Scenario 2: Project directory and .git directory exist
                        // Action: Remove any existing git index lock and fetch the latest updates.
                        if (fileExists("${PROJECT_DIR}/.git")) {
                            sh "rm -f '${PROJECT_DIR}/.git/index.lock'"

                            echo "Fetching latest changes..."
                            dir ("${PROJECT_DIR}") {                            
                            sh "git fetch origin"
                            sh "git reset --hard origin/${PR_BRANCH}"
                            }
                        } 
                        // Scenario 3: Project directory exists but .git directory is missing or corrupted
                        // Action: Clean the directory and clone the repository afresh.
                        else { 
                            echo "Cleaning workspace..."
                            sh "rm -rf '${PROJECT_DIR}'"
                            echo "Cloning repository..."
                            sh "git clone ${REPO_SSH} \"${PROJECT_DIR}\""   
                        }
                    }
                }

                dir ("${PROJECT_DIR}") {
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
                // Unity Setup: Identify the version of Unity Editor for the project
                echo "Identifying Unity version..."
                script {
                    env.UNITY_EXECUTABLE = util.getUnityExecutable(WORKSPACE, PROJECT_DIR)
                }

                // Initial running the project on Unity Editor    
                echo "Running Unity in batch mode to setup initial files..."
                dir ("${PROJECT_DIR}") {
                    script {
                        sh "git checkout ${PR_BRANCH}"
                        def logFile = "${PROJECT_DIR}/batch_mode_execution.log"

                        def flags = "-batchmode -nographics -projectPath \"${PROJECT_DIR}\" -logFile \"${logFile}\" -quit ${MEMORY_SETTING}"
                        
                        echo "Flags set to: ${flags}"
                        
                        // Execute Unity in batch mode with memory settings
                        def exitCode = sh(script: """\"${env.UNITY_EXECUTABLE}\" ${flags}""", returnStatus: true)
                        
                        // Handle exit code
                        if (exitCode != 0) {
                            sh "exit ${exitCode}"
                        }
                    }
                }
            }
        }
        // Runs the project's EditMode tests, and then generates a test report and a code coverage report.
        // Sends the test results to Bitbucket once the tests complete.
        // stage('EditMode Tests') {
        //     steps {
        //         dir ("${REPORT_DIR}") {
        //             sh "mkdir -p test_results/EditMode-report"
        //             sh "mkdir -p coverage_results"
        //         }
        //         echo "Running EditMode tests..."
        //         dir ("${PROJECT_DIR}") {
        //             script {
        //                 util.runUnityTests(UNITY_EXECUTABLE, REPORT_DIR, PROJECT_DIR, editMode, true, false)

        //                 // For some reason, Jenkins doesn't always want to wait until the test log is finished being written to.
        //                 // If it doesn't wait, then the convertTestResultsToHtml function will always fail,
        //                 // because the file is currently open elsewhere.
        //                 waitUntil {
        //                     def fileAvailable = util.checkIfFileIsLocked("${REPORT_DIR}/test_results/EditMode-tests.log")

        //                     fileAvailable == 0
        //                 }

        //                 util.convertTestResultsToHtml(REPORT_DIR, editMode)
        //                 util.publishTestResultsHtmlToWebServer(FOLDER_NAME, TICKET_NUMBER, "${REPORT_DIR}/test_results/${editMode}-report", editMode)

        //                 echo "Sending EditMode test results to Bitbucket..."
        //                 util.sendTestReport(WORKSPACE, REPORT_DIR, COMMIT_HASH, editMode)
        //             }
        //         }
        //     }
        // }
        // Runs the project's PlayMode tests, and then generates a code coverage report.
        // PlayMode tests need to be run once in the editor to generate the overall coverage report.
        // stage('PlayMode Tests in Editor') {
        //     steps {
        //         dir ("${REPORT_DIR}") {
        //             sh "mkdir -p test_results/PlayMode-report"
        //         }
        //         echo "Running PlayMode tests in Editor environment..."
        //         dir ("${PROJECT_DIR}") {
        //             retry (5) {
        //                 script {
        //                     util.runUnityTests(UNITY_EXECUTABLE, REPORT_DIR, PROJECT_DIR, playMode, true, false)

        //                     waitUntil {
        //                         def fileAvailable = util.checkIfFileIsLocked("${REPORT_DIR}/test_results/PlayMode-tests.log")
        //                         fileAvailable == 0
        //                     }   

        //                     util.convertTestResultsToHtml(REPORT_DIR, playMode)
        //                     util.publishTestResultsHtmlToWebServer(FOLDER_NAME, TICKET_NUMBER, "${REPORT_DIR}/test_results/${playMode}-report", playMode)

        //                     echo "Sending PlayMode test results to Bitbucket..."
        //                     util.sendTestReport(WORKSPACE, REPORT_DIR, COMMIT_HASH, playMode)
        //                 }
        //             }
        //         }
        //     }
        // }
        // Merges the two coverage reports from the EditMode and PlayMode (editor) reports into one.
        // Then sends a coverage report to Bitbucket.
        // stage('Generate Code Coverage Report') {
        //     steps {
        //         echo "Generating code coverage report..."
        //         dir("${PROJECT_DIR}") {
        //             sh """\"${UNITY_EXECUTABLE}\" \
        //             -batchmode \
        //             ${MEMORY_SETTING} \
        //             -nographics \
        //             -logFile \"${REPORT_DIR}/coverage_results/coverage_report.log\" \
        //             -projectPath . \
        //             -debugCodeOptimization \
        //             -enableCodeCoverage \
        //             -coverageResultsPath \"${REPORT_DIR}/coverage_results\" \
        //             -coverageOptions \"generateHtmlReport;generateHtmlReportHistory;generateBadgeReport;generateAdditionalMetrics;useProjectSettings\" \
        //             -quit"""

        //             script {
        //                 util.publishTestResultsHtmlToWebServer(FOLDER_NAME, TICKET_NUMBER, "${REPORT_DIR}/coverage_results/Report", "CodeCoverage")

        //                 echo "Sending code coverage report to Bitbucket..."
        //                 util.sendCoverageReport(WORKSPACE, REPORT_DIR, COMMIT_HASH)
        //             }   
        //         }
        //     }
        // }
        //Builds the project and saves it.
        stage('Build Project') {
            steps {
                echo "Building Unity project..."
                sh "mkdir -p \"${PROJECT_DIR}/Assets/Editor/\"" //The following line assumed this folder exists in every project, adding check to ensure it does.
                sh "cp Builder.cs \"${PROJECT_DIR}/Assets/Editor/\""

                retry (5) {
                    script {
                        util.buildProject(REPORT_DIR, PROJECT_DIR, UNITY_EXECUTABLE)
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
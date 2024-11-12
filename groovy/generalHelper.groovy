// Clones or updates a repository in the specified directory.
// If the directory exists and is a valid git repository, it fetches the latest changes.
// Otherwise, it clones the repository from scratch.
def cloneOrUpdateRepo(String projectDir, String repoSsh, String branch) {
    if (!projectDir || !repoSsh || !branch) {
        error "Missing required parameters for cloneOrUpdateRepo()"
    }

    echo "Checking if the project directory exists..."
    if (!new File(projectDir).exists()) {
        echo "Cloning repository..."
        sh "git clone ${repoSsh} \"${projectDir}\""
    } else {
        if (new File("${projectDir}/.git").exists()) {
            echo "Project already exists. Fetching latest changes..."
            dir(projectDir) {
                // Remove lock file if it exists
                sh "rm -f '.git/index.lock'"
                sh "git fetch origin"

                // Check if the branch exists remotely
                def branchExists = sh(script: "git show-ref --verify --quiet refs/remotes/origin/${branch}", returnStatus: true) == 0
                if (!branchExists) {
                    error "Branch ${branch} does not exist in the remote repository."
                }

                // Reset and checkout the branch
                sh "git checkout ${branch}"
                sh "git reset --hard origin/${branch}"
            }
        } else {
            echo "Invalid git repository. Cleaning up and cloning afresh..."
            sh "rm -rf '${projectDir}'"
            sh "git clone ${repoSsh} \"${projectDir}\""
        }
    }
}

def getDefaultBranch() {
    // Use git remote show to get the default branch
    def defaultBranch = sh(script: "git remote show origin | grep 'HEAD branch' | awk '{print \$NF}'", returnStdout: true).trim()
    if (!defaultBranch) {
        error "Failed to determine the default branch from the remote repository."
    }
    echo "Default branch is determined to be '${defaultBranch}'."
    return defaultBranch
}



def initializeEnvironment(String workspace, String commitHash, String prBranch, String projectDir) {
    echo "Sending 'In Progress' status to Bitbucket..."
    sendBuildStatus(workspace, "INPROGRESS", commitHash)
    env.TICKET_NUMBER = parseTicketNumber(prBranch)
    env.FOLDER_NAME = "${JOB_NAME}".split('/').first()
}

def checkoutBranch(String projectDir, String prBranch) {
    dir(projectDir) {
        echo "Checking out branch ${prBranch}..."
        def branchExists = sh(script: "git show-ref --verify --quiet refs/heads/${prBranch} || git show-ref --verify --quiet refs/remotes/origin/${prBranch}", returnStatus: true) == 0
        if (!branchExists) {
            error "Branch ${prBranch} does not exist locally or remotely."
        }
        sh "git checkout ${prBranch}"
    }
}


// This will try to merge the branch, throwing an error if theres an error.
def mergeBranchIfNeeded() {
    def destinationBranch = getDefaultBranch()
    try {
        echo "Fetching latest changes from origin..."
        sh "git fetch origin"

        // Check if the destination branch exists remotely
        def branchExists = sh(script: "git show-ref --verify --quiet refs/remotes/origin/${destinationBranch}", returnStatus: true) == 0
        if (!branchExists) {
            error "Branch ${destinationBranch} does not exist in the remote repository."
        }

        // Check if the branch is up-to-date
        echo "Checking if branch is up-to-date with ${destinationBranch}..."
        if (isBranchUpToDate(destinationBranch) == 0) {
            echo "Branch is up-to-date with ${destinationBranch}."
            return
        }

        echo "Branch is not up-to-date. Attempting to merge ${destinationBranch}..."
        if (tryMerge(destinationBranch) == 0) {
            echo "Merge completed successfully."
        } else {
            echo "Merge conflicts detected. Aborting the merge."
            sh "git merge --abort || true" // Safely abort merge if one is in progress
            error("Merge process failed.")
        }
    } catch (Exception e) {
        echo "An error occurred during the merge process: ${e.getMessage()}"
        error("Merge process failed.")
    }
}

def validateCommitHashes(String workspace, String projectDir, String prCommit, String testRunFlag) {
    def commitHash = getFullCommitHash(workspace, prCommit)
    dir(projectDir) {
        def currentHash = getCurrentCommitHash()
        echo "Current Commit Hash: ${currentHash}, Target Commit Hash: ${commitHash}"
        if (isEqualCommitHash(currentHash, commitHash) && !testRunFlag.equals("Y")) {
            def message = "No commits updated. Exiting the pipeline..."
            echo message
            currentBuild.result = 'ABORTED'
            error(message)
        }
    }
    return commitHash // Return the fetched commit hash for further use
}


// Checks whether a branch is up to date with the destination branch by seeing if it is an ancestor of the destination.
// This is in its own method to avoid pipeline failure if the branch needs updating.
def isBranchUpToDate(destinationBranch) {
    return sh (script: "git merge-base --is-ancestor origin/${destinationBranch} @", returnStatus: true)
}

// Attempts to merge the destination branch into the current branch.
// This is in its own method to avoid automatic pipeline failure if there are merge errors. We want to alert the user of the merge errors.
def tryMerge(String destinationBranch) {
    echo "Attempting to merge origin/${destinationBranch}..."
    return sh(script: "git merge origin/${destinationBranch}", returnStatus: true)
}


// Retrieves the full commit hash from Bitbucket Cloud API, since the webhook only gives us the short version.
def getFullCommitHash(String workspace, String shortCommit) {
    def fullHash = sh(script: "python '${workspace}/python/get_bitbucket_commit_hash.py' ${shortCommit}", returnStdout: true).trim()
    if (!fullHash) {
        error "Failed to retrieve the full commit hash for ${shortCommit}."
    }
    return fullHash
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
def sendBuildStatus(workspace, state, commitHash, deployment = false, javascript = false) {
    try {
        def pythonCommand = "python '${workspace}/python/send_bitbucket_build_status.py' '${commitHash}' '${state}'"
        if (deployment) {
            pythonCommand += " -d"
        }
        if (javascript) {
            pythonCommand += " -js"
        }
        echo "Executing build status update: ${pythonCommand}"
        def exitCode = sh(script: pythonCommand, returnStatus: true)
        if (exitCode != 0) {
            echo "Build status update script failed with exit code: ${exitCode}."
        }
    } catch (Exception e) {
        echo "An error occurred while updating the build status: ${e.getMessage()}"
    }
}

def checkIfFileIsLocked(filePath) {
    return bat (script: """2>nul (
            >>\"${filePath}\" (call )
        ) && (exit 0) || (exit 1)""", returnStatus: true)
}

// Parses a Jira ticket number from the branch name.
def parseTicketNumber(branchName) {
    def patternMatches = branchName =~ /[A-Za-z]+-[0-9]+/
    
    if (patternMatches) {
        return patternMatches[0]
    }
}

// Publishes a test result HTML file to the VARLab's remote web server for hosting.
def copyResultsHtmlFileToWebServer(remoteProjectFolderName, ticketNumber, reportDir, reportType, buildNumber = null) {
    echo "Attempting to publish results to web server"
    def destinationDir = buildNumber ? "/var/www/html/${remoteProjectFolderName}/Reports/${ticketNumber}/Build-${buildNumber}" : "/var/www/html/${remoteProjectFolderName}/Reports/${ticketNumber}"

     sh """ssh vconadmin@dlx-webhost.canadacentral.cloudapp.azure.com \
    \"mkdir -p ${destinationDir} \
    && sudo chown vconadmin:vconadmin ${destinationDir} \
    && sudo chmod 755 /var/www/html/${remoteProjectFolderName} \
    && sudo chmod -R 755 /var/www/html/${remoteProjectFolderName}/Reports \""""

    sh "scp -i C:/Users/ci-catherine/.ssh/vconkey1.pem -rp \"${reportDir}/*\" \
    \"vconadmin@dlx-webhost.canadacentral.cloudapp.azure.com:${destinationDir}\""
}

// Publishes a test result HTML file to the VARLab's remote web server for hosting.
def publishTestResultsHtmlToWebServer(remoteProjectFolderName, ticketNumber, reportDir, reportType, buildNumber = null) {
    echo "Attempting to publish results to web server"
    def destinationDir = buildNumber ? "/var/www/html/${remoteProjectFolderName}/Reports/${ticketNumber}/Build-${buildNumber}/${reportType}-report" : "/var/www/html/${remoteProjectFolderName}/Reports/${ticketNumber}/${reportType}-report"

     sh """ssh vconadmin@dlx-webhost.canadacentral.cloudapp.azure.com \
    \"mkdir -p ${destinationDir} \
    && sudo chown vconadmin:vconadmin ${destinationDir} \
    && sudo chmod 755 /var/www/html/${remoteProjectFolderName} \
    && sudo chmod -R 755 /var/www/html/${remoteProjectFolderName}/Reports \""""

    sh "scp -i C:/Users/ci-catherine/.ssh/vconkey1.pem -rp \"${reportDir}/*\" \
    \"vconadmin@dlx-webhost.canadacentral.cloudapp.azure.com:${destinationDir}\""
}

// Deletes a branch's reports from the web server after it has been merged.
def cleanMergedBranchReportsFromWebServer(remoteProjectFolderName, ticketNumber) {
    sh """ssh vconadmin@dlx-webhost.canadacentral.cloudapp.azure.com \
    \"sudo rm -r -f /var/www/html/${remoteProjectFolderName}/Reports/${ticketNumber}\""""
}

return this
#!groovy

properties([disableConcurrentBuilds()])

pipeline {
    agent {
        label any
    }
    options {
        buildDiscarder(logRotator(numToKeepStr: '10', artifactNumToKeepStr: '10'))
        timestamps()
    }
    stages {
        stage("Stage 1") {
            steps {
                sh 'uname -a'
            }
        }

    }
}
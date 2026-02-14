#!groovy

properties([disableConcurrentBuilds()])

pipeline {
    agent any

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
    stages {
        stage("Stage 2") {
            steps {
                sh 'lscpu'
            }
        }

    }
}
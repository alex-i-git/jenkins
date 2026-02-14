#!groovy

properties([disableConcurrentBuilds()])

pipeline {
    agent any

    options {
        buildDiscarder(logRotator(numToKeepStr: '10', artifactNumToKeepStr: '10'))
        timestamps()
    }
    stages {
        stage("create docker image") {
            steps {
                echo "Building docker image"
                dir ('helm-chart/docker/cli') {
                    sh 'docker build .'
                }
            }
        }

    }
    
}
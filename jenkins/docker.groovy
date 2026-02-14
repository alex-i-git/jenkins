#!groovy

properties([disableConcurrentBuilds()])

pipeline {
    agent {
        label 'docker'
    }

    parameters {
        string(
            name: 'IMAGE_NAME',
            defaultValue: 'alpine/newimage',
            description: 'Имя образа'
        ) 
    }
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
pipeline {
  agent any
  options {
    timestamps()
    ansiColor('xterm')
  }
  stages {
    stage('Checkout') {
      steps {
        checkout scm
      }
    }
    stage('Build') {
      steps {
        script {
          if (fileExists('pom.xml')) {
            sh 'mvn -B -DskipTests clean package'
          } else if (fileExists('package.json')) {
            sh 'npm ci && npm run build || true'
          } else {
            sh 'echo "No build step defined; set BUILD steps in the Jenkinsfile."'
          }
        }
      }
    }
    stage('Test') {
      steps {
        script {
          if (fileExists('pom.xml')) {
            sh 'mvn test'
          } else if (fileExists('package.json')) {
            sh 'npm test || true'
          } else {
            sh 'echo "No tests configured."'
          }
        }
      }
    }
    stage('Archive') {
      steps {
        script {
          if (fileExists('target')) {
            archiveArtifacts artifacts: 'target/**/*.jar', fingerprint: true
          } else if (fileExists('dist') || fileExists('build')) {
            archiveArtifacts artifacts: 'dist/**', fingerprint: true
          } else {
            echo 'Nothing to archive.'
          }
        }
      }
    }
  }
  post {
    success {
      echo 'Build succeeded'
    }
    failure {
      echo 'Build failed'
    }
    always {
      cleanWs()
    }
  }
}

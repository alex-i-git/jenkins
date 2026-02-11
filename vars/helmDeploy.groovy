def call(Map config = [:]) {
    pipeline {
        agent { label config.agent ?: 'k3s-helm' }
        
        parameters {
            string(name: 'ENVIRONMENT', defaultValue: config.environment ?: 'dev')
            string(name: 'RELEASE_NAME', defaultValue: config.releaseName ?: 'myapp')
            booleanParam(name: 'DRY_RUN', defaultValue: false)
        }
        
        stages {
            stage('Deploy') {
                steps {
                    deployHelm(
                        environment: params.ENVIRONMENT,
                        releaseName: params.RELEASE_NAME,
                        chartPath: config.chartPath ?: 'helm-chart',
                        namespace: config.namespace ?: "${params.ENVIRONMENT}-namespace",
                        dryRun: params.DRY_RUN
                    )
                }
            }
        }
    }
}
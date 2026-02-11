@Library('shared-library') _ // Опционально, для shared libraries

// Pipeline с параметрами
pipeline {
    agent {
        label 'k3s-helm-agent' // Агент с установленными helm и kubectl
    }
    
    // Параметры сборки
    parameters {
        choice(
            name: 'ENVIRONMENT',
            choices: ['dev', 'staging', 'production'],
            description: 'Целевое окружение для деплоя'
        )
        string(
            name: 'RELEASE_NAME',
            defaultValue: 'myapp',
            description: 'Имя Helm релиза'
        )
        string(
            name: 'NAMESPACE',
            defaultValue: '',
            description: 'Kubernetes namespace (оставьте пустым для автоопределения)'
        )
        booleanParam(
            name: 'DRY_RUN',
            defaultValue: false,
            description: 'Режим проверки (dry run)'
        )
        booleanParam(
            name: 'DEBUG_MODE',
            defaultValue: false,
            description: 'Режим отладки Helm'
        )
    }
    
    // Переменные окружения
    environment {
        // K3S конфигурация
        KUBECONFIG = credentials('k3s-kubeconfig')
        HELM_EXPERIMENTAL_OCI = '1'
        
        // Определяем namespace для каждого окружения
        NAMESPACE = params.NAMESPACE ?: 
            sh(script: "echo ${params.ENVIRONMENT} | sed 's/production/prod/'", returnStdout: true).trim()
        
        // Версия чарта
        CHART_VERSION = readMavenPom().getVersion() ?: sh(script: "git describe --tags", returnStdout: true).trim()
        
        // Docker registry
        DOCKER_REGISTRY = 'registry.example.com'
        DOCKER_CREDENTIALS = credentials('docker-hub')
        
        // Slack уведомления
        SLACK_CHANNEL = "#deployments-${params.ENVIRONMENT}"
    }
    
    options {
        // Общие настройки пайплайна
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '10'))
        disableConcurrentBuilds()
        timeout(time: 30, unit: 'MINUTES')
        retry(2)
    }
    
    stages {
        // Этап 1: Проверка окружения
        stage('Check Environment') {
            when {
                expression { params.ENVIRONMENT != null }
            }
            steps {
                script {
                    sh '''
                        echo "=== Проверка окружения K3s ==="
                        kubectl cluster-info
                        kubectl get nodes
                        
                        echo "=== Проверка Helm ==="
                        helm version
                        helm env
                        
                        echo "=== Проверка namespace ==="
                        kubectl get namespace ${NAMESPACE} || kubectl create namespace ${NAMESPACE}
                        kubectl label namespace ${NAMESPACE} environment=${ENVIRONMENT} --overwrite
                    '''
                }
            }
        }
        
        // Этап 2: Проверка и обновление зависимостей
        stage('Helm Dependencies') {
            when {
                expression { fileExists('helm-chart/Chart.yaml') }
            }
            steps {
                dir('helm-chart') {
                    script {
                        sh '''
                            echo "=== Обновление зависимостей ==="
                            helm dependency update
                            helm dependency build
                            
                            echo "=== Проверка линтером ==="
                            helm lint .
                        '''
                    }
                }
            }
        }
        
        // Этап 3: Сборка Docker образа (опционально)
        stage('Build Docker Image') {
            when {
                expression { 
                    params.ENVIRONMENT == 'production' || 
                    params.ENVIRONMENT == 'staging' 
                }
            }
            steps {
                script {
                    docker.withRegistry("https://${DOCKER_REGISTRY}", 'docker-hub') {
                        def imageName = "${DOCKER_REGISTRY}/${JOB_BASE_NAME}:${env.BUILD_ID}"
                        def imageLatest = "${DOCKER_REGISTRY}/${JOB_BASE_NAME}:latest"
                        
                        sh """
                            echo "=== Сборка Docker образа ==="
                            docker build -t ${imageName} -t ${imageLatest} .
                        """
                        
                        if (!params.DRY_RUN) {
                            sh """
                                echo "=== Публикация Docker образа ==="
                                docker push ${imageName}
                                docker push ${imageLatest}
                            """
                        }
                    }
                }
            }
        }
        
        // Этап 4: Генерация значений для Helm
        stage('Prepare Values') {
            steps {
                script {
                    // Генерируем values файл в зависимости от окружения
                    def valuesFile = "values-${params.ENVIRONMENT}.yaml"
                    def customValues = [
                        image: [
                            repository: "${DOCKER_REGISTRY}/${JOB_BASE_NAME}",
                            tag: "${env.BUILD_ID}",
                            pullPolicy: params.ENVIRONMENT == 'production' ? 'Always' : 'IfNotPresent'
                        ],
                        replicaCount: params.ENVIRONMENT == 'production' ? 3 : 1,
                        resources: [
                            limits: params.ENVIRONMENT == 'production' ? 
                                [cpu: '1000m', memory: '1Gi'] : 
                                [cpu: '500m', memory: '512Mi'],
                            requests: [
                                cpu: '100m',
                                memory: '128Mi'
                            ]
                        ],
                        ingress: [
                            enabled: true,
                            host: "${params.ENVIRONMENT}.app.example.com",
                            annotations: [
                                'kubernetes.io/ingress.class': 'traefik' // для k3s
                            ]
                        ],
                        env: [
                            name: params.ENVIRONMENT,
                            version: env.BUILD_ID
                        ]
                    ]
                    
                    writeYaml file: valuesFile, data: customValues
                    stash includes: valuesFile, name: 'values'
                }
            }
        }
        
        // Этап 5: Dry Run (проверка)
        stage('Helm Dry Run') {
            when {
                expression { params.DRY_RUN || params.ENVIRONMENT == 'production' }
            }
            steps {
                dir('helm-chart') {
                    script {
                        sh """
                            echo "=== Dry Run: Проверка установки ==="
                            helm upgrade --install ${params.RELEASE_NAME} . \
                                --namespace ${NAMESPACE} \
                                -f ../values-${params.ENVIRONMENT}.yaml \
                                --set version=${env.BUILD_ID} \
                                --set buildDate=$(date -u +'%Y-%m-%dT%H:%M:%SZ') \
                                --dry-run \
                                --debug \
                                --timeout 5m
                        """
                    }
                }
            }
        }
        
        // Этап 6: Деплой в K3s
        stage('Deploy to K3s') {
            when {
                expression { !params.DRY_RUN }
            }
            steps {
                dir('helm-chart') {
                    script {
                        // Базовые аргументы Helm
                        def helmArgs = [
                            "upgrade", "--install", params.RELEASE_NAME,
                            ".",
                            "--namespace", NAMESPACE,
                            "--create-namespace",
                            "--values", "../values-${params.ENVIRONMENT}.yaml",
                            "--set", "version=${env.BUILD_ID}",
                            "--set", "buildDate=$(date -u +'%Y-%m-%dT%H:%M:%SZ')",
                            "--set", "gitCommit=${env.GIT_COMMIT}",
                            "--wait",
                            "--timeout", "10m",
                            "--atomic"  // Автоматический rollback при ошибке
                        ]
                        
                        // Добавляем опции в зависимости от окружения
                        if (params.ENVIRONMENT == 'production') {
                            helmArgs.addAll([
                                "--reuse-values",
                                "--history-max", "10"
                            ])
                        } else {
                            helmArgs.addAll([
                                "--reset-values",
                                "--history-max", "5"
                            ])
                        }
                        
                        if (params.DEBUG_MODE) {
                            helmArgs.add("--debug")
                        }
                        
                        // Выполняем Helm команду
                        def helmCommand = "helm " + helmArgs.join(' ')
                        sh(helmCommand)
                        
                        // Проверка статуса деплоя
                        sh """
                            echo "=== Проверка статуса ==="
                            kubectl get pods -n ${NAMESPACE} -l app.kubernetes.io/instance=${params.RELEASE_NAME}
                            helm status ${params.RELEASE_NAME} -n ${NAMESPACE}
                        """
                    }
                }
            }
        }
        
        // Этап 7: Тестирование релиза
        stage('Helm Test') {
            when {
                expression { 
                    !params.DRY_RUN && 
                    params.ENVIRONMENT != 'production' 
                }
            }
            steps {
                dir('helm-chart') {
                    script {
                        try {
                            sh """
                                echo "=== Запуск Helm тестов ==="
                                helm test ${params.RELEASE_NAME} -n ${NAMESPACE} --logs --timeout 5m
                            """
                        } catch (Exception e) {
                            unstable(message: "Helm тесты не прошли: ${e.message}")
                        }
                    }
                }
            }
        }
        
        // Этап 8: Проверка здоровья приложения
        stage('Health Check') {
            when {
                expression { !params.DRY_RUN }
            }
            steps {
                script {
                    def maxRetries = 10
                    def retryCount = 0
                    def healthy = false
                    
                    while (retryCount < maxRetries && !healthy) {
                        try {
                            sh """
                                echo "=== Проверка готовности приложения (попытка ${retryCount + 1}) ==="
                                kubectl rollout status deployment/${params.RELEASE_NAME} -n ${NAMESPACE} --timeout=30s
                            """
                            healthy = true
                        } catch (Exception e) {
                            retryCount++
                            sleep(time: 5, unit: 'SECONDS')
                        }
                    }
                    
                    if (!healthy) {
                        error "Деплой не прошел проверку здоровья"
                    }
                    
                    // Дополнительная проверка через port-forward
                    sh """
                        echo "=== Проверка HTTP endpoint ==="
                        kubectl run curl-test --image=curlimages/curl -n ${NAMESPACE} --rm -it --restart=Never -- \
                            curl -f http://${params.RELEASE_NAME}:8080/health || true
                    """
                }
            }
        }
    }
    
    post {
        always {
            script {
                // Архивация Helm чарта
                dir('helm-chart') {
                    sh """
                        echo "=== Пакетирование Helm чарта ==="
                        helm package . --version ${CHART_VERSION} --app-version ${env.BUILD_ID}
                        mkdir -p ../charts
                        mv *.tgz ../charts/
                    """
                    archiveArtifacts artifacts: '*.tgz', fingerprint: true
                }
                
                // Сохранение конфигурации релиза
                if (!params.DRY_RUN) {
                    sh """
                        helm get all ${params.RELEASE_NAME} -n ${NAMESPACE} > helm-release-${params.RELEASE_NAME}.txt
                        kubectl get all -n ${NAMESPACE} -l app.kubernetes.io/instance=${params.RELEASE_NAME} -o yaml > k8s-resources.yaml
                    """
                    archiveArtifacts artifacts: 'helm-release-*.txt, k8s-resources.yaml'
                }
            }
            cleanWs(cleanWhenNotBuilt: false, 
                    cleanWhenSuccess: true, 
                    cleanWhenUnstable: true,
                    cleanWhenFailure: false)
        }
        
        success {
            script {
                // Slack уведомление об успехе
                slackSend(
                    channel: env.SLACK_CHANNEL,
                    color: 'good',
                    message: """
                        ✅ Успешный деплой: ${env.JOB_NAME} #${env.BUILD_NUMBER}
                        Окружение: ${params.ENVIRONMENT}
                        Релиз: ${params.RELEASE_NAME}
                        Namespace: ${env.NAMESPACE}
                        Версия: ${env.BUILD_ID}
                        URL: ${env.BUILD_URL}
                    """
                )
            }
            
            echo "✅ Деплой успешно завершен в окружение: ${params.ENVIRONMENT}"
        }
        
        failure {
            script {
                // Slack уведомление об ошибке
                slackSend(
                    channel: env.SLACK_CHANNEL,
                    color: 'danger',
                    message: """
                        ❌ Ошибка деплоя: ${env.JOB_NAME} #${env.BUILD_NUMBER}
                        Окружение: ${params.ENVIRONMENT}
                        Релиз: ${params.RELEASE_NAME}
                        Namespace: ${env.NAMESPACE}
                        Ошибка: ${currentBuild.result}
                        Логи: ${env.BUILD_URL}console
                    """
                )
                
                // Автоматический rollback при ошибке
                if (params.ENVIRONMENT == 'production' && !params.DRY_RUN) {
                    try {
                        sh """
                            echo "=== Автоматический rollback ==="
                            helm rollback ${params.RELEASE_NAME} -n ${NAMESPACE} --wait --timeout 5m
                        """
                    } catch (Exception e) {
                        echo "❌ Rollback также не удался: ${e.message}"
                    }
                }
            }
        }
        
        unstable {
            script {
                slackSend(
                    channel: env.SLACK_CHANNEL,
                    color: 'warning',
                    message: """
                        ⚠️ Деплой нестабилен: ${env.JOB_NAME} #${env.BUILD_NUMBER}
                        Окружение: ${params.ENVIRONMENT}
                        Релиз: ${params.RELEASE_NAME}
                        Namespace: ${env.NAMESPACE}
                        Требуется ручная проверка
                    """
                )
            }
        }
    }
}
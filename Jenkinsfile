pipeline {
    agent any

    options {
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
        disableConcurrentBuilds()
    }

    parameters {
        booleanParam(name: 'RUN_DEP_CHECK', defaultValue: false,
                description: 'Scansione OWASP Dependency-Check di policy-service (lenta: scarica il database NVD)')
    }

    environment {
        IMAGE_PREFIX = 'claimshub'
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        // i due servizi sono indipendenti: test in parallelo
        stage('Build & Test') {
            parallel {

                stage('policy-service (Maven)') {
                    steps {
                        dir('policy-service') {
                            // "sh mvnw" invece di "./mvnw": non dipende dal permesso di esecuzione del file
                            sh 'sh ./mvnw -B test'
                        }
                    }
                    post {
                        always {
                            junit 'policy-service/target/surefire-reports/*.xml'
                            archiveArtifacts artifacts: 'policy-service/target/site/jacoco/**',
                                    allowEmptyArchive: true
                        }
                    }
                }

                stage('claims-service (Gradle)') {
                    steps {
                        dir('claims-service') {
                            sh 'sh ./gradlew --no-daemon test'
                        }
                    }
                    post {
                        always {
                            junit 'claims-service/build/test-results/test/*.xml'
                            archiveArtifacts artifacts: 'claims-service/build/reports/jacoco/**',
                                    allowEmptyArchive: true
                        }
                    }
                }
            }
        }

        // alternativa gratuita a Nexus IQ: cerca vulnerabilità note nelle dipendenze
        stage('Dependency check') {
            when {
                expression { params.RUN_DEP_CHECK }
            }
            steps {
                dir('policy-service') {
                    sh 'sh ./mvnw -B org.owasp:dependency-check-maven:check -DfailBuildOnCVSS=7'
                }
            }
        }

        // costruisce le immagini con i Dockerfile multi-stage (i test sono già passati)
        stage('Docker images') {
            steps {
                sh "docker build -t ${IMAGE_PREFIX}/policy-service:${env.BUILD_NUMBER} -t ${IMAGE_PREFIX}/policy-service:latest policy-service"
                sh "docker build -t ${IMAGE_PREFIX}/claims-service:${env.BUILD_NUMBER} -t ${IMAGE_PREFIX}/claims-service:latest claims-service"
            }
        }
    }

    post {
        success {
            echo "Build ${env.BUILD_NUMBER} completata: immagini ${IMAGE_PREFIX}/*:${env.BUILD_NUMBER}"
        }
        failure {
            echo "Build ${env.BUILD_NUMBER} fallita: guarda lo stage rosso e il suo log"
        }
    }
}

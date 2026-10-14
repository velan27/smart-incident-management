
pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    environment {
        BACKEND_IMAGE = 'akshaygouda646/smartims-backend'
        FRONTEND_IMAGE = 'akshaygouda646/smartims-frontend'
        IMAGE_TAG = 'v1'
        BACKEND_HEALTH_URL = 'http://smartims-backend:8081/actuator/health'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Environment Check') {
            steps {
                sh '''
                    set -e
                    java -version
                    git --version
                    docker --version
                    docker compose version
                    node --version
                    npm --version
                    mvn -version
                '''
            }
        }

        stage('Backend Build') {
            steps {
                dir('backend') {
                    sh 'chmod +x mvnw && ./mvnw clean package -DskipTests'
                }
            }
        }

        stage('SonarQube Analysis') {
            steps {
                dir('backend') {
                    withSonarQubeEnv('Local SonarQube') {
                        sh '''
                            ./mvnw sonar:sonar \
                              -Dsonar.projectKey=smart-incident-management \
                              -Dsonar.projectName="Smart Incident Management Backend"
                        '''
                    }
                }
            }
        }

        stage('Quality Gate') {
            steps {
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        stage('Frontend Build') {
            steps {
                dir('frontend') {
                    sh 'npm ci && npm run build'
                }
            }
        }

        stage('Backend Docker Build') {
            steps {
                sh 'docker build --pull -t "$BACKEND_IMAGE:$IMAGE_TAG" backend'
            }
        }

        stage('Frontend Docker Build') {
            steps {
                sh '''
                    docker build --pull \
                      --build-arg VITE_API_URL=http://localhost:8081 \
                      -t "$FRONTEND_IMAGE:$IMAGE_TAG" frontend
                '''
            }
        }

        stage('Docker Environment') {
            steps {
                sh '''
                    set -e
                    docker info
                    docker compose version
                '''
            }
        }

        stage('Docker Login') {
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: 'dockerhub-pat-v2',
                        usernameVariable: 'DOCKER_USERNAME',
                        passwordVariable: 'DOCKER_PASSWORD'
                    )
                ]) {
                    sh '''
                        set +x
                        printf '%s' "$DOCKER_PASSWORD" |
                          docker login --username "$DOCKER_USERNAME" --password-stdin
                    '''
                }
            }
        }

        stage('Docker Push') {
            steps {
                sh '''
                    docker push "$BACKEND_IMAGE:$IMAGE_TAG"
                    docker push "$FRONTEND_IMAGE:$IMAGE_TAG"
                '''
            }
        }

        stage('Validate Compose Configuration') {
            steps {
                withCredentials([
                    file(
                        credentialsId: 'smartims-env-properties',
                        variable: 'SMARTIMS_ENV_FILE'
                    )
                ]) {
                    sh '''
                        docker compose --env-file "$SMARTIMS_ENV_FILE" config --quiet
                    '''
                }
            }
        }

        stage('Deploy') {
            steps {
                withCredentials([
                    file(
                        credentialsId: 'smartims-env-properties',
                        variable: 'SMARTIMS_ENV_FILE'
                    )
                ]) {
                    sh '''
                        set -e
                        docker compose --env-file "$SMARTIMS_ENV_FILE" pull
                        docker compose --env-file "$SMARTIMS_ENV_FILE" up -d \
                          --force-recreate --remove-orphans
                        docker compose --env-file "$SMARTIMS_ENV_FILE" ps
                    '''
                }
            }
        }

        stage('Verify Backend Variables') {
            steps {
                sh '''
                    set -e
                    docker exec smartims-backend sh -c \
                      'test -n "$JWT_EXPIRATION" && test -n "$JWT_SECRET"'
                    echo "Required JWT environment variables are configured."
                '''
            }
        }

        stage('Health Check') {
            steps {
                withCredentials([
                    file(
                        credentialsId: 'smartims-env-properties',
                        variable: 'SMARTIMS_ENV_FILE'
                    )
                ]) {
                    sh '''
                        set -eu
                        attempt=1
                        max_attempts=18

                        while [ "$attempt" -le "$max_attempts" ]; do
                            if curl -fsS --max-time 10 "$BACKEND_HEALTH_URL" \
                                -o /tmp/smartims-health.json &&
                               grep -q '"status"[[:space:]]*:[[:space:]]*"UP"' \
                                /tmp/smartims-health.json; then
                                echo "Backend health check passed."
                                rm -f /tmp/smartims-health.json
                                exit 0
                            fi

                            echo "Backend not ready: attempt $attempt/$max_attempts"
                            sleep 10
                            attempt=$((attempt + 1))
                        done

                        docker compose --env-file "$SMARTIMS_ENV_FILE" ps
                        docker compose --env-file "$SMARTIMS_ENV_FILE" \
                          logs --tail=150 backend
                        rm -f /tmp/smartims-health.json
                        echo "Backend health check failed."
                        exit 1
                    '''
                }
            }
        }

        stage('Deployment Summary') {
            steps {
                withCredentials([
                    file(
                        credentialsId: 'smartims-env-properties',
                        variable: 'SMARTIMS_ENV_FILE'
                    )
                ]) {
                    sh '''
                        docker compose --env-file "$SMARTIMS_ENV_FILE" ps
                    '''
                }
            }
        }
    }

    post {
        always {
            sh 'docker logout || true'
        }

        success {
            echo 'Smart Incident Management deployment successful.'
            echo 'Frontend:   http://localhost:5173'
            echo 'Backend:    http://localhost:8081'
            echo 'SonarQube:  http://localhost:9000'
            echo 'Prometheus: http://localhost:9092'
            echo 'Grafana:    http://localhost:3002'
        }

        failure {
            echo 'Pipeline failed. Check the failed stage Console Output.'
        }
    }
}

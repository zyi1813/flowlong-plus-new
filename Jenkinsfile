pipeline {
    agent any

	tools {
		maven "apache-maven-3.8.7"
	}

    environment {
        docker_tag = sh(returnStdout: true, script: "git rev-parse --short HEAD").trim()
        // docker_tag = 'V1.0.1'
        docker_host = '172.20.0.12:9002'
    }

    stages {
        stage('拉取test-env分支代码') {
            steps {
                git branch: 'test-env', credentialsId: 'f0610d82-9c24-4cb2-9d08-38f0a07df90e', url: 'https://gitlab.yuencode.cn/jiaxiaoyu/flowlong-plus.git'
            }

        }

        stage('maven构建') {
            steps {
                sh '''
                    mvn clean package -Dmaven.test.skip=true
                '''
            }
        }

        stage('构建docker镜像') {
            steps {
                sh '''
                    echo "开始打包docker镜像 docker_tag=$docker_tag"
                    docker build -f ./Dockerfile -t flowlong-plus-server:${docker_tag} .
                '''
            }
        }

        stage('上传docker镜像到私服') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'DockerHarbor', passwordVariable: 'DockerHarborPassword', usernameVariable: 'DockerHarborUser')]) {
                    sh '''
                        echo "开始上传docker镜像"
                        docker tag flowlong-plus-server:${docker_tag} ${docker_host}/library/flowlong-plus-server:${docker_tag}
                        docker login ${docker_host} -u ${DockerHarborUser} -p ${DockerHarborPassword}
                        docker push ${docker_host}/library/flowlong-plus-server:${docker_tag}
                        docker logout ${docker_host}
                    '''
                }
            }
        }

        stage('上传redis配置文件到部署服务器') {
            steps {
                 sshPublisher(publishers: [sshPublisherDesc(configName: '172.20.0.13 docker-server', transfers: [sshTransfer(cleanRemote: false, excludes: '', execCommand: '',
                 execTimeout: 120000, flatten: false, makeEmptyDirs: false, noDefaultExcludes: false, patternSeparator: '[, ]+', remoteDirectory: '/root/project/flowlong_plus_server/redis/config', remoteDirectorySDF: false, removePrefix: 'redis', sourceFiles: 'redis/redis.conf')], usePromotionTimestamp: false, useWorkspaceInPromotion: false, verbose: true)])
            }
        }

        stage('上传frp配置文件到部署服务器') {
            steps {
                 sshPublisher(publishers: [sshPublisherDesc(configName: '172.20.0.13 docker-server', transfers: [sshTransfer(cleanRemote: false, excludes: '', execCommand: '',
                 execTimeout: 120000, flatten: false, makeEmptyDirs: false, noDefaultExcludes: false, patternSeparator: '[, ]+', remoteDirectory: '/root/project/flowlong_plus_server/frp', remoteDirectorySDF: false, removePrefix: 'frp', sourceFiles: 'frp/frpc.ini')], usePromotionTimestamp: false, useWorkspaceInPromotion: false, verbose: true)])
            }
        }

        stage('上传脚本文件到部署服务器') {
            steps {
                 sshPublisher(publishers: [sshPublisherDesc(configName: '172.20.0.13 docker-server', transfers: [sshTransfer(cleanRemote: false, excludes: '', execCommand: '',
                 execTimeout: 120000, flatten: false, makeEmptyDirs: false, noDefaultExcludes: false, patternSeparator: '[, ]+', remoteDirectory: '/root/project/flowlong_plus_server/script', remoteDirectorySDF: false, removePrefix: 'script', sourceFiles: 'script/*')], usePromotionTimestamp: false, useWorkspaceInPromotion: false, verbose: true)])
            }
        }


        stage('部署flowlong-plus-server镜像') {
            steps {
                 sshPublisher(publishers: [sshPublisherDesc(configName: '172.20.0.13 docker-server', transfers: [sshTransfer(cleanRemote: false, excludes: '', execCommand: """
                    cd /root/project/flowlong_plus_server
                    export docker_tag=${docker_tag}
                    export docker_host=${docker_host}
                    docker-compose config
                    docker-compose config > docker-compose-config.yml
                    
                    echo '停止容器'
                    docker-compose down
                    
                    echo '删除原有镜像'
                    sh script/clean_image.sh

                    echo '启动容器'
                    docker-compose up -d
                 """, execTimeout: 120000, flatten: false, makeEmptyDirs: false, noDefaultExcludes: false, patternSeparator: '[, ]+', remoteDirectory: '/root/project/flowlong_plus_server', remoteDirectorySDF: false, removePrefix: '', sourceFiles: 'docker-compose.yml')], usePromotionTimestamp: false, useWorkspaceInPromotion: false, verbose: true)])
            }
        }

    }
}
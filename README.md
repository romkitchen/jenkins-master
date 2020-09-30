# Docker image for Jenkins romkitchen master

This is a fully functional [Jenkins](https://jenkins.io) server with pre-installed plugins.

## Usage
`docker-compose up -d` will create a container while `docker [start|stop] romkitchen_master` will start/stop it.

## Job setup
Create a pipeline job for each Jenkinsfile manually.
To get started with the pipeline syntax [check out their documentation](https://www.jenkins.io/doc/book/pipeline/syntax).

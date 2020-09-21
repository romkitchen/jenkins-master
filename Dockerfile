FROM jenkins/jenkins:lts
RUN /usr/local/bin/install-plugins.sh \
	copyartifact \
	discord-notifier \
	git \
	matrix-project \
	workflow-aggregator

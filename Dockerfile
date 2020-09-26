FROM jenkins/jenkins:lts
RUN /usr/local/bin/install-plugins.sh \
	copyartifact \
	discord-notifier \
	extensible-choice-parameter \
	git \
	matrix-project \
	workflow-aggregator

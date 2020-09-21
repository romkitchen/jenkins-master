FROM jenkins/jenkins:lts
RUN /usr/local/bin/install-plugins.sh \
	copyartifact \
	git \
	matrix-project \
	workflow-aggregator

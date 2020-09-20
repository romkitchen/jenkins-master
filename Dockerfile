FROM jenkins/jenkins:lts
RUN /usr/local/bin/install-plugins.sh \
	git \
	matrix-project \
	workflow-aggregator

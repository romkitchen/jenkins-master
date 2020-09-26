static def getCommunityInstallableURL(device, os) {
	return null
}

pipeline {
	agent { label 'master' }
	stages {
		stage('Prepare') {
			steps {
				script {					
					properties([
						parameters([
							string(name: 'CONFIG_ID', defaultValue: '', description: 'Unique configuration identifier.'),
							[
								$class: 'ExtensibleChoiceParameterDefinition',
								choiceListProvider: [
									$class: 'SystemGroovyChoiceListProvider',
									groovyScript: [
										classpath: [],
										sandbox: false,
										script: '''
											import hudson.model.labels.LabelAtom
											import jenkins.model.Jenkins

											def configurations = []

											def jenkins = Jenkins.get()
											def onlineComputers = jenkins.computers.findAll { it.online }
											def availableLabels = onlineComputers.collect {
													it.assignedLabels.collect { LabelAtom.escape(it.expression) } }
												.flatten().unique(false)

											def lineage16Configurations = ['samsung:klte:lineage:lineage-16.0']

											if (availableLabels.containsAll(['lineage', 'lineage-16.0'])) {
												configurations.addAll(lineage16Configurations)
											}

											return configurations
										'''
									],
									usePredefinedVariables: false
								],
								description: '',
								editable: false,
								name: 'CONFIG'
							]
						])
					])

					echo 'Preparing...'

					if (params.CONFIG_ID == null) {
						error 'Missing parameter CONFIG_ID.'
					}

					configSplit = params.CONFIG.split(':')
					vendor = configSplit[0]
					device = configSplit[1]
					os = configSplit[2]
					version = configSplit[3]
				}

				stash allowEmpty: true, includes: "${JENKINS_HOME}/configs/${params.CONFIG_ID}/**/*", name: 'config'
			}
		}
		stage('Build') {
			agent { label "${os} && ${version}" }
			steps {
				echo "Building ${params.CONFIG}..."

				dir("${params.CONFIG_ID}") {
					dir('config') {
						unstash 'config'
					}

					// STEP 1: Synchronizing repository from $AGENT_HOME into a subdirectory of the workspace
					dir('src') {
						sh """
						rsync -ahvzP --delete "${AGENT_HOME}/" ./
						"""
					}

					// STEP 2: Prepare the device-specific code
					dir('src') {
						sh """#!/bin/bash
						source build/envsetup.sh
						breakfast ${device}
						"""
					}

					// STEP 3: Extracting proprietary blobs

					// STEP 3.1: Download and extract installable zip
					dir('installable') {
						script {
							installableURL = getCommunityInstallableURL(device, os)
							if (installableURL == null) {
								installableURL = sh(script: """
								wget --spider -Fr -np "https://lineageos.mirrorhub.io/full/${device}/" 2>&1 \
									| grep '^--' | awk '{ print \$3 }' | grep "${os}-${version}.*\\.zip\$" | sort -nr | head -n 1
								""", returnStdout: true).trim()
							}
						}

						sh """
						installableZip=\$(basename ${installableURL})
						curl --output \${installableZip} ${installableURL}
						unzip \${installableZip} -d .
						"""
					}

					// STEP 3.2: Finally extract
					dir('installable') {
						sh """#!/bin/bash
						for i in "*.new.dat.br"; do
							partition=\$(basename \${i} .new.dat.br)
							if [ -f "\${partition}.transfer.list" ]; then
								brotli --decompress --output="\${partition}.new.dat" "\${partition}.new.dat.br"
								sdat2img "\${partition}.transfer.list" "\${partition}.new.dat" "\${partition}.img"
							fi
						done

						if [ -f "payload.bin" ]; then
							python "../src/scripts/update-payload-extractor/extract.py" "payload.bin" --output_dir .
						fi

						if [ -f "system.img" ]; then
							7z x system.img -o"system_dump"
						fi

						images=( vendor product oem odm )
						for i in "\${images[@]}"; do
							if [ -f "\${i}.img" ]; then
								7z x "\${i}.img" -o"system_dump/\${i}"
							fi
						done

						if find -- "system_dump" -prune -type d -empty | grep -q .; then
							cp "system/*" "system_dump"
						fi
						"""
					}

					dir("src/device/${vendor}/${device}") {
						sh "./extract-files.sh ../../../../installable/system_dump"
					}

					// STEP 4: Prepare the device-specific code again
					dir('src') {
						sh """#!/bin/bash
						source build/envsetup.sh
						breakfast ${device}
						"""
					}

					// STEP 5: Apply patches
					dir('src') {
						sh """
						if [ -d "../config/patches" ]; then
							for i in "../config/patches/*"; do
								patch -t < "\${i}"
							done
						fi
						"""
					}

					// STEP 6: Add apps
					// TODO

					// STEP 7: Start the build
					dir('src') {
						sh """#!/bin/bash
						export CCACHE_EXEC=/usr/bin/ccache
						export USE_CCACHE=1
						\${CCACHE_EXEC} -M \${CCACHE_SIZE}
						source build/envsetup.sh
						brunch ${device}
						"""
					}

					// STEP 8: Sign build
					// TODO
				}
			}
			post {
				always {
					archiveArtifacts allowEmptyArchive: false, artifacts: "${params.CONFIG_ID}/src/out/target/product/${device}/${os}-${version}*.zip, ${params.CONFIG_ID}/src/out/target/product/${device}/${os}-${version}*.zip.md5sum", onlyIfSuccessful: true
				}
				cleanup {
					echo 'Cleaning workspace...'
					dir("${params.CONFIG_ID}") {
						deleteDir()
					}
				}
			}
		}
		stage('Finalize') {
			steps {
				echo 'Finalizing...'
				copyArtifacts fingerprintArtifacts: true, flatten: true, optional: false, projectName: "${JOB_NAME}", selector: specific("${BUILD_NUMBER}"), target: "${JENKINS_HOME}/configs/${params.CONFIG_ID}/out"
			}
		}
	}
	post {
		cleanup {
			echo 'Final cleaning...'
		}
	}
}

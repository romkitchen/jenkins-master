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
							string(name: 'CONFIG_ID', defaultValue: '', description: 'Unique configuration identifier.', trim: true),
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

											def jenkins = Jenkins.get()
											def workspace = "${jenkins.rootDir}/workspace/${project.name}"

											return jenkins.computers
												.findAll { it.online }
												.collect { it.node.labelString }
												.collect { it.split('\\s+') }
												.flatten()
												.findAll { !it.empty }
												.collect { new File("${workspace}/configurations/${it}.txt") }
												.findAll { it.exists() }
												.collect { it.text.split('[\\r\\n]+') }
												.flatten()
										'''
									],
									usePredefinedVariables: true
								],
								description: 'Configuration containing vendor, device, OS and version. Each separated by a colon.',
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

					// STEP 1: Test patches
					dir("${AGENT_HOME}") {
						sh """
						if [ -d "${WORKSPACE}/${params.CONFIG_ID}/config/patches" ]; then
							for i in "${WORKSPACE}/${params.CONFIG_ID}/config/patches/*"; do
								patch --dry-run -t < "\${i}"
							done
						fi
						"""
					}

					// STEP 2: Synchronizing repository from $AGENT_HOME into a subdirectory of the workspace
					dir('src') {
						sh """
						rsync -ahvzP --delete "${AGENT_HOME}/" ./
						"""
					}

					// STEP 3: Prepare the device-specific code
					dir('src') {
						sh """#!/bin/bash
						source build/envsetup.sh
						breakfast ${device}
						"""
					}

					// STEP 4: Extracting proprietary blobs

					// STEP 4.1: Download and extract installable zip
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

					// STEP 4.2: Finally extract
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

					// STEP 5: Prepare the device-specific code again
					dir('src') {
						sh """#!/bin/bash
						source build/envsetup.sh
						breakfast ${device}
						"""
					}

					// STEP 6: Apply patches
					dir('src') {
						sh """
						if [ -d "../config/patches" ]; then
							for i in "../config/patches/*"; do
								patch -t < "\${i}"
							done
						fi
						"""
					}

					// STEP 7: Add apps
					// TODO

					// STEP 8: Start the build
					dir('src') {
						sh """#!/bin/bash
						export CCACHE_EXEC=/usr/bin/ccache
						export USE_CCACHE=1
						\${CCACHE_EXEC} -M \${CCACHE_SIZE}
						source build/envsetup.sh
						brunch ${device}
						"""
					}

					// STEP 9: Sign build
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

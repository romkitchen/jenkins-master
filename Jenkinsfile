pipeline {
	agent { label 'master' }
	stages {
		stage('Prepare') {
			steps {
				echo 'Preparing...'

				script {
					properties([
						parameters([
							string(name: 'CONFIG_ID', defaultValue: '', description: 'Unique configuration identifier.', trim: true),
							[
								$class: 'ExtensibleChoiceParameterDefinition',
								choiceListProvider: [
									$class: 'SystemGroovyChoiceListProvider',
									usePredefinedVariables: true,
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
									]
								],
								description: 'Configuration containing vendor, device, OS and version. Each separated by a colon.',
								editable: false,
								name: 'CONFIG'
							],
							booleanParam(name: 'DEBUG', defaultValue: false, description: 'Whether directories should be cleaned up or not.')
						])
					])

					if (!params.CONFIG_ID) {
						error 'Missing parameter CONFIG_ID.'
					}

					configSplit = params.CONFIG.split(':')
					vendor = configSplit[0]
					device = configSplit[1]
					os = configSplit[2]
					version = configSplit[3]
				}

				stash allowEmpty: true, includes: "${JENKINS_HOME}/uploads/${params.CONFIG_ID}/**/*", name: 'config-uploads'
			}
		}
		stage('Build') {
			agent {
				node {
					label "${os} && ${version}"
					customWorkspace "${AGENT_HOME}"
				}
			}
			steps {
				// check if sync job is queued
				// and wait until it's finished
				script {
					def syncQueued = false

					while ({
						syncQueued = fileExists 'sync.queued'
						syncQueued
					}()) continue
				}

				echo "Building ${params.CONFIG}..."

				// create status file used for sync job
				// to check for currently running builds
				writeFile file: "${params.CONFIG_ID}.running", text: ''

				// create custom device
				dir("device/romkitchen/${params.CONFIG_ID}") {
					unstash 'config-uploads'

					// create patched files in overlay folder
					dir('patches') {
						dir('original') {
							sh 'mv ../*.patch .'
						}

						sh 'splitpatch original/*.patch'

						dir('../../../..') {
							sh """#!/bin/bash
							for i in "device/romkitchen/${params.CONFIG_ID}/patches/*.patch"; do
								OUTFILE = \$(diffstat -p0 -l "\${i}")
								patch -p0 -t -o "overlay/\${OUTFILE}" << "\${i}"
							done
							"""
						}
					}

					writeFile file: 'AndroidProducts.mk', text: 'PRODUCT_MAKEFILES := $(LOCAL_DIR)/product.mk'

					// read original makefiles
					sh """#!/bin/bash
					inheritProductCalls=\$(LOCAL_DIR="device/${vendor}/${device}" make -f - 2>/dev/null <<\\EOF
					include AndroidProducts.mk
					all:
						\$(foreach MAKEFILE,\$(PRODUCT_MAKEFILES),\$\$(call inherit-product, \$(MAKEFILE)))
EOF
					)
					"""

					// write product makefile
					writeFile file: 'product.mk', text: """
					${inheritProductCalls}

					include \$(CLEAR_VARS)
					LOCAL_MODULE := system-apps
					LOCAL_SRC_FILES := apps/system/*.apk
					LOCAL_MODULE_CLASS := APPS
					LOCAL_MODULE_TAGS := optional
					LOCAL_UNINSTALLABLE_MODULE := true
					LOCAL_CERTIFICATE := PRESIGNED
					LOCAL_MULTILIB := both
					include \$(BUILD_PREBUILT)

					include \$(CLEAR_VARS)
					LOCAL_MODULE := data-apps
					LOCAL_SRC_FILES := apps/data/*.apk
					LOCAL_MODULE_CLASS := APPS
					LOCAL_MODULE_TAGS := optional
					LOCAL_UNINSTALLABLE_MODULE := false
					LOCAL_CERTIFICATE := PRESIGNED
					LOCAL_MULTILIB := both
					include \$(BUILD_PREBUILT)

					DEVICE_PACKAGE_OVERLAYS += \$(LOCAL_PATH)/overlay
					PRODUCT_NAME := ${os}_${device}_${params.CONFIG_ID}
					PRODUCT_PACKAGES += system-apps data-apps
					"""
				}

				// add missing proprietary blobs
				script {
					def roomserviceXmlFile = "${AGENT_HOME}/.repo/local_manifests/roomservice.xml"
					def roomserviceXml = new XmlParser().parse(roomserviceXmlFile)
					def hasProprietaryBlobs = roomserviceXml.project.any { it.@path == "vendor/${vendor}" }

					if (!hasProprietaryBlobs) {
						roomserviceXml.project << { @name: "TheMuppets/proprietary_vendor_${vendor}", @path: "vendor/${vendor}", @remote: 'github' }
						new XmlNodePrinter(new PrintWriter(new FileWriter(roomserviceXmlFile))).print(roomserviceXml)
					}
				}

				// get missing proprietary blobs
				sh """
				if [ ! ${hasProprietaryBlobs} ]; then
					repo sync "TheMuppets/proprietary_vendor_${vendor}"
				fi
				"""

				// prepare the device-specific code
				sh """#!/bin/bash
				source build/envsetup.sh
				breakfast ${device}
				lunch ${os}_${device}_${params.CONFIG_ID}-userdebug
				"""

				// turn on caching to speed up build and start
				sh """#!/bin/bash
				export OUT_DIR="${AGENT_HOME}/out/${params.CONFIG_ID}"
				export CCACHE_EXEC=/usr/bin/ccache
				export USE_CCACHE=1
				ccache -M \${CCACHE_SIZE}
				source build/envsetup.sh
				mka bacon
				"""

				// sign build
				// TODO ...
			}
			post {
				always {
					archiveArtifacts allowEmptyArchive: false, artifacts: "out/${params.CONFIG_ID}/target/product/${device}/*${version}*.zip, out/${params.CONFIG_ID}/target/product/${device}/*${version}*.zip.md5sum", onlyIfSuccessful: true
				}
				cleanup {
					echo 'Cleaning workspace...'

					script {
						if (!params.DEBUG) {
							dir("device/romkitchen/${params.CONFIG_ID}") {
								deleteDir()
							}

							dir ("out/${params.CONFIG_ID}") {
								deleteDir()
							}
						}
					}

					// delete status file
					sh "rm ${params.CONFIG_ID}.running"
				}
			}
		}
		stage('Finalize') {
			steps {
				echo 'Finalizing...'

				copyArtifacts flatten: true, optional: false, projectName: "${JOB_NAME}", selector: specific("${BUILD_NUMBER}"), target: "${JENKINS_HOME}/out/${params.CONFIG_ID}"
			}
		}
	}
	post {
		cleanup {
			echo 'Final cleaning...'

			script {
				if (!params.DEBUG) {
					dir("${JENKINS_HOME}/uploads/${params.CONFIG_ID}") {
						deleteDir()
					}
				}
			}
		}
	}
}

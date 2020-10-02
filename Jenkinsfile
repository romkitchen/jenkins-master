@NonCPS
def appendMuppetsRepository(String manifest, String vendor) {
	def projects = new XmlSlurper().parseText(manifest)
	hasProprietaryBlobs = projects.project.any { it.@path == "vendor/${vendor}" }

	if (!hasProprietaryBlobs) {
		projects.appendNode(new XmlSlurper().parseText("<project name=\"TheMuppets/proprietary_vendor_${vendor}\" path=\"vendor/${vendor}\" remove=\"github\"/>"))
		return groovy.xml.XmlUtil.serialize(projects)
	}

	return null
}

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
												.collect { it.split('\\\\s+') }
												.flatten()
												.findAll { !it.empty }
												.collect { new File("${workspace}/configurations/${it}.txt") }
												.findAll { it.exists() }
												.collect { it.text.split('[\\\\r\\\\n]+') }
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
			agent { label "${os} && ${version}" }
			steps {
				ws("${AGENT_WORKDIR}/workspace") {
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

					// prepare custom device
					dir("device/romkitchen/${params.CONFIG_ID}") {
						unstash 'config-uploads'

						// create patched files in overlay folder
						dir('patches') {
							script {
								def hasPatches = new File("${WORKSPACE}/device/romkitchen/${params.CONFIG_ID}/patches").listFiles().any { it.file }
								if (hasPatches) {
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
							}
						}
					}

					// add missing proprietary blobs
					script {
						def manifest = readFile "${WORKSPACE}/.repo/local_manifests/romkitchen.xml"
						def newManifest = appendMuppetsRepository(manifest, vendor)
						hasProprietaryBlobs = newManifest == null

						if (!hasProprietaryBlobs) {
							writeFile file: "${WORKSPACE}/.repo/local_manifests/romkitchen.xml", text: newManifest
						}
					}

					// get missing proprietary blobs
					sh """
					if [ "${hasProprietaryBlobs}" = false ]; then
						repo sync "TheMuppets/proprietary_vendor_${vendor}"
					fi
					"""

					// prepare the device-specific code
					sh """#!/bin/bash
					source build/envsetup.sh
					breakfast ${device}
					"""

					// create custom device
					dir("device/romkitchen/${params.CONFIG_ID}") {
						// read original makefiles
						script {
							inheritProductCalls = sh(returnStdout: true, script: """#!/bin/bash
								export LOCAL_DIR="device/${vendor}/${device}"
								echo 'romkitchen:;@echo \$(PRODUCT_MAKEFILES)' | make -f - -f "${WORKSPACE}/\${LOCAL_DIR}/AndroidProducts.mk" --no-print-directory romkitchen
							""").trim().split(' ').collect { "\$(call inherit-product, ${it})" }.join("\n")
						}

						writeFile file: 'AndroidProducts.mk', text: 'PRODUCT_MAKEFILES := $(LOCAL_DIR)/product.mk'

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

					sh """#!/bin/bash
					source build/envsetup.sh
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
				}
			}
			post {
				always {
					ws("${AGENT_WORKDIR}/workspace") {
						archiveArtifacts allowEmptyArchive: false, artifacts: "out/${params.CONFIG_ID}/target/product/${device}/*${version}*.zip, out/${params.CONFIG_ID}/target/product/${device}/*${version}*.zip.md5sum", onlyIfSuccessful: true
					}
				}
				cleanup {
					ws("${AGENT_WORKDIR}/workspace") {
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

#!/bin/groovy

def getConfigurations() {
	return ['samsung:klte:lineage-16.0']
}

def getCommunityInstallableURL(device, os) {
	return null;
}

pipeline {
	agent { label 'master' }
	parameters {
		string name: 'CONFIG_ID', defaultValue: '', description: 'Unique configuration identifier.'
		choice name: 'CONFIG', choices: getConfigurations(), description: 'Configuration containing vendor, device and OS. Each separated by a colon.'
	}
	stages {
		stage('Prepare') {
			steps {
				script {
					echo 'Preparing...'

					if (params.CONFIG_ID == null) {
						error 'Missing parameter CONFIG_ID.'
					}

					vendor = params.CONFIG.substring(0, params.CONFIG.indexOf(':'))
					device = params.CONFIG.substring(params.CONFIG.indexOf(':') + 1, params.CONFIG.lastIndexOf(':'))
					os = params.CONFIG.substring(params.CONFIG.lastIndexOf(':') + 1)
				}

				stash allowEmpty: true, includes: "${env.JENKINS_HOME}/configs/${params.CONFIG_ID}/**/*", name: 'config'
			}
		}
		stage('Build') {
			agent { label os }
			steps {
				echo "Building ${os} for ${device}..."

				dir('config') {
					unstash 'config'
				}

				// STEP 1: Synchronizing repository from $AGENT_HOME into a subdirectory of the workspace
				dir('src') {
					sh """
					make clean
					rsync -ahvzP --delete "${AGENT_HOME}/" ./
					"""
				}

				// STEP 2: Prepare the device-specific code
				dir('src') {
					sh """
					source build/envsetup.sh
					breakfast ${device}
					"""
				}

				// STEP 3: Extracting proprietary blobs

				// STEP 3.1: Download and extract installable zip
				script {
					installableURL = getCommunityInstallableURL(device, os)
					if (installableURL == null) {
						installableURL = sh script: """
						wget --spider -Fr -np "https://lineageos.mirrorhub.io/full/${device}/" 2>&1 \
							| grep '^--' | awk '{ print \$3 }' | grep "$os.*\\.zip\$" | sort -nr | head -n 1
						""", returnStdout: true
					}
				}
				sh "curl ${installableURL} --output installable.zip && unzip installable.zip -d installable"

				// STEP 3.2: Finally extract
				sh """
				for i in "installable/*.new.dat.br"; do
					partition=\$(basename \$i .new.dat.br)
					if [ -f "installable/${partition}.transfer.list" ]; then
						brotli --decompress --output="${partition}.new.dat" "\$i"
						sdat2img "installable/${partition}.transfer.list" "${partition}.new.dat" "${partition}.img"
					fi
				done

				if [ -f "installable/payload.bin" ]; then
					python "src/scripts/update-payload-extractor/extract.py" "installable/payload.bin" --output_dir .
				fi

				if [ -f "system.img" ]; then
					7z x system.img -o"system_dump"
				fi

				images=( vendor product oem odm )
				for i in "\${images[@]}"; do
					if [ -f "${i}.img" ]; then
						7z x "${i}.img" -o"system_dump/${i}"
					fi
				done

				if find -- "system_dump" -prune -type d -empty | grep -q .; then
					cp "installable/system/*" "system_dump"
				fi

				./src/device/${vendor}/${device}/extract-files.sh "system_dump"
				"""

				// STEP 4: Prepare the device-specific code again
				dir('src') {
					sh "breakfast ${device}"
				}

				// STEP 5: Apply patches
				dir('src') {
					sh """
					for i in "../config/patches/*"; do
						patch -t < "../config/patches/${i}"
					done
					"""
				}

				// STEP 6: Add apps
				// TODO

				// STEP 7: Turn on caching to speed up build
				sh """
				export USE_CCACHE=1
				export CCACHE_EXEC=/usr/bin/ccache
				${CCACHE_EXEC} -M ${CCACHE_SIZE}
				"""

				// STEP 8: Start the build
				dir('src') {
					sh "brunch ${device}"
				}

				// STEP 9: Sign build
				// TODO
			}
			post {
				always {
					archiveArtifacts allowEmptyArchive: false, artifacts: "src/out/target/product/${device}/*.zip", onlyIfSuccessful: true
				}
				cleanup {
					echo 'Cleaning build...'
					sh """
					for i in "../config/patches/*"; do
						patch -tR < "../config/patches/\$i"
					done
					"""
					// TODO remove apps
				}
			}
		}
		stage('Finalize') {
			steps {
				echo 'Finalizing...'
				copyArtifacts optional: false, projectName: "${JOB_NAME}", selector: specific("${BUILD_NUMBER}"), target: "${env.JENKINS_HOME}/configs/${params.CONFIG_ID}/out"
			}
		}
	}
	post {
		cleanup {
			echo 'Final cleaning...'
		}
	}
}

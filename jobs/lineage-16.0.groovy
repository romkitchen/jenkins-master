job("lineage-16.0") {
	concurrentBuild()
	configure { project ->
		(project / "authToken").setValue("A6EWtQMoCayRy3e76j0j")
	}
	description("Builds LineageOS 16.0 for a specific device with optional patches.")
	displayName("LineageOS 16.0 (nightly)")
	label("lineage-16.0")
	parameters {
		choiceParam("DEVICE", ["samsung:klte"], "All Android devices have a unique codename that is used in development projects.")
		booleanParam("HID_DEV_MTU_SIZE_512_PATCH", false, "Applies a patch which sets HID_DEV_MTU_SIZE to 512 for Nintendo Amiibo support.")
		stringParam {
			name("USER_EMAIL_ADDRESS")
			description("Email address to send an email to when job is finished.")
			trim()
		}
		stringParam {
			name("USER_LANGUAGE")
			defaultValue("en")
			description("An ISO 639 alpha-2 or alpha-3 language code which is used to localize the job finished email.")
			trim()
		}
	}
	properties {
		copyArtifactPermissionProperty {
			projectNames('lineage-16.0-deploy')
		}
	}
	publishers {
		archiveArtifacts {
			allowEmpty(false)
			defaultExcludes()
			fingerprint()
			onlyIfSuccessful()
			pattern("src/out/target/product/**/lineage-*.zip")
		}
		downstream("lineage-16.0-deploy", "UNSTABLE")
	}
	scm {
		git {
			branch("*/master")
			extensions {
				relativeTargetDirectory('scripts')
			}
			remote {
				name("scripts")
				github("romkitchen/jenkins-scripts", "https")
			}
		}
	}
	steps {
		shell("./scripts/build-lineage.sh")
	}
}

job("lineage-16.0-deploy") {
	concurrentBuild()
	description("Deploy and send email to user if email is provided.")
	displayName("Deploy: LineageOS 16.0")
	steps {
		copyArtifacts('lineage-16.0') {
			buildSelector {
				upstreamBuild()
			}
			fingerprintArtifacts()
			targetDirectory('/var/www/demo')
		}
	}
}

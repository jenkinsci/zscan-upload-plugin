# zscan-jenkins-plugin
Jenkins plugin to upload builds to zScan for analysis. 

## Pre-requisites

This project requires Java 11 or higher to build. All of the 3rd-party jars we're using are compiled with Java 17.  

In your console, head over to the Authorizations tab in the Manage section and generate a new API key that at least has the scope to `upload z3a_builds`.

## Build
```mvn clean install```

and the artifact `zDevJenkinsUploadPlugin.hpi` will have been created in the `target` directory
(`./target/zScanJenkinsUploadPlugin.hpi`.) 
If you don't see the `.hpi` file, running ```mvn package``` also creates the `.hpi` file.


## Installation
1. Manage Jenkins -> Manage Plugins -> Advanced tab -> Deploy Plugin -> Choose file (`zDevJenkinsUploadPlugin.hpi`)
2. Restart Jenkins

## Configuration
In the `Configure` section of your project, `Add post-build action` and select `Upload build artifacts to zDev`.

Fields that need to be populated are:
1. Zimperium Server URL Endpoint
- This is going to be your root URL to your console (`https://ziap-dev.zimperium.com` or `https://ziap.zimperium.com` for example)
2. Client ID
- This is from the `Authorizations` section when you generate your API Key
3. Client Secret
- Similar to Client ID however this is ONLY displayed when you first generated your key so be sure to save it or `Regenerate Secret`
4. Source Files
- This provides ability to specify patterns to select files to be uploaded, multiple patterns are comma-separated (`*.apk, *.ipa` for example)
5. Excluded Files
- Opposite of above, provides ability to specify patterns to exclude files, multiple patterns are comma-separated (`*.md, *.java` for example)

## Other Maven goals you may find useful

The Maven sub-goals provided by the HPI plugin are documented here: 

[Jenkins Maven Plugin Goals](https://jenkinsci.github.io/maven-hpi-plugin/plugin-info.html)

For example, ```mvn hpi:hpi``` builds the `.hpi` file. 

## References

[Jenkins CI Plugin POM on GitHub](https://github.com/jenkinsci/plugin-pom)

[Jenkins CI Plugin Releases](https://github.com/jenkinsci/plugin-pom/releases)

[Choosing the Jenkins baseline](https://www.jenkins.io/doc/developer/plugin-development/choosing-jenkins-baseline/#currently-recommended-versions)

[Jenkins Plugin Development Guide](https://www.jenkins.io/doc/developer/plugin-development/)

[Managing Jenkins Plugins](https://www.jenkins.io/doc/book/managing/plugins/)

[Jenkins Maven Plugin Goals](https://jenkinsci.github.io/maven-hpi-plugin/plugin-info.html)





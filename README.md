# zscan-upload

Jenkins plugin to upload builds to zScan for analysis.

## Pre-requisites

This project requires Java 17 or higher to build. All of the 3rd-party jars we're using are compiled with Java 17.  

In your console, head over to the *Authorizations* tab in the *Account Management* section and generate a new API key that at least has the permissions of `zScan Builds - Upload`.  If assessment reports are required, the `zScan Assessments - View` permission is also necessary.

## Build

```mvn clean install```

and the artifact `zscan-upload.hpi` will have been created in the `target` directory
(`./target/zscan-upload.hpi`.)
If you don't see the `.hpi` file, running ```mvn package``` also creates the `.hpi` file.

## Installation

The easiest way to install this plugin is from the Jenkins Marketplace.  If you prefer manual installation, follow these steps:

1. Download one of the official releases from this repo or build one yourself using the above instructions.
2. Manage Jenkins -> Manage Plugins -> Advanced tab -> Deploy Plugin -> Choose file (`zscan-upload.hpi`)
3. Restart Jenkins

## Configuration

In the `Configure` section of your project, `Add post-build action` and select `Upload build artifacts to zScan`.

Fields that need to be populated are:

### Zimperium Server URL Endpoint

This is going to be your root URL to your console (e.g., `https://ziap.zimperium.com` or `https://zc202.zimperium.com`).

### Client ID

This is from the `Authorizations` section when you generate your API Key.

### Client Secret

Similar to Client ID however this is ONLY displayed when you first generated your key so be sure to save it or `Regenerate Secret`.

### Source Files

This provides ability to specify patterns to select files to be uploaded, multiple patterns are comma-separated (`*.apk, *.ipa` for example). To prevent accidental flooding of zScan servers, only the first 5 matches will be processed.

### Excluded Files

Opposite of above, provides ability to specify patterns to exclude files, multiple patterns are comma-separated (`*.md, *.java` for example).

### Wait for Report

If checked, the plugin will wait for an assessment report after uploading each binary. Reports take about 10 minutes to generate and the build step execution is paused while waiting. Report generation times out after 20 minutes to prevent 'stuck builds'.  If unchecked, the execution will move on to the next binary.  Reports can also be obtained from the zScan console

### Report Format

Specifies the format for the assessment report.  For more information on SARIF, please see [OASIS Open](https://docs.oasis-open.org/sarif/sarif/v2.1.0/sarif-v2.1.0.html).

### Report File Name

Filename(s) for the assessment report(s). Assessment ID is appended to the filename to prevent multiple reports overwriting one another.

## Advanced Configuration

### Team Name

Team name to assign applications to. If no team name is provided or if a team with the provided name is not found, the 'Default' team is used.  

**Note:** The API key must have additional permissions to be able to manage team assignment: `Common - Teams - Manage` and `zScan Apps - Manage`.  This setting is only relevant when uploading an application for the first time. To change the application's team, please use the zScan Console.

## Other Maven goals you may find useful when building this plugin

The Maven sub-goals provided by the HPI plugin are documented here:

[Jenkins Maven Plugin Goals](https://jenkinsci.github.io/maven-hpi-plugin/plugin-info.html)

For example, ```mvn hpi:hpi``` builds the `.hpi` file, while ```mvn hpi:run``` starts debug instance of Jenkins with the plugin preloaded.

## References

[Jenkins CI Plugin POM on GitHub](https://github.com/jenkinsci/plugin-pom)

[Jenkins CI Plugin Releases](https://github.com/jenkinsci/plugin-pom/releases)

[Choosing the Jenkins baseline](https://www.jenkins.io/doc/developer/plugin-development/choosing-jenkins-baseline/#currently-recommended-versions)

[Jenkins Plugin Development Guide](https://www.jenkins.io/doc/developer/plugin-development/)

[Managing Jenkins Plugins](https://www.jenkins.io/doc/book/managing/plugins/)

[Jenkins Maven Plugin Goals](https://jenkinsci.github.io/maven-hpi-plugin/plugin-info.html)

## License

This plugin is licensed under the MIT License. By using this plugin, you agree to the following terms:

```text
MIT License

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Enhancements

Submitting improvements to the plugin is welcomed and all pull requests will be approved by Zimperium after review.

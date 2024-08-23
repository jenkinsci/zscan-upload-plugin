package com.zimperium.plugins.zDevJenkinsUploadPlugin.dtos;

public class BuildMetadata {
    private final String branchName;
    private final String buildNumber;
    private final String ciToolId;
    private final String ciToolName;
    private final String environment;
    private final String startedBy;

    public BuildMetadata(String branchName,
                         String buildNumber,
                         String ciToolId,
                         String ciToolName,
                         String environment,
                         String startedBy) {

        this.branchName = branchName;
        this.buildNumber = buildNumber;
        this.ciToolId = ciToolId;
        this.ciToolName = ciToolName;
        this.environment = environment;
        this.startedBy = startedBy;
    }

    public String getBranchName() {
        return branchName;
    }

    public String getBuildNumber() {
        return buildNumber;
    }

    public String getCiToolId() {
        return ciToolId;
    }

    public String getCiToolName() {
        return ciToolName;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getStartedBy() {
        return startedBy;
    }
}

package com.zimperium.plugins.zDevJenkinsUploadPlugin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zimperium.plugins.zDevJenkinsUploadPlugin.dtos.LoginCredentials;
import com.zimperium.plugins.zDevJenkinsUploadPlugin.dtos.LoginResponse;
import com.zimperium.plugins.zDevJenkinsUploadPlugin.dtos.RefreshCredentials;
import com.zimperium.plugins.zDevJenkinsUploadPlugin.services.UploadPluginService;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;
import okhttp3.*;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.apache.commons.io.FilenameUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.verb.POST;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;


public class ZDevUploadPlugin extends Recorder implements SimpleBuildStep{

    // constants
    public final static String toolId = "JKNS";
    public final static String toolName = "Jenkins";
    public final static long checkInterval = 30 * 1000; // 30 seconds
    public final static int connectionTimeout = 2 * 60 * 1000; // 2 minutes
    public final static int writeTimeout = 10 * 60 * 1000; // 10 minutes
    public final static int readTimeout = 10 * 60 * 1000; // 10 minutes

    public final static Result DEFAULT_STEP_RESULT = Result.FAILURE;
    public final static int MAX_FILES_UPLOAD = 5;
    public final static int MAX_RETRIES = 3;
    public final static String DEFAULT_REPORT_FILE = "zscan-report.json";
    public final static String DEFAULT_TEAM_NAME = "Default";
    public final static Integer DEFAULT_REPORT_TIMEOUT_MINUTES = 30;

    // plugin settings
    // console information
    private Boolean useOwnConsoleInfo;
    private String endpoint;
    private String clientId;
    private Secret clientSecret;
    private Boolean useProxy;

    // binaries
    private String sourceFile;
    private String excludedFile;

    // report settings
    private Boolean waitForReport;
    private ReportFormat reportFormat = ReportFormat.JSON;
    private String reportFileName = DEFAULT_REPORT_FILE;
    private Boolean evaluateBuildStatusOnScanFindings = false;
    private ScanStatusAction scanStatusAction = ScanStatusAction.UNSTABLE;
    private ScanEvaluationMode scanEvaluationMode = ScanEvaluationMode.ANY_FINDING;
    private Severity minimumSeverity = Severity.LOW;

    // advanced settings
    private String teamName = DEFAULT_TEAM_NAME;
    private Integer reportTimeoutMinutes = DEFAULT_REPORT_TIMEOUT_MINUTES;

    @DataBoundConstructor
    public ZDevUploadPlugin(Boolean useOwnConsoleInfo, String endpoint, String clientId, Secret clientSecret, Boolean useProxy, 
                            String sourceFile, String excludedFile,
                            Boolean waitForReport, ReportFormat reportFormat, String reportFileName, 
                            Boolean evaluateBuildStatusOnScanFindings, ScanStatusAction scanStatusAction, ScanEvaluationMode scanEvaluationMode, Severity minimumSeverity,
                            String teamName, Integer reportTimeoutMinutes) {
        this.useOwnConsoleInfo = useOwnConsoleInfo;
        this.endpoint = endpoint;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.useProxy = useProxy;
        this.sourceFile = sourceFile;
        this.excludedFile = excludedFile;
        this.waitForReport = waitForReport;
        this.reportFormat = reportFormat != null ? reportFormat : ReportFormat.JSON;
        this.reportFileName = reportFileName != null ? reportFileName : DEFAULT_REPORT_FILE;
        this.evaluateBuildStatusOnScanFindings = evaluateBuildStatusOnScanFindings != null ? evaluateBuildStatusOnScanFindings : false;
        this.scanStatusAction = scanStatusAction != null ? scanStatusAction : ScanStatusAction.UNSTABLE;
        this.scanEvaluationMode = scanEvaluationMode != null ? scanEvaluationMode : ScanEvaluationMode.ANY_FINDING;
        this.minimumSeverity = minimumSeverity != null ? minimumSeverity : Severity.LOW;
        this.teamName = teamName != null ? teamName : DEFAULT_TEAM_NAME;
        this.reportTimeoutMinutes = reportTimeoutMinutes != null ? reportTimeoutMinutes : DEFAULT_REPORT_TIMEOUT_MINUTES;
    }


    @DataBoundSetter
    public void setUseOwnConsoleInfo(Boolean useOwnConsoleInfo) {
        this.useOwnConsoleInfo = useOwnConsoleInfo;
    }
    public Boolean getUseOwnConsoleInfo() {
        return useOwnConsoleInfo;
    }

    @DataBoundSetter
    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }
    public String getEndpoint() {
        return endpoint;
    }

    @DataBoundSetter
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }
    public String getClientId() {
        return clientId;
    }

    @DataBoundSetter
    public void setClientSecret(Secret clientSecret) {
        this.clientSecret = clientSecret;
    }
    public Secret getClientSecret() {
        return clientSecret;
    }

    @DataBoundSetter
    public void setUseProxy(Boolean useProxy) {
        this.useProxy = useProxy;
    }
    public Boolean getUseProxy() {
        return useProxy;
    }

    @DataBoundSetter
    public void setSourceFile(String sourceFile) {
        this.sourceFile = sourceFile;
    }
    public String getSourceFile() {
        return sourceFile;
    }

    @DataBoundSetter
    public void setExcludedFile(String excludedFile) {
        this.excludedFile = excludedFile;
    }
    public String getExcludedFile() {
        return excludedFile;
    }

    @DataBoundSetter
    public void setWaitForReport(Boolean waitForReport) {
        this.waitForReport = waitForReport;
    }
    public Boolean getWaitForReport() {
        return waitForReport;
    }

    @DataBoundSetter
    public void setReportFormat(ReportFormat reportFormat) {
        this.reportFormat = reportFormat;
    }
    public ReportFormat getReportFormat() {
        return reportFormat;
    }

    @DataBoundSetter
    public void setReportFileName(String reportFileName) {
        this.reportFileName = reportFileName;
    }
    public String getReportFileName() {
        return reportFileName;
    }

    @DataBoundSetter
    public void setScanStatusAction(ScanStatusAction scanStatusAction) {
        this.scanStatusAction = scanStatusAction != null ? scanStatusAction : ScanStatusAction.UNSTABLE;
    }
    public ScanStatusAction getScanStatusAction() {
        return scanStatusAction != null ? scanStatusAction : ScanStatusAction.UNSTABLE;
    }

    @DataBoundSetter
    public void setEvaluateBuildStatusOnScanFindings(Boolean evaluateBuildStatusOnScanFindings) {
        this.evaluateBuildStatusOnScanFindings = evaluateBuildStatusOnScanFindings;
    }
    public Boolean getEvaluateBuildStatusOnScanFindings() {
        return evaluateBuildStatusOnScanFindings != null ? evaluateBuildStatusOnScanFindings : false;
    }

    @DataBoundSetter
    public void setScanEvaluationMode(ScanEvaluationMode scanEvaluationMode) {
        this.scanEvaluationMode = scanEvaluationMode != null ? scanEvaluationMode : ScanEvaluationMode.ANY_FINDING;
    }
    public ScanEvaluationMode getScanEvaluationMode() {
        return scanEvaluationMode != null ? scanEvaluationMode : ScanEvaluationMode.ANY_FINDING;
    }

    @DataBoundSetter
    public void setMinimumSeverity(Severity minimumSeverity) {
        this.minimumSeverity = minimumSeverity != null ? minimumSeverity : Severity.LOW;
    }
    public Severity getMinimumSeverity() {
        return minimumSeverity != null ? minimumSeverity : Severity.LOW;
    }

    @DataBoundSetter
    public void setTeamName(String teamName) {
        this.teamName = teamName != null ? teamName : DEFAULT_TEAM_NAME;
    }
    public String getTeamName() {
        return teamName;
    }

    @DataBoundSetter
    public void setReportTimeoutMinutes(Integer reportTimeoutMinutes) {
        this.reportTimeoutMinutes = reportTimeoutMinutes != null ? reportTimeoutMinutes : DEFAULT_REPORT_TIMEOUT_MINUTES;
    }
    public Integer getReportTimeoutMinutes() {
        return (reportTimeoutMinutes != null) ? reportTimeoutMinutes : DEFAULT_REPORT_TIMEOUT_MINUTES;
    }
    
    private long getReportTimeoutMillis() {
        Integer timeout = (reportTimeoutMinutes != null) ? reportTimeoutMinutes : DEFAULT_REPORT_TIMEOUT_MINUTES;
        return (long) timeout * 60 * 1000;
    }

    private void log(final PrintStream logger, final String message) {
        logger.println(StringUtils.defaultString("[" + getDescriptor().getDisplayName()) + "] " + message);
    }

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {
        final PrintStream console = listener.getLogger();

        // check if this step needs to run
        if (Result.ABORTED.equals(run.getResult())) {
            log(console, "Cancelling uploads to zScan as build was aborted");
            return;
        }

        if (Result.FAILURE.equals(run.getResult())) {
            log(console, "Cancelling uploads to zScan as build failed");
            return;
        }

        // Check if there is anything to upload
        final Map<String, String> envVars = run.getEnvironment(listener);

        final String expanded = Util.replaceMacro(sourceFile, envVars);
        final String excluded = Util.replaceMacro(excludedFile, envVars);

        // if we did not find anything to upload, return error
        if (expanded == null) {
            log(console, "Found nothing to upload!");
            run.setResult(DEFAULT_STEP_RESULT);
            return;
        }

        String effectiveEndpoint = (useOwnConsoleInfo) ? endpoint : getDescriptor().getGlobalEndpoint();
        String effectiveClientId = (useOwnConsoleInfo) ? clientId : getDescriptor().getGlobalClientId();
        Secret effectiveClientSecret = (useOwnConsoleInfo) ? clientSecret : getDescriptor().getGlobalClientSecret();
        Boolean effectiveUseProxy = (useOwnConsoleInfo) ? useProxy : getDescriptor().getGlobalUseProxy();

        // create HTTP helper objects
        OkHttpClient okHttpClient = new ZDevHTTPClient().getHttpClient(effectiveUseProxy, connectionTimeout, writeTimeout, readTimeout);

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(effectiveEndpoint)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        UploadPluginService service = retrofit.create(UploadPluginService.class);

        // Login and obtain a token
        Call<LoginResponse> loginResponseCall = service.login(new LoginCredentials(effectiveClientId, Secret.toString(effectiveClientSecret)));
        Response<LoginResponse> response = loginResponseCall.execute();
        LoginResponse loginResponseBody = response.body();

        if (!response.isSuccessful() || loginResponseBody == null) {
            log(console, "Unable to login with provided the credentials to " + effectiveEndpoint);
            run.setResult(DEFAULT_STEP_RESULT);
            return;
        }

        String accessToken = loginResponseBody.getAccessToken();
        String refreshToken = loginResponseBody.getRefreshToken();
        String authToken = "Bearer " + accessToken;

        int totalCount = 0;
        for (String startPath : expanded.split(",")) {
            for (FilePath path : workspace.list(startPath, excluded)) {

                // We don't support uploading directories
                if (path.isDirectory()) {
                    log(console, path.getBaseName() + " is a directory. Skipping.");
                    continue;
                }

                String fileName = path.getName();

                FilePath localPath = null;
                if(path.isRemote()) {
                    // copy locally
                    localPath = new FilePath(File.createTempFile(fileName, null));
                    localPath.copyFrom(path);
                }
                else {
                    localPath = path;
                }

                File file = new File(localPath.getRemote());
                log(console, "Uploading " + fileName + " (" + file.getAbsolutePath() + ") to " + effectiveEndpoint);

                String branchName = (envVars.get("BRANCH_NAME") != null) ? envVars.get("BRANCH_NAME") : "";
                String buildNumber = (envVars.get("BUILD_NUMBER") != null) ? envVars.get("BUILD_NUMBER") : "";

                MultipartBody.Builder multipartBody = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("ciToolId", toolId)
                        .addFormDataPart("ciToolName", toolName)
                        .addFormDataPart("branchName", branchName)
                        .addFormDataPart("buildNumber", buildNumber)
                        .addFormDataPart("buildFile", fileName, RequestBody.create(file, MediaType.parse("multipart/form-data")));
                RequestBody requestBody = multipartBody.build();

                long start = System.currentTimeMillis();
                Response<ResponseBody> uploadResponse = null;
                for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                    Call<ResponseBody> uploadCall = service.upload(authToken, requestBody);
                    uploadResponse = uploadCall.execute();
                    if (uploadResponse.isSuccessful() || !shouldRetryForUpload(uploadResponse.code()) || attempt >= MAX_RETRIES) {
                        break;
                    }

                    log(console, "Transient upload error (HTTP " + uploadResponse.code() + ") while uploading " + fileName + ". Retry " + attempt + "/" + MAX_RETRIES + "...");
                    waitBeforeRetry(console, "upload");
                }
                long end = System.currentTimeMillis();

                if (uploadResponse != null && uploadResponse.isSuccessful()) {
                    log(console, "Successfully uploaded " + fileName + " to " + effectiveEndpoint + " (" + (end - start) + "ms)");
                    try(ResponseBody uploadResponseBody = uploadResponse.body()) {
                        // we're inside the try() block; exceptions will be caught  
                        JsonObject jsonObject = JsonParser.parseString(uploadResponseBody.string()).getAsJsonObject();

                        // Extract the appId needed for team assignment, buildId to check report status, and the current team 
                        String zdevAppId = (jsonObject.get("zdevAppId").isJsonNull()) ? "" : jsonObject.get("zdevAppId").getAsString();
                        String teamId = (jsonObject.get("teamId").isJsonNull()) ? "" : jsonObject.get("teamId").getAsString();
                        String buildId = (jsonObject.get("buildId").isJsonNull()) ? "" : jsonObject.get("buildId").getAsString();

                        // If teamID is empty, find the correct team id by name
                        if(teamId.isEmpty()) {
                            log(console, "Application " + zdevAppId + " does not belong to a team. Assigning it to the " + teamName + " team.");

                            // need to wait a bit; otherwise we can get 404
                            synchronized(this) {
                                wait(checkInterval);
                            }

                            // get the list of teams to figure out team id
                            Call<ResponseBody> teamsCall = service.listTeams(authToken);
                            Response<ResponseBody> teamList = teamsCall.execute();
                            try {
                                ResponseBody teamListBody = teamList.body();
                                if(teamList.isSuccessful() && teamListBody != null) {
                                    JsonObject teamsObject = JsonParser.parseString(teamListBody.string()).getAsJsonObject();
                                    if(teamsObject != null && !teamsObject.isJsonNull() && !teamsObject.isEmpty() && teamsObject.get("content").isJsonArray()) {
                                        JsonArray teamArray = teamsObject.get("content").getAsJsonArray();
                                        log(console, "Found " + teamArray.size() + " teams");
                                        for (JsonElement teamElement : teamArray) {
                                            String name = teamElement.getAsJsonObject().get("name").getAsString();
                                            // log(console, "Team " + name);
                                            if(name.equals(teamName)){
                                                teamId = teamElement.getAsJsonObject().get("id").getAsString();
                                                //log(console, "Found team with ID: " + teamId);
                                                break;
                                            }
                                        }
                                        
                                        // if we did not find the specified team, try 'Default'
                                        if(teamId.isEmpty() && !teamName.equals("Default")) {
                                            log(console, "Team " + teamName + " not found.  Trying the 'Default' team.");
                                            for (JsonElement teamElement : teamArray) {
                                                String name = teamElement.getAsJsonObject().get("name").getAsString();
                                                // log(console, "Team " + name);
                                                if(name.equals("Default")){
                                                    teamId = teamElement.getAsJsonObject().get("id").getAsString();
                                                    log(console, "Found team with ID: " + teamId);
                                                    break;
                                                }
                                            }
                                        }

                                        // Assign the app to the team
                                        if(!teamId.isEmpty()) {
                                            // create payload in the json format {"teamId": ""}
                                            JsonObject jsonPayload = new JsonObject();
                                            jsonPayload.addProperty("teamId", teamId);

                                            RequestBody assignRequestBody = RequestBody.create(jsonPayload.toString(), MediaType.parse("application/json"));
                                            Call<ResponseBody> assignCall = service.assignTeam(zdevAppId, authToken, assignRequestBody);
                                            Response<ResponseBody> assignResponse = assignCall.execute();

                                            if(assignResponse.isSuccessful()) {
                                                log(console, "Successfully assigned application to team.");
                                            }
                                            else {
                                                log(console, "Unable to assign this app to a team.  Please review team name setting and retry.");
                                                ResponseBody assignResponseBody = assignResponse.errorBody();
                                                if(assignResponseBody != null) {
                                                    log(console, "HTTP " + assignResponse.code() + ": " + assignResponseBody.string());
                                                }
                                            }
                                        }
                                        else {
                                            log(console, "Unable to assign this app to a team.  Please review team name setting and retry.");
                                        }
                                    }
                                    else {
                                        log(console, "Unable to assign this app to a team.  Unexpected response from the server.");
                                        teamListBody = teamList.body();
                                        if(teamListBody != null) {
                                            log(console, "HTTP " + teamList.code() + ": " + teamListBody.string());
                                        }
                                    }
                                }
                                else {
                                    log(console, "Unable to assign this app to a team.  Please review team name setting and credentials, and retry.");
                                    teamListBody = teamList.errorBody();
                                    if(teamListBody != null) {
                                        log(console, "HTTP " + teamList.code() + ": " + teamListBody.string());
                                    }
                                }
                            }
                            catch(RuntimeException e) {
                                log(console, "Unexpected runtime exception: " + e.getLocalizedMessage());
                                throw e;
                            }
                            catch(Exception e) {
                                log(console, "Error processing team list: " + e.getLocalizedMessage());
                                run.setResult(Result.UNSTABLE);
                            }
                        }
                        else {
                            log(console, "Application " + zdevAppId + " already belongs to team " + teamId );
                        }

                        // report may have taken a long time; refresh the access token
                        Call<LoginResponse> refreshTokenCall = service.refreshAccess(new RefreshCredentials(refreshToken));
                        Response<LoginResponse> tokenResponse = refreshTokenCall.execute();
                        LoginResponse tokenResponseBody = tokenResponse.body();
                        if (!tokenResponse.isSuccessful() || tokenResponseBody == null) {
                            log(console, "Unable to refresh access token. Will continue using the original.");
                        }
                        else {
                            String newAccessToken = tokenResponseBody.getAccessToken();
                            refreshToken = tokenResponseBody.getRefreshToken();
                            authToken = "Bearer " + newAccessToken;
                        }
                        
                        String assessmentId = "";
                        // wait for the report, if configured
                        if(waitForReport) {
                            start = System.currentTimeMillis();
                            end = start + getReportTimeoutMillis();
                            while( System.currentTimeMillis() < end ) {
                                Call<ResponseBody> statusCall = service.checkStatus(buildId, authToken);
                                Response<ResponseBody> statusResponse = statusCall.execute();
                                if(statusResponse.isSuccessful()) {
                                    try(ResponseBody statusBody = statusResponse.body()) {
                                        // we're inside the try() block; exceptions will be caught
                                        JsonObject statusObject = JsonParser.parseString(statusBody.string()).getAsJsonObject();
                                        String scanStatus = statusObject.getAsJsonObject("zdevMetadata").get("analysis").getAsString();
                                        log(console, "Scan status = " + scanStatus);

                                        if(scanStatus.equals("Done")) {
                                            assessmentId = statusObject.get("id").getAsString();
                                            // need to pause before continuing to make sure reports are available
                                            log(console, "Waiting for the report to become available...");
                                            synchronized(this) {
                                                wait(checkInterval);
                                            }
                                            break;
                                        }
                                    }
                                    catch(Exception e) {
                                        log(console, "Unexpected exception: " + e.getLocalizedMessage());
                                        run.setResult(Result.UNSTABLE);
                                        break;
                                    }
                                }
                                else if (statusResponse.code() != 404) {
                                    log(console, "Unable to get assessment report. Please check credentials and try again.");
                                    ResponseBody statusBody = statusResponse.errorBody();
                                    if(statusBody != null) {
                                        log(console, "HTTP " + statusResponse.code() + ": " + statusBody.string());
                                    }
                                    run.setResult(Result.UNSTABLE);
                                    // move on to the next one
                                    break;
                                }
                                
                                synchronized(this) {
                                    wait(checkInterval);
                                }
                            }

                            // report may have taken a long time; refresh the access token
                            refreshTokenCall = service.refreshAccess(new RefreshCredentials(refreshToken));
                            tokenResponse = refreshTokenCall.execute();
                            tokenResponseBody = tokenResponse.body();
                    
                            if (!tokenResponse.isSuccessful() || tokenResponseBody == null) {
                                log(console, "Unable to refresh access token. Will continue using the original.");
                            }
                            else {

                                String newAccessToken = tokenResponseBody.getAccessToken();
                                refreshToken = tokenResponseBody.getRefreshToken();
                                authToken = "Bearer " + newAccessToken;
                            }

                            if(assessmentId.isEmpty()) {
                                // we've timed out waiting for report
                                log(console, "Timeout (" + reportTimeoutMinutes + " min) waiting for the report; moving on. Report can be downloaded from the console after the scan is complete.");
                            }
                            else {
                                // get the actual report
                                log(console, "Retrieving report for assessment " + assessmentId);
                                String requestedReportFormatString = getReportFormat().getDescription().toLowerCase();
                                String evalReportFormatString = "json";
                                JsonObject evalReport = null;

                                try {
                                    String jsonReportContent = downloadReportContent(service, assessmentId, evalReportFormatString, authToken, console);
                                    if(jsonReportContent != null) {
                                        evalReport = JsonParser.parseString(jsonReportContent).getAsJsonObject();
                                        log(console, "Parsed evaluation report JSON for assessment " + assessmentId);

                                        if("json".equals(requestedReportFormatString)) {
                                            saveReportContentToWorkspace(jsonReportContent, assessmentId, requestedReportFormatString, reportFileName, workspace, console);
                                        }
                                    }
                                }
                                catch(Exception e) {
                                    log(console, "Unable to download or parse evaluation JSON report: " + e.getLocalizedMessage());
                                    run.setResult(Result.UNSTABLE);
                                }

                                if(!"json".equals(requestedReportFormatString)) {
                                    try {
                                        String reportContent = downloadReportContent(service, assessmentId, requestedReportFormatString, authToken, console);
                                        if(reportContent != null) {
                                            saveReportContentToWorkspace(reportContent, assessmentId, requestedReportFormatString, reportFileName, workspace, console);
                                        }
                                    }
                                    catch(Exception e) {
                                        log(console, "Unable to download or save requested report format " + requestedReportFormatString + ": " + e.getLocalizedMessage());
                                        run.setResult(Result.UNSTABLE);
                                    }
                                }

                                if(evalReport != null) {
                                    ScanSummary summary = summarizeScanReport(evalReport);
                                    if(summary != null) {
                                        log(console, "Scan Summary for assessment " + assessmentId + ":");
                                        for(Severity severity : Severity.values()) {
                                            int total = summary.getTotalForSeverity(severity);
                                            int unaccepted = summary.getUnacceptedForSeverity(severity);
                                            if(total > 0 | unaccepted > 0) {
                                                log(console, "  " + severity.getDescription() + ": total=" + total + ", unaccepted=" + unaccepted);
                                            }
                                        }

                                        if(getEvaluateBuildStatusOnScanFindings()) {
                                            if(summary.matchesCriteria(getScanEvaluationMode(), getMinimumSeverity())) {
                                                Result action = getScanStatusAction() == ScanStatusAction.FAILURE ? Result.FAILURE : Result.UNSTABLE;
                                                if(run.getResult() == null || Result.SUCCESS.equals(run.getResult()) || Result.UNSTABLE.equals(run.getResult())) {
                                                    run.setResult(action);
                                                }
                                                log(console, "Build status set to " + action.toString() + " based on scan evaluation criteria.");
                                            }
                                            else {
                                                log(console, "Build status gating criteria not met for assessment " + assessmentId + ".");
                                            }
                                        }
                                        else {
                                            log(console, "Build status evaluation is disabled for scan findings.");
                                        }
                                    }
                                    else {
                                        log(console, "Unable to summarize scan results for assessment " + assessmentId + ".");
                                    }
                                }
                            }
                        }
                    }
                    catch(RuntimeException e) {
                        log(console, "Unexpected runtime exception: " + e.getLocalizedMessage());
                        throw e;
                    }
                    catch(Exception e) {
                        log(console, "Unexpected exception: " + e.getLocalizedMessage());
                        run.setResult(Result.UNSTABLE);
                    }

                    totalCount++;
                } else {
                    int uploadStatusCode = uploadResponse != null ? uploadResponse.code() : -1;
                    log(console, "An error (HTTP " + uploadStatusCode + ") occurred while trying to upload " + fileName + " to " + effectiveEndpoint);
                    ResponseBody uploadResponseBody = uploadResponse != null ? uploadResponse.errorBody() : null;
                    if(uploadResponseBody != null) {
                        log(console, "Error message: " + uploadResponseBody.string());
                    }
                    run.setResult(Result.UNSTABLE);
                }

                // delete the local copy if needed
                if(path.isRemote()) {
                    if(!localPath.delete()) {
                        log(console, "Unable to delete temporary file " + localPath.getRemote());
                    }
                }

                // safety check
                if(totalCount >= MAX_FILES_UPLOAD) {
                    break;
                }
            }
        }
        log(console, totalCount + " file(s) were uploaded.");
        if (run.getResult() == null || Result.SUCCESS.equals(run.getResult())) {
            run.setResult(Result.SUCCESS);
        }
    }

    public static boolean shouldRetryForUpload(int responseCode) {
        return responseCode == 500 || responseCode == 502 || responseCode == 503 || responseCode == 504;
    }

    public static boolean shouldRetryForDownload(int responseCode) {
        return responseCode == 404 || shouldRetryForUpload(responseCode);
    }

    private void waitBeforeRetry(PrintStream console, String operation) throws InterruptedException {
        log(console, "Waiting " + (checkInterval / 1000) + "s before retrying " + operation + "...");
        synchronized (this) {
            wait(checkInterval);
        }
    }

    private String downloadReportContent(UploadPluginService service, String assessmentId, String reportFormat, String authToken, PrintStream console) throws IOException, InterruptedException {
        Response<ResponseBody> reportResponse = null;
        Call<ResponseBody> reportCall = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            reportCall = service.downloadReport(assessmentId, reportFormat, authToken);
            reportResponse = reportCall.execute();
            if (reportResponse.isSuccessful() || !shouldRetryForDownload(reportResponse.code()) || attempt >= MAX_RETRIES) {
                break;
            }

            log(console, "Report download returned HTTP " + reportResponse.code() + ". Retry " + attempt + "/" + MAX_RETRIES + "...");
            waitBeforeRetry(console, "report download");
        }

        ResponseBody reportResponseBody = reportResponse != null ? reportResponse.body() : null;
        if (reportResponse != null && reportResponse.isSuccessful() && reportResponseBody != null) {
            try(InputStream inputStream = reportResponseBody.byteStream()) {
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        else {
            log(console, "Report failed to download: HTTP" + (reportResponse != null ? reportResponse.code() : "?") + ": " + (reportCall != null ? reportCall.request().url() : "unknown"));
        }
        return null;
    }

    private void saveReportContentToWorkspace(String content, String assessmentId, String reportFormat, String reportFileName, FilePath workspace, PrintStream console) throws IOException, InterruptedException {
        String extension = reportFormat;
        File reportFile = File.createTempFile("zScan-report-", "." + extension);
        try (OutputStream outputStream = new FileOutputStream(reportFile)) {
            outputStream.write(content.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        }

        String effectiveReportFileName = (reportFileName == null || reportFileName.isEmpty()) ?
                "zScan-report-" + assessmentId + "." + extension :
                FilenameUtils.removeExtension(reportFileName) + "-" + assessmentId + "." + FilenameUtils.getExtension(reportFileName);

        try {
            FilePath reportPath = new FilePath(workspace, effectiveReportFileName);
            reportPath.copyFrom(new FilePath(reportFile));
            log(console, "Written " + reportFile.length() + " bytes to file " + reportPath.getRemote());
        }
        finally {
            if(!reportFile.delete()) {
                log(console, "Unable to delete temporary file " + reportFile.getAbsolutePath());
            }
        }
    }

    private ScanSummary summarizeScanReport(JsonObject report) {
        ScanSummary summary = new ScanSummary();

        if(report == null || report.isJsonNull()) {
            return summary;
        }

        if(report.has("findings") && report.get("findings").isJsonArray()) {
            for(JsonElement element : report.getAsJsonArray("findings")) {
                if(element.isJsonObject()) {
                    summary.addFinding(parseSeverity(element.getAsJsonObject()), parseIsAcceptedFinding(element.getAsJsonObject()));
                }
            }
            return summary;
        }

        if(report.has("issues") && report.get("issues").isJsonArray()) {
            for(JsonElement element : report.getAsJsonArray("issues")) {
                if(element.isJsonObject()) {
                    summary.addFinding(parseSeverity(element.getAsJsonObject()), parseIsAcceptedFinding(element.getAsJsonObject()));
                }
            }
            return summary;
        }

        if(report.has("runs") && report.get("runs").isJsonArray()) {
            for(JsonElement runElement : report.getAsJsonArray("runs")) {
                if(runElement.isJsonObject()) {
                    JsonObject runObject = runElement.getAsJsonObject();
                    if(runObject.has("results") && runObject.get("results").isJsonArray()) {
                        for(JsonElement resultElement : runObject.getAsJsonArray("results")) {
                            if(resultElement.isJsonObject()) {
                                summary.addFinding(parseSeverity(resultElement.getAsJsonObject()), parseIsAcceptedFinding(resultElement.getAsJsonObject()));
                            }
                        }
                    }
                }
            }
            return summary;
        }

        return summary;
    }

    private Severity parseSeverity(JsonObject finding) {
        if(finding == null || finding.isJsonNull()) {
            return Severity.UNKNOWN;
        }

        if(finding.has("severity") && finding.get("severity").isJsonPrimitive()) {
            return Severity.fromString(finding.get("severity").getAsString());
        }

        if(finding.has("severityOrdinal") && finding.get("severityOrdinal").isJsonPrimitive()) {
            try {
                return Severity.fromOrdinal(finding.get("severityOrdinal").getAsInt());
            }
            catch(NumberFormatException ignored) {
                // fall through to other fields
            }
        }

        return Severity.UNKNOWN;
    }

    private boolean parseIsAcceptedFinding(JsonObject finding) {
        if(finding == null || finding.isJsonNull()) {
            return false;
        }

        if(finding.has("accepted_status") && finding.get("accepted_status").isJsonPrimitive()) {
            return finding.get("accepted_status").getAsBoolean();
        }

        return true;
    }

    private static class ScanSummary {
        private final Map<Severity, Integer> totalBySeverity = new HashMap<>();
        private final Map<Severity, Integer> unacceptedBySeverity = new HashMap<>();

        public void addFinding(Severity severity, boolean isAccepted) {
            totalBySeverity.put(severity, totalBySeverity.getOrDefault(severity, 0) + 1);
            if(!isAccepted) {
                unacceptedBySeverity.put(severity, unacceptedBySeverity.getOrDefault(severity, 0) + 1);
            }
        }

        public int getTotalForSeverity(Severity severity) {
            return totalBySeverity.getOrDefault(severity, 0);
        }

        public int getUnacceptedForSeverity(Severity severity) {
            return unacceptedBySeverity.getOrDefault(severity, 0);
        }

        public boolean matchesCriteria(ScanEvaluationMode mode, Severity threshold) {
            for(Severity severity : Severity.values()) {
                if(severity == Severity.BEST_PRACTICES) {
                    continue;
                }
                if(severity.getRank() < threshold.getRank()) {
                    continue;
                }
                int count = (mode == ScanEvaluationMode.UNACCEPTED_FINDING_ONLY) ? getUnacceptedForSeverity(severity) : getTotalForSeverity(severity);
                if(count > 0) {
                    return true;
                }
            }
            return false;
        }
    }

    public static enum ScanStatusAction {
        UNSTABLE("Unstable"),
        FAILURE("Failure");

        private final String description;

        ScanStatusAction(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public static enum ScanEvaluationMode {
        ANY_FINDING("Any findings"),
        UNACCEPTED_FINDING_ONLY("Unaccepted findings only");

        private final String description;

        ScanEvaluationMode(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public static enum Severity {
        CRITICAL(4, "Critical"),
        HIGH(3, "High"),
        MEDIUM(2, "Medium"),
        LOW(1, "Low"),
        INFORMATIONAL(0, "Informational"),
        BEST_PRACTICES(-2, "Best Practices"),
        UNKNOWN(-1, "Unknown");

        private final int rank;
        private final String description;

        Severity(int rank, String description) {
            this.rank = rank;
            this.description = description;
        }

        public int getRank() {
            return rank;
        }

        public String getDescription() {
            return description;
        }

        public static Severity fromString(String value) {
            if(value == null) {
                return UNKNOWN;
            }
            switch(value.trim().toLowerCase()) {
                case "critical":
                case "crit":
                    return CRITICAL;
                case "high":
                    return HIGH;
                case "medium":
                case "med":
                    return MEDIUM;
                case "low":
                    return LOW;
                case "informational":
                case "info":
                    return INFORMATIONAL;
                case "best practices":
                case "best_practices":
                case "best-practices":
                    return BEST_PRACTICES;
                default:
                    return UNKNOWN;
            }
        }

        public static Severity fromOrdinal(int ordinal) {
            switch(ordinal) {
                case 5:
                    return BEST_PRACTICES;
                case 4:
                    return CRITICAL;
                case 3:
                    return HIGH;
                case 2:
                    return MEDIUM;
                case 1:
                    return LOW;
                case 0:
                    return INFORMATIONAL;
                default:
                    return UNKNOWN;
            }
        }
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    public static enum ReportFormat {

        JSON("JSON"),
        SARIF("SARIF");

        private final String description;
        
        ReportFormat(String description) {
            this.description = description;
        }
        public String getDescription() {
            return description;
        }
    }

    @Extension
    @Symbol("zScanUpload")
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        
        private String globalEndpoint;
        private String globalClientId;
        private Secret globalClientSecret;
        private Boolean globalUseProxy;

        public DescriptorImpl() {
            load();
        }

        @SuppressWarnings("rawtypes")
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        public String getDisplayName() {
            return "Upload build artifacts to zScan";
        }

        @Override
        public boolean configure(StaplerRequest2 req, JSONObject formData) throws FormException {
            JSONObject zscanData = formData.getJSONObject("zscan");
            req.bindJSON(this, zscanData); 
            save();
            return super.configure(req, formData);
        }

        public void setGlobalEndpoint(String globalEndpoint) {
            this.globalEndpoint = globalEndpoint;
        }
        public String getGlobalEndpoint() {
            return globalEndpoint;
        }

        public void setGlobalClientId(String globalClientId) {
            this.globalClientId = globalClientId;
        }
        public String getGlobalClientId() {
            return globalClientId;
        }

        public void setGlobalClientSecret(Secret globalClientSecret) {
            this.globalClientSecret = globalClientSecret;
        }
        public Secret getGlobalClientSecret() {
            return globalClientSecret;
        }

        public void setGlobalUseProxy(Boolean globalUseProxy) {
            this.globalUseProxy = globalUseProxy;
        }
        public Boolean getGlobalUseProxy() {
            return globalUseProxy;
        }

        public String getDefaultReportFileName() {
            return DEFAULT_REPORT_FILE;
        }

        public String getDefaultTeamName() {
            return DEFAULT_TEAM_NAME;
        }

        public Integer getDefaultReportTimeoutMinutes() {
            return DEFAULT_REPORT_TIMEOUT_MINUTES;
        }

        // Validate credentials by trying to obtain access token
        // This method can be executed by anyone with job configuration permission
        // Only the response code is checked
        @POST
        public FormValidation doValidateCredentials(
            @QueryParameter("endpoint") final String endpoint,
            @QueryParameter("clientId") final String clientId, 
            @QueryParameter("clientSecret") final Secret clientSecret,
            @QueryParameter("useProxy") final Boolean useProxy,
            @AncestorInPath Item item) {

            return checkConsoleInformation(item, endpoint, clientId, globalClientSecret, useProxy);
        }

        // Validate global credentials by trying to obtain access token
        // This method can be executed by anyone with job configuration permission
        // Only the response code is checked
        @POST
        public FormValidation doValidateGlobalCredentials(
            @QueryParameter("globalEndpoint") final String globalEndpoint,
            @QueryParameter("globalClientId") final String globalClientId, 
            @QueryParameter("globalClientSecret") final Secret globalClientSecret,
            @QueryParameter("globalUseProxy") final Boolean globalUseProxy,
            @AncestorInPath Item item) {

            return checkConsoleInformation(item, globalEndpoint, globalClientId, globalClientSecret, globalUseProxy);
        }

        private FormValidation checkConsoleInformation(Item item, String endpoint, String clientId, Secret clientSecret, Boolean useProxy) {
            if (StringUtils.isBlank(endpoint)) {
                return FormValidation.error("Endpoint is required");
            }
            if (StringUtils.isBlank(clientId)) {
                return FormValidation.error("Client ID is required");
            }
            if (clientSecret == null || StringUtils.isBlank(Secret.toString(clientSecret))) {
                return FormValidation.error("Client Secret is required");
            }

            try {
                if(item == null){
                    Jenkins.get().checkPermission(Jenkins.ADMINISTER);
                }else {
                    item.checkPermission(Item.CONFIGURE);
                }

                OkHttpClient okHttpClient = new ZDevHTTPClient().getHttpClient(useProxy, connectionTimeout, writeTimeout, readTimeout);

                Retrofit retrofit = new Retrofit.Builder()
                        .baseUrl(endpoint)
                        .client(okHttpClient)
                        .addConverterFactory(GsonConverterFactory.create())
                        .build();

                UploadPluginService service = retrofit.create(UploadPluginService.class);

                Call<LoginResponse> loginResponseCall = service.login(new LoginCredentials(clientId, Secret.toString(clientSecret)));
                Response<LoginResponse> response = loginResponseCall.execute();

                if (!response.isSuccessful() || response.body() == null) {
                    return FormValidation.error("Unable to login with provided Client ID and Client Secret to " + endpoint);
                }
            }
            catch(hudson.security.AccessDeniedException3 e) {
                return FormValidation.error("User not authorized to check credentials.");
            }
            catch(java.net.UnknownHostException e) {
                return FormValidation.error("Unknown host: " + endpoint);
            }
            catch(java.io.IOException e) {
                return FormValidation.error("Unable to connect to the provided endpoint: " + endpoint + "(" + e.getLocalizedMessage() + ")");
            }
            catch(Exception e) {
                return FormValidation.error("Error validating credentials: " + e.getLocalizedMessage());
            }

            // If we get here, the credentials are valid        
            return FormValidation.ok("Credentials are valid");
        }
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.STEP;
    }
}

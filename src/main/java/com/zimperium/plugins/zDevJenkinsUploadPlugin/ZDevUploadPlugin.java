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
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import hudson.util.Secret;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;
import okhttp3.*;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.io.FilenameUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
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
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletException;


public class ZDevUploadPlugin extends Recorder implements SimpleBuildStep{

    // constants
    public final static String toolId = "JKNS";
    public final static String toolName = "Jenkins";
    public final static long checkInterval = 30;
    public final static int reportTimeout = 1200;

    public final static Result DEFAULT_STEP_RESULT = Result.FAILURE;
    public final static int MAX_FILES_UPLOAD = 5;
    public final static String DEFAULT_REPORT_FILE = "zscan-report.json";
    public final static String DEFAULT_TEAM_NAME = "Default";

    // plugin settings
    // mandatory
    public String sourceFile;
    public String excludedFile;
    public String endpoint;
    public String clientId;
    public Secret clientSecret;

    // optional
    private Boolean waitForReport;
    private String reportFormat;
    private String reportFileName;
    private String teamName;   

    @DataBoundConstructor
    public ZDevUploadPlugin(String sourceFile, String excludedFile, String endpoint, String clientId, Secret clientSecret) {
        this.sourceFile = sourceFile;
        this.excludedFile = excludedFile;
        this.endpoint = endpoint;
        this.clientId = clientId;
        this.clientSecret = clientSecret;

        this.waitForReport = false;
        this.reportFormat = "sarif";
        this.reportFileName = DEFAULT_REPORT_FILE;
        this.teamName = DEFAULT_TEAM_NAME; 
    }

    @DataBoundSetter
    public void setWaitForReport(Boolean waitForReport) {
        this.waitForReport = waitForReport;
    }

    public Boolean getWaitForReport() {
        return waitForReport;
    }

    @DataBoundSetter
    public void setReportFormat(String reportFormat) {
        this.reportFormat = reportFormat;
    }

    public String getReportFormat() {
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
    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    public String getTeamName() {
        return teamName;
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

        // create HTTP helper objects
        OkHttpClient okHttpClient = new OkHttpClient().newBuilder()
                .writeTimeout(2, TimeUnit.MINUTES)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(this.endpoint)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        UploadPluginService service = retrofit.create(UploadPluginService.class);

        final Map<String, String> envVars = run.getEnvironment(listener);

        final String expanded = Util.replaceMacro(sourceFile, envVars);
        final String excluded = Util.replaceMacro(excludedFile, envVars);

        // if we did not find anything to upload, return error
        if (expanded == null) {
            log(console, "Found nothing to upload!");
            run.setResult(DEFAULT_STEP_RESULT);
            return;
        }

        // Login and obtain a token
        Call<LoginResponse> loginResponseCall = service.login(new LoginCredentials(this.clientId, Secret.toString(this.clientSecret)));
        Response<LoginResponse> response = loginResponseCall.execute();

        if (!response.isSuccessful() || response.body() == null) {
            log(console, "Unable to login with provided Client ID and Client Secret to " + this.endpoint);
            run.setResult(DEFAULT_STEP_RESULT);
            return;
        }

        @SuppressWarnings("null") // there's a null check above
        String accessToken = response.body().getAccessToken();
        @SuppressWarnings("null")
        String refreshToken = response.body().getRefreshToken();
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
                File file = new File(path.getRemote());

                log(console, "Uploading " + fileName + " to " + this.endpoint);

                String branchName = (envVars.get("BRANCH_NAME") != null) ? envVars.get("BRANCH_NAME") : "";
                String buildNumber = (envVars.get("BUILD_NUMBER") != null) ? envVars.get("BUILD_NUMBER") : "";

                MultipartBody.Builder multipartBody = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("ciToolId", toolId)
                        .addFormDataPart("ciToolName", toolName)
                        .addFormDataPart("branchName", branchName)
                        .addFormDataPart("buildNumber", buildNumber)
                        .addFormDataPart("buildFile", fileName, RequestBody.create(MediaType.parse("multipart/form-data"), file));
                RequestBody requestBody = multipartBody.build();
                Call<ResponseBody> uploadCall = service.upload(authToken, requestBody);

                long start = System.currentTimeMillis();
                Response<ResponseBody> uploadResponse = uploadCall.execute();
                long end = System.currentTimeMillis();

                if (uploadResponse.isSuccessful()) {
                    log(console, "Successfully uploaded " + fileName + " to " + this.endpoint + " (" + (end - start) + "ms)");
                    try(ResponseBody uploadResponseBody = uploadResponse.body()) {
                        // we're inside the try() block; exceptions will be caught  
                        @SuppressWarnings("null")
                        JsonObject jsonObject = JsonParser.parseString(uploadResponseBody.string()).getAsJsonObject();

                        // Extract the appId needed for team assignment, buildId to check report status, and the current team 
                        String zdevAppId = (jsonObject.get("zdevAppId").isJsonNull()) ? "" : jsonObject.get("zdevAppId").getAsString();
                        String teamId = (jsonObject.get("teamId").isJsonNull()) ? "" : jsonObject.get("teamId").getAsString();
                        String buildId = (jsonObject.get("buildId").isJsonNull()) ? "" : jsonObject.get("buildId").getAsString();

                        // If teamID is empty, find the correct team id by name
                        if(teamId.isEmpty()) {
                            log(console, "Application " + zdevAppId + " does not belong to a team. Assigning it to the " + teamName + " team.");
                            // get the list of teams to figure out team id
                            Call<ResponseBody> teamsCall = service.listTeams(authToken);
                            Response<ResponseBody> teamList = teamsCall.execute();
                            try {
                                if(teamList.isSuccessful() && teamList.body() != null) {
                                    // There's a null check above
                                    @SuppressWarnings("null")
                                    JsonObject teamsObject = JsonParser.parseString(teamList.body().string()).getAsJsonObject();
                                    if(!teamsObject.isJsonNull() && !teamsObject.isEmpty() && teamsObject.get("content").isJsonArray()) {
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

                                            RequestBody assignRequestBody = RequestBody.create(MediaType.parse("application/json"), jsonPayload.toString());
                                            Call<ResponseBody> assignCall = service.assignTeam(zdevAppId, authToken, assignRequestBody);
                                            Response<ResponseBody> assignResponse = assignCall.execute();

                                            if(assignResponse.isSuccessful()) {
                                                log(console, "Successfully assigned application to team.");
                                            }
                                            else {
                                                log(console, "Unable to assign this app to a team.  Please review team name setting and retry.");
                                                if(assignResponse.errorBody() != null) {
                                                    log(console, "HTTP " + assignResponse.code() + ": " + assignResponse.errorBody());
                                                }
                                            }
                                        }
                                        else {
                                            log(console, "Unable to assign this app to a team.  Please review team name setting and retry.");
                                        }
                                    }
                                }
                            }
                            catch(RuntimeException e) {
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

                        if (!tokenResponse.isSuccessful() || tokenResponse.body() == null) {
                            log(console, "Unable to refresh access token. Will continue using the original.");
                        }
                        else {
                            // there's a null check above
                            @SuppressWarnings("null")
                            String newAccessToken = tokenResponse.body().getAccessToken();
                            refreshToken = tokenResponse.body().getRefreshToken();
                            authToken = "Bearer " + newAccessToken;
                        }
                        
                        String assessmentId = "";
                        // wait for the report, if configured
                        if(waitForReport) {
                            synchronized(this) {
                                start = System.currentTimeMillis();
                                end = start + reportTimeout * 1000;
                                while( System.currentTimeMillis() < end ) {
                                    Call<ResponseBody> statusCall = service.checkStatus(buildId, authToken);
                                    Response<ResponseBody> statusResponse = statusCall.execute();
                                    if(statusResponse.isSuccessful()) {
                                        try(ResponseBody statusBody = statusResponse.body()) {
                                            // we're inside the try() block; exceptions will be caught
                                            @SuppressWarnings("null")
                                            JsonObject statusObject = JsonParser.parseString(statusBody.string()).getAsJsonObject();
                                            String scanStatus = statusObject.getAsJsonObject("zdevMetadata").get("analysis").getAsString();
                                            log(console, "Scan status = " + scanStatus);

                                            if(scanStatus.equals("Done")) {
                                                assessmentId = statusObject.get("id").getAsString();
                                                // need to pause before continuing to make sure reports are available
                                                log(console, "Waiting for the report to become available...");
                                                wait(checkInterval * 1000);
                                                break;
                                            }
                                        }
                                        catch(Exception e) {
                                            log(console, "Unexpected exception: " + e.getLocalizedMessage());
                                            run.setResult(Result.UNSTABLE);
                                            break;
                                        }
                                    }
                                    
                                    wait(checkInterval * 1000);
                                }
                            }

                            // report may have taken a long time; refresh the access token
                            refreshTokenCall = service.refreshAccess(new RefreshCredentials(refreshToken));
                            tokenResponse = refreshTokenCall.execute();
                    
                            if (!tokenResponse.isSuccessful() || tokenResponse.body() == null) {
                                log(console, "Unable to refresh access token. Will continue using the original.");
                            }
                            else {
                                // there's a null check above
                                @SuppressWarnings("null")
                                String newAccessToken = tokenResponse.body().getAccessToken();
                                refreshToken = tokenResponse.body().getRefreshToken();
                                authToken = "Bearer " + newAccessToken;
                            }

                            if(!assessmentId.isEmpty()) {
                                // get the actual report
                                log(console, "Retrieving report for assessment " + assessmentId);

                                // append assessment id to the filename
                                String effectiveReportFileName = FilenameUtils.removeExtension(reportFileName) + "-" + assessmentId + "." + FilenameUtils.getExtension(reportFileName);

                                Call<ResponseBody> reportCall = service.downloadReport(assessmentId, reportFormat, authToken);
                                Response<ResponseBody> reportResponse = reportCall.execute();

                            if(reportResponse.isSuccessful() && reportResponse.body() != null) {
                                    File reportFile = new File(effectiveReportFileName);
                                    // there's a null check above
                                    try(@SuppressWarnings("null")
                                    InputStream inputStream = reportResponse.body().byteStream(); OutputStream outputStream = new FileOutputStream(reportFile); ) {
                                        byte[] buffer = new byte[4096];
                                        long bytesWritten = 0;

                                        while(true) {
                                            int read = inputStream.read(buffer);
                                            if( read == -1 ) {
                                                break;
                                            }

                                            outputStream.write(buffer, 0, read);
                                            bytesWritten += read;
                                        }

                                        outputStream.flush();
                                        log(console, "Written " + bytesWritten + " bytes to file " + reportFile.getAbsolutePath());
                                    }
                                    catch(Exception e) {
                                        log(console, "Unable to write to file " + effectiveReportFileName + ": " + e.getLocalizedMessage());
                                        run.setResult(Result.UNSTABLE);
                                    }
                                }
                                else {
                                    log(console, "Report failed to download: HTTP" + reportResponse.code() + ": " + reportCall.request().url());
                                    run.setResult(Result.UNSTABLE);
                                }
                            }
                        }
                    }
                    catch(RuntimeException e) {
                        throw e;
                    }
                    catch(Exception e) {
                        log(console, "Unexpected exception: " + e.getLocalizedMessage());
                        run.setResult(Result.UNSTABLE);
                    }
                    totalCount++;

                    // safety check
                    if(totalCount >= MAX_FILES_UPLOAD) {
                        break;
                    }
                } else {
                    log(console, "An error (HTTP " + uploadResponse.code() + ") occurred while trying to upload " + fileName + " to " + this.endpoint);
                    if(uploadResponse.errorBody() != null) {
                        // there is a null check above
                        log(console, "Error message: " + uploadResponse.errorBody().string());
                    }
                    run.setResult(Result.UNSTABLE);
                }
            }
        }
        log(console, totalCount + " file(s) were uploaded.");
        run.setResult(Result.SUCCESS);
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        
        private String sourceFile;
        private String excludedFile;
        private String endpoint;
        private String clientId;
        private String clientSecret;
        private Boolean waitForReport;

        // advanced
        public String reportFormat;
        public String reportFileName;
        public String teamName; 
        

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

        // TODO: Add server validation logic
        public FormValidation doCheckEndpoint(@QueryParameter String value) throws IOException, ServletException {
            return FormValidation.ok();
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            sourceFile = formData.getString("sourceFile");
            excludedFile = formData.getString("excludedFile");
            endpoint = formData.getString("endpoint");
            clientId = formData.getString("clientId");
            clientSecret = formData.getString("clientSecret");
            waitForReport = formData.getBoolean("waitForReport");
            // Advanced
            reportFormat = formData.getString("reportFormat");
            reportFileName = formData.getString("reportFileName");
            teamName = formData.getString("teamName");

            save();
            return super.configure(req, formData);
        }

        
        public String getSourceFile() { return sourceFile; }

        public String getExcludedFile() { return excludedFile; }

        public String getEndpoint() {
            return endpoint;
        }

        public String getClientId() {
            return clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public Boolean getWaitForReport() {
            return waitForReport;
        }

        public String getReportFormat() {
            return reportFormat;
        }

        public String getReportFileName() {
            return reportFileName;
        }

        public String getTeamName() {
            return teamName;
        }

        public String getDefaultReportFileName() {
            return DEFAULT_REPORT_FILE;
        }

        public String getDefaultTeamName() {
            return DEFAULT_TEAM_NAME;
        }

        // Validate credentials by trying to obtain access token
        // This method can be executed by anyone since the token is not saved or logged anywhere
        // Only the response code is checked
        @SuppressWarnings("lgtm[jenkins/no-permission-check]")
        @POST
        public FormValidation doValidateCredentials(
            @QueryParameter("endpoint") final String endpoint,
            @QueryParameter("clientId") final String clientId, 
            @QueryParameter("clientSecret") final String clientSecret,
            @AncestorInPath Job<?,?> job) {

            try {
                OkHttpClient okHttpClient = new OkHttpClient().newBuilder()
                    .writeTimeout(2, TimeUnit.MINUTES)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build();

                Retrofit retrofit = new Retrofit.Builder()
                        .baseUrl(endpoint)
                        .client(okHttpClient)
                        .addConverterFactory(GsonConverterFactory.create())
                        .build();

                UploadPluginService service = retrofit.create(UploadPluginService.class);

                Call<LoginResponse> loginResponseCall = service.login(new LoginCredentials(clientId, clientSecret));
                Response<LoginResponse> response = loginResponseCall.execute();

                if (!response.isSuccessful() || response.body() == null) {
                    return FormValidation.error("Unable to login with provided Client ID and Client Secret to " + endpoint);
                }
            }
            catch(java.net.UnknownHostException e) {
                return FormValidation.error("Unknown host: " + endpoint);
            }
            catch(java.io.IOException e) {
                return FormValidation.error("Unable to connect to the provided endpoint: " + endpoint);
            }
            catch(Exception e) {
                return FormValidation.error("Error validating credentials: " + e.getLocalizedMessage());
            }

            return FormValidation.ok("Success");
        }
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.STEP;
    }
}

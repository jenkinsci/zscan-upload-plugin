package com.zimperium.plugins.zDevJenkinsUploadPlugin;

import com.zimperium.plugins.zDevJenkinsUploadPlugin.dtos.LoginCredentials;
import com.zimperium.plugins.zDevJenkinsUploadPlugin.dtos.LoginResponse;
import com.zimperium.plugins.zDevJenkinsUploadPlugin.services.UploadPluginService;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;
import okhttp3.*;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ZDevUploadPlugin extends Recorder implements SimpleBuildStep{
    public String sourceFile;
    public String excludedFile;
    public String endpoint;
    public String clientId;
    public String clientSecret;

    @DataBoundConstructor
    public ZDevUploadPlugin(String sourceFile, String excludedFile, String endpoint, String clientId, String clientSecret) {
        this.sourceFile = sourceFile;
        this.excludedFile = excludedFile;
        this.endpoint = endpoint;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    private void log(final PrintStream logger, final String message) {
        logger.println(StringUtils.defaultString("[" + getDescriptor().getDisplayName()) + "] " + message);
    }

    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws InterruptedException, IOException {
        final PrintStream console = listener.getLogger();

        if (Result.ABORTED.equals(run.getResult())) {
            log(console, "Cancelling uploads to zScan as build was aborted");
            return;
        }

        if (Result.FAILURE.equals(run.getResult())) {
            log(console, "Cancelling uploads to zScan as build failed");
            return;
        }

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

        if (expanded == null) {
            throw new IOException();
        }

        int totalCount = 0;
        for (String startPath : expanded.split(",")) {
            for (FilePath path : workspace.list(startPath, excluded)) {

                if (path.isDirectory()) {
                    throw new IOException(path + " is a directory");
                }

                Call<LoginResponse> loginResponseCall = service.login(new LoginCredentials(this.clientId, this.clientSecret));
                Response<LoginResponse> response = loginResponseCall.execute();

                if (!response.isSuccessful() || response.body() == null) {
                    log(console, "Unable to login with provided Client ID and Client Secret to " + this.endpoint);
                    return;
                }

                String accessToken = response.body().getAccessToken();
                String authToken = "Bearer " + accessToken;

                String fileName = path.getName();
                File file = new File(path.getRemote());

                log(console, "Uploading " + fileName + " to " + this.endpoint);

                MultipartBody.Builder multipartBody = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("buildFile", fileName, RequestBody.create(MediaType.parse("multipart/form-data"), file));

                RequestBody requestBody = multipartBody.build();

                Call<ResponseBody> uploadCall = service.upload(authToken, requestBody);

                long start = System.currentTimeMillis();
                Response<ResponseBody> uploadResponse = uploadCall.execute();
                long end = System.currentTimeMillis();

                if (uploadResponse.isSuccessful()) {
                    log(console, "Successfully uploaded " + fileName + " to " + this.endpoint + " (" + (end - start) + "ms)");
//                    if (uploadResponse.body() != null) {
//                        log(console, uploadResponse.body().string());
//                    }
                    totalCount++;
                } else {
                    log(console, "An error occurred while trying to upload " + fileName + " to " + this.endpoint);
                }
            }
        }
        log(console, totalCount + " file(s) were uploaded.");
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

        public DescriptorImpl() {
            load();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        public String getDisplayName() {
            return "Upload build artifacts to zScan";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            sourceFile = formData.getString("sourceFile");
            excludedFile = formData.getString("excludedFile");
            endpoint = formData.getString("endpoint");
            clientId = formData.getString("clientId");
            clientSecret = formData.getString("clientSecret");

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
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.STEP;
    }
}

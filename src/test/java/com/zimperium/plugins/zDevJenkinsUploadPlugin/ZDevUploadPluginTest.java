package com.zimperium.plugins.zDevJenkinsUploadPlugin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zimperium.plugins.zDevJenkinsUploadPlugin.ZDevUploadPlugin.ReportFormat;

import hudson.util.Secret;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZDevUploadPluginTest {

    @Test
    void uploadRetriesForTransientServerErrors() {
        assertTrue(ZDevUploadPlugin.shouldRetryForUpload(500));
        assertTrue(ZDevUploadPlugin.shouldRetryForUpload(502));
        assertTrue(ZDevUploadPlugin.shouldRetryForUpload(503));
        assertTrue(ZDevUploadPlugin.shouldRetryForUpload(504));
        assertFalse(ZDevUploadPlugin.shouldRetryForUpload(400));
        assertFalse(ZDevUploadPlugin.shouldRetryForUpload(404));
    }

    @Test
    void reportDownloadRetriesForNotFound() {
        assertTrue(ZDevUploadPlugin.shouldRetryForDownload(404));
        assertFalse(ZDevUploadPlugin.shouldRetryForDownload(500));
        assertFalse(ZDevUploadPlugin.shouldRetryForDownload(400));
        assertEquals(3, ZDevUploadPlugin.MAX_RETRIES);
    }

    @Test
    void constructorUsesDefaultValuesWhenOptionalSettingsAreNull() {
        ZDevUploadPlugin plugin = new ZDevUploadPlugin(
                false,
                "https://example.test",
                "client-id",
                Secret.fromString("secret"),
                false,
                "src/**",
                "",
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertEquals(ZDevUploadPlugin.ReportFormat.JSON, plugin.getReportFormat());
        assertEquals(ZDevUploadPlugin.DEFAULT_REPORT_FILE, plugin.getReportFileName());
        assertEquals(ZDevUploadPlugin.DEFAULT_TEAM_NAME, plugin.getTeamName());
        assertEquals(ZDevUploadPlugin.DEFAULT_REPORT_TIMEOUT_MINUTES, plugin.getReportTimeoutMinutes());
        assertEquals(ZDevUploadPlugin.Severity.LOW, plugin.getMinimumSeverity());
        assertEquals(ZDevUploadPlugin.ScanStatusAction.UNSTABLE, plugin.getScanStatusAction());
        assertEquals(ZDevUploadPlugin.ScanEvaluationMode.ANY_FINDING, plugin.getScanEvaluationMode());
    }

    @Test
    void summarizeScanReportAggregatesFindingsAndSupportsEvaluationCriteria() throws Exception {
        JsonObject report = new JsonObject();
        JsonArray findings = new JsonArray();

        JsonObject criticalFinding = new JsonObject();
        criticalFinding.addProperty("severity", "critical");
        criticalFinding.addProperty("accepted_status", false);
        findings.add(criticalFinding);

        JsonObject highFinding = new JsonObject();
        highFinding.addProperty("severity", "high");
        highFinding.addProperty("accepted_status", true);
        findings.add(highFinding);

        JsonObject mediumFinding = new JsonObject();
        mediumFinding.addProperty("severity", "medium");
        mediumFinding.addProperty("accepted_status", true);
        findings.add(mediumFinding);

        report.add("findings", findings);

        ZDevUploadPlugin plugin = new ZDevUploadPlugin(
                false,
                "https://example.test",
                "client-id",
                Secret.fromString("secret"),
                false,
                "src/**",
                "",
                false,
                ReportFormat.JSON,
                null,
                false,
                null,
                null,
                null,
                null,
                null
        );

        Method summarizeMethod = ZDevUploadPlugin.class.getDeclaredMethod("summarizeScanReport", JsonObject.class);
        summarizeMethod.setAccessible(true);
        Object summary = summarizeMethod.invoke(plugin, report);

        Method totalMethod = summary.getClass().getMethod("getTotalForSeverity", ZDevUploadPlugin.Severity.class);
        Method unacceptedMethod = summary.getClass().getMethod("getUnacceptedForSeverity", ZDevUploadPlugin.Severity.class);
        Method matchesMethod = summary.getClass().getMethod("matchesCriteria", ZDevUploadPlugin.ScanEvaluationMode.class, ZDevUploadPlugin.Severity.class);

        assertEquals(1, totalMethod.invoke(summary, ZDevUploadPlugin.Severity.CRITICAL));
        assertEquals(1, totalMethod.invoke(summary, ZDevUploadPlugin.Severity.HIGH));
        assertEquals(1, totalMethod.invoke(summary, ZDevUploadPlugin.Severity.MEDIUM));
        assertEquals(1, unacceptedMethod.invoke(summary, ZDevUploadPlugin.Severity.CRITICAL));
        assertEquals(0, unacceptedMethod.invoke(summary, ZDevUploadPlugin.Severity.HIGH));
        assertTrue((Boolean) matchesMethod.invoke(summary, ZDevUploadPlugin.ScanEvaluationMode.ANY_FINDING, ZDevUploadPlugin.Severity.LOW));
        assertTrue((Boolean) matchesMethod.invoke(summary, ZDevUploadPlugin.ScanEvaluationMode.UNACCEPTED_FINDING_ONLY, ZDevUploadPlugin.Severity.LOW));
    }
}

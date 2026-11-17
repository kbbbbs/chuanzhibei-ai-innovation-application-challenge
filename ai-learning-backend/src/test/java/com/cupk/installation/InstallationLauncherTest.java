package com.cupk.installation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InstallationLauncherTest {
    @Test void failureSummaryShowsTypeAndErrorCode() {
        assertEquals("IllegalStateException: INSTANCE_ALREADY_RUNNING",
                InstallationLauncher.failureSummary(new IllegalStateException("INSTANCE_ALREADY_RUNNING")));
    }
    @Test void failureSummaryHidesFreeTextMessages() {
        String summary=InstallationLauncher.failureSummary(
                new RuntimeException("Access denied for jdbc:mysql://db/app?user=root&password=hunter2"));
        assertEquals("RuntimeException",summary);
    }
    @Test void failureSummaryNamesTheRootCause() {
        var failure=new RuntimeException("wrapper",new java.io.IOException("disk full at /private/path"));
        assertEquals("RuntimeException (caused by IOException)",InstallationLauncher.failureSummary(failure));
    }
}

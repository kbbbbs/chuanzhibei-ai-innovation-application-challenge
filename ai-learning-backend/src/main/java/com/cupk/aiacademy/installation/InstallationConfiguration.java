package com.cupk.aiacademy.installation;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.cupk.installation.InstallationLifecycle;
import java.nio.file.Path;

@Configuration
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="installation.lifecycle.enabled",havingValue="true")
public class InstallationConfiguration {
    @Bean public InstallationStateStore installationStateStore() { return new InstallationStateStore(directory(), System.getenv()); }
    @Bean public InstallationConfigStore installationConfigStore() { return new InstallationConfigStore(directory(), System.getenv()); }
    @Bean public InstallationSessions installationSessions() { return new InstallationSessions(directory()); }
    @Bean public DatabaseInstaller databaseInstaller() { return new DatabaseInstaller(); }
    @Bean public DatabaseDraftResolver databaseDraftResolver(InstallationStateStore state) { return new DatabaseDraftResolver(System.getenv(), state); }
    @Bean public InstallationLifecycle installationLifecycle() { return new InstallationLifecycle(directory(), System.getenv()); }
    @Bean public DraftModelProbeService draftModelProbeService(InstallationConfigStore store) { return new DraftModelProbeService(store); }
    @Bean public InstallationDrainFilter installationDrainFilter(InstallationLifecycle lifecycle,org.springframework.context.ConfigurableApplicationContext context) {
        return new InstallationDrainFilter(lifecycle,context);
    }
    public static Path directory() { return Path.of(System.getenv().getOrDefault("AI_INSTALLATION_DIR", "./data/installation")); }
}

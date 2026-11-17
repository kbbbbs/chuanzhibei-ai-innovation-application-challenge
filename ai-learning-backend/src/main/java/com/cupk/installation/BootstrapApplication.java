package com.cupk.installation;

import com.cupk.aiacademy.installation.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;

/** Recovery HTTP server deliberately has no datasource or business component scan. */
@SpringBootConfiguration
@EnableAutoConfiguration(excludeName = {
        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
        "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration",
        "org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration"})
@Import({InstallationConfiguration.class, InstallationController.class, InstallationErrorHandler.class})
public class BootstrapApplication {
    public static void main(String[] args) { InstallationLauncher.main(args); }
}

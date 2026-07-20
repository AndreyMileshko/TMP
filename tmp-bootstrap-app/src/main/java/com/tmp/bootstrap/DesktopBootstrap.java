package com.tmp.bootstrap;

import com.tmp.core.api.PlatformCore;
import com.tmp.ui.shell.JavaFxShellLauncher;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public final class DesktopBootstrap {

    private DesktopBootstrap() {
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext springContext = new SpringApplicationBuilder(TmpBootstrapApplication.class)
                .web(WebApplicationType.NONE)
                .run(args);
        PlatformCore platformCore = springContext.getBean(PlatformCore.class);
        JavaFxShellLauncher.launch(springContext::close, platformCore.status().summary());
    }
}

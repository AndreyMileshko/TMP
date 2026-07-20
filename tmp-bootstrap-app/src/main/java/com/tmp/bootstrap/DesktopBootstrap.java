package com.tmp.bootstrap;

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
        JavaFxShellLauncher.launch(springContext::close);
    }
}

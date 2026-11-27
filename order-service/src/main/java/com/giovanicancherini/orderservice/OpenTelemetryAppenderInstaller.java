package com.giovanicancherini.orderservice;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

// O Logback sobe antes do Spring; o appender do logback-spring.xml só passa a enviar
// quando recebe o OpenTelemetry configurado pelo Boot.
@Component
class OpenTelemetryAppenderInstaller implements InitializingBean {

    private final OpenTelemetry openTelemetry;

    OpenTelemetryAppenderInstaller(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
    }

    @Override
    public void afterPropertiesSet() {
        OpenTelemetryAppender.install(openTelemetry);
    }
}

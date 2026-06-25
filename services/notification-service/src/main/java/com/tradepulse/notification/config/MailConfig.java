package com.tradepulse.notification.config;

import io.awspring.cloud.ses.SimpleEmailServiceMailSender;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.ses.SesClient;

import java.lang.reflect.Proxy;

/**
 * Local mail configuration providing mock AWS SES clients.
 * This allows the service to boot locally without requiring AWS credentials or connectivity.
 */
@Configuration
public class MailConfig {

    @Bean
    public SesClient sesClient() {
        return (SesClient) Proxy.newProxyInstance(
                SesClient.class.getClassLoader(),
                new Class<?>[]{SesClient.class},
                (proxy, method, args) -> {
                    if ("toString".equals(method.getName())) {
                        return "MockSesClient";
                    }
                    if ("serviceName".equals(method.getName())) {
                        return "ses";
                    }
                    return null;
                }
        );
    }

    @Bean
    public SimpleEmailServiceMailSender simpleEmailServiceMailSender(SesClient sesClient) {
        return new SimpleEmailServiceMailSender(sesClient);
    }
}

package com.easyeat.config;

import org.apache.catalina.valves.RemoteIpValve;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Trusted proxy headers configuration to prevent client IP spoofing (T046).
 * Ensures X-Forwarded-For is only trusted from designated internal proxy subnets.
 */
@Configuration
public class ProxyConfig {

    @Value("${server.tomcat.remoteip.internal-proxies:10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|192\\.168\\.\\d{1,3}\\.\\d{1,3}|169\\.254\\.\\d{1,3}\\.\\d{1,3}|127\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|172\\.(1[6-9]|2[0-9]|3[0-1])\\.\\d{1,3}\\.\\d{1,3}}")
    private String internalProxies = "10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|192\\.168\\.\\d{1,3}\\.\\d{1,3}|169\\.254\\.\\d{1,3}\\.\\d{1,3}|127\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|172\\.(1[6-9]|2[0-9]|3[0-1])\\.\\d{1,3}\\.\\d{1,3}";

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatRemoteIpCustomizer() {
        return factory -> {
            RemoteIpValve valve = new RemoteIpValve();
            valve.setInternalProxies(internalProxies);
            valve.setRemoteIpHeader("X-Forwarded-For");
            valve.setProtocolHeader("X-Forwarded-Proto");
            factory.addEngineValves(valve);
        };
    }
}

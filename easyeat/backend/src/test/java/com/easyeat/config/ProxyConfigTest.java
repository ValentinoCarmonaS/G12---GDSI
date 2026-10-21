package com.easyeat.config;

import org.apache.catalina.valves.RemoteIpValve;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;

import static org.assertj.core.api.Assertions.assertThat;

class ProxyConfigTest {

    @Test
    @DisplayName("Should customize Tomcat factory with RemoteIpValve and internal proxies regex")
    void shouldCustomizeTomcatWithRemoteIpValve() {
        ProxyConfig config = new ProxyConfig();
        WebServerFactoryCustomizer<TomcatServletWebServerFactory> customizer = config.tomcatRemoteIpCustomizer();

        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
        customizer.customize(factory);

        assertThat(factory.getEngineValves()).anyMatch(valve -> valve instanceof RemoteIpValve);
    }
}

package com.registro.usuarios.infrastructure.web;

import org.apache.catalina.Context;
import org.apache.catalina.Valve;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.valves.ErrorReportValve;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Makes Tomcat's host report errors with {@link JsonErrorReportValve}. Spring Boot itself adds a
 * stock {@link ErrorReportValve} to the host, and the innermost one wins because it writes first.
 * The customizer therefore runs after Boot's (lowest precedence, which the order of the customizer
 * itself decides, not an annotation on the bean method), removes the stock valves, and registers
 * its own class as the host's error report valve class so the host adds no other.
 */
@Configuration(proxyBeanMethods = false)
class ContainerErrorConfig {

  @Bean
  JsonErrorReportCustomizer jsonErrorReportCustomizer() {
    return new JsonErrorReportCustomizer();
  }

  static final class JsonErrorReportCustomizer
      implements WebServerFactoryCustomizer<TomcatServletWebServerFactory>, Ordered {

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
      factory.addContextCustomizers(JsonErrorReportCustomizer::replaceStockReportValve);
    }

    @Override
    public int getOrder() {
      return Ordered.LOWEST_PRECEDENCE;
    }

    private static void replaceStockReportValve(Context context) {
      if (context.getParent() instanceof StandardHost host) {
        for (Valve valve : host.getPipeline().getValves()) {
          if (valve instanceof ErrorReportValve) {
            host.getPipeline().removeValve(valve);
          }
        }
        host.setErrorReportValveClass(JsonErrorReportValve.class.getName());
        host.getPipeline().addValve(new JsonErrorReportValve());
      }
    }
  }
}

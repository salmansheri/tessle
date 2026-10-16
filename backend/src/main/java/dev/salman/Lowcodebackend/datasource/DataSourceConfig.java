package dev.salman.Lowcodebackend.datasource;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SecretsProperties.class)
class DataSourceConfig {

}

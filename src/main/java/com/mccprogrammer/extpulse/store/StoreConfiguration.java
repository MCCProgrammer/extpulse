package com.mccprogrammer.extpulse.store;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StoreProperties.class)
class StoreConfiguration {

}

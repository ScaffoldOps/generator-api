package com.scaffoldops.generatorapi.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(KafkaTopicProperties.class)
public class KafkaConfiguration {
}

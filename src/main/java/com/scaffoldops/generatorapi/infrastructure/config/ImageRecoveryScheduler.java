package com.scaffoldops.generatorapi.infrastructure.config;
import com.scaffoldops.generatorapi.application.service.ImageRecoveryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
@Configuration
@EnableScheduling
@ConditionalOnProperty(name="scaffoldops.generation.recovery.enabled",havingValue="true",matchIfMissing=true)
public class ImageRecoveryScheduler {
 private final ImageRecoveryService service;
 public ImageRecoveryScheduler(ImageRecoveryService service) { this.service=service; }
 @Scheduled(fixedDelayString="${scaffoldops.generation.recovery.fixed-delay:5m}",initialDelayString="${scaffoldops.generation.recovery.fixed-delay:5m}")
 public void recover() { service.recover(); }
}

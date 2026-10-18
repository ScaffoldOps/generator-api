package com.scaffoldops.generatorapi.infrastructure.config;
import com.scaffoldops.generatorapi.application.service.ImageRecoveryService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class ImageRecoverySchedulerTest {
 @Test void disabledSchedulerIsNotRegistered() {
  new ApplicationContextRunner().withUserConfiguration(ImageRecoveryScheduler.class)
   .withBean(ImageRecoveryService.class,()->mock(ImageRecoveryService.class))
   .withPropertyValues("scaffoldops.generation.recovery.enabled=false")
   .run(context -> assertThat(context).doesNotHaveBean(ImageRecoveryScheduler.class));
 }
 @Test void schedulerDelegates() {
  var service=mock(ImageRecoveryService.class);new ImageRecoveryScheduler(service).recover();verify(service).recover();
 }
}

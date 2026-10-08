package com.scaffoldops.generatorapi.application.service;

import com.scaffoldops.generatorapi.infrastructure.persistence.repository.SpringDataGenerationRequestJpaRepository;
import com.scaffoldops.generatorapi.domain.event.ImageBuildRetryRequestedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;

@Service
public class ImageRecoveryService {
 private final SpringDataGenerationRequestJpaRepository repository;
 private final KafkaTemplate<String, Object> kafka;
 private final int maxRetries, batchSize;
 private final Duration reservationTimeout;
 private final String topic;
 private final org.springframework.transaction.support.TransactionTemplate transactions;
 public ImageRecoveryService(SpringDataGenerationRequestJpaRepository repository, KafkaTemplate<String, Object> kafka,
  org.springframework.transaction.PlatformTransactionManager transactionManager,
  @Value("${scaffoldops.generation.recovery.max-retries:5}") int maxRetries,
  @Value("${scaffoldops.generation.recovery.batch-size:10}") int batchSize,
  @Value("${scaffoldops.generation.recovery.reservation-timeout:30m}") Duration reservationTimeout,
  @Value("${app.kafka.topics.image-build-retry-requested:image-build-retry-requested}") String topic) {
  if(maxRetries < 0 || batchSize < 1 || reservationTimeout.isNegative() || reservationTimeout.isZero())
   throw new IllegalArgumentException("Invalid recovery configuration");
  this.transactions=new org.springframework.transaction.support.TransactionTemplate(transactionManager);
  this.repository=repository; this.kafka=kafka; this.maxRetries=maxRetries; this.batchSize=batchSize;
  this.reservationTimeout=reservationTimeout; this.topic=topic;
 }
 public void recover() {
  var events=transactions.execute(status -> {
   var now=OffsetDateTime.now();
   var reserved=new java.util.ArrayList<ImageBuildRetryRequestedEvent>();
   for(var row:repository.findRecoverable(maxRetries,batchSize,now)) {
    int attempt=(row.getRetryCount()==null?0:row.getRetryCount())+1;
    row.setRetryCount(attempt); row.reserveRecovery(now.plus(reservationTimeout));
    reserved.add(new ImageBuildRetryRequestedEvent(row.getId(),row.getName(),row.getTemplate(),row.isDatabase(),
     row.isRestApi(),row.isSecurity(),row.isMessaging(),row.getDeploymentTarget().name(),row.getArtifactRef(),attempt,row.getFailureStage()));
   }
   return reserved;
  });
  for(var event:events) {
   try { kafka.send(topic,event.generationRequestId().toString(),event).get(30,TimeUnit.SECONDS); }
   catch(InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException("Recovery publication interrupted",ex); }
   catch(Exception ex) { org.slf4j.LoggerFactory.getLogger(getClass()).error("Recovery publication failed requestId={}; reservation will expire",event.generationRequestId(),ex); }
  }
 }
}

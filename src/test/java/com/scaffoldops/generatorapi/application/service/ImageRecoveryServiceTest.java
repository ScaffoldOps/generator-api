package com.scaffoldops.generatorapi.application.service;
import com.scaffoldops.generatorapi.infrastructure.persistence.repository.SpringDataGenerationRequestJpaRepository;
import com.scaffoldops.generatorapi.infrastructure.persistence.entity.GenerationRequestEntity;
import com.scaffoldops.generatorapi.domain.model.*;
import com.scaffoldops.generatorapi.domain.event.ImageBuildRetryRequestedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import java.time.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class ImageRecoveryServiceTest {
 private final SpringDataGenerationRequestJpaRepository repository=mock(SpringDataGenerationRequestJpaRepository.class);
 private final KafkaTemplate<String,Object> kafka=mock(KafkaTemplate.class);
 private final PlatformTransactionManager manager=mock(PlatformTransactionManager.class);
 private ImageRecoveryService service() {
  when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
  return new ImageRecoveryService(repository,kafka,manager,5,10,Duration.ofMinutes(30),"image-build-retry-requested");
 }
 @Test void reservesBeforePublicationAndIncludesExistingArtifact() {
  var row=new GenerationRequestEntity(UUID.randomUUID(),"billing","spring",true,true,false,false,
   DeploymentTarget.KUBERNETES,GenerationStatus.GENERATION_FAILED,DeploymentStatus.NOT_DEPLOYED,"{}",null,
   "s3://bucket/project.zip",null,null,OffsetDateTime.now(),OffsetDateTime.now());
  row.setRetryCount(4);row.setFailureStage("IMAGE_PUSH");
  when(repository.findRecoverable(eq(5),eq(10),any())).thenReturn(List.of(row));
  when(kafka.send(anyString(),anyString(),any())).thenReturn(CompletableFuture.completedFuture(null));
  service().recover();
  assertThat(row.getRetryCount()).isEqualTo(5);
  var order=inOrder(manager,kafka);
  order.verify(manager).getTransaction(any()); order.verify(manager).commit(any());
  order.verify(kafka).send(eq("image-build-retry-requested"),eq(row.getId().toString()),argThat(event ->
   event instanceof ImageBuildRetryRequestedEvent e && e.retryAttempt()==5 && e.artifactRef().equals(row.getArtifactRef())
   && e.failureStage().equals("IMAGE_PUSH")));
 }
 @Test void ignoresEmptySelection() {
  when(repository.findRecoverable(eq(5),eq(10),any())).thenReturn(List.of());
  service().recover(); verifyNoInteractions(kafka);
 }
 @Test void failedPublicationDoesNotRollBackReservedBudget() {
  var row=new GenerationRequestEntity(UUID.randomUUID(),"billing","spring",true,true,false,false,
   DeploymentTarget.KUBERNETES,GenerationStatus.GENERATION_FAILED,DeploymentStatus.NOT_DEPLOYED,"{}",null,
   "s3://bucket/project.zip",null,null,OffsetDateTime.now(),OffsetDateTime.now());row.setFailureStage("IMAGE_BUILD");
  when(repository.findRecoverable(eq(5),eq(10),any())).thenReturn(List.of(row));
  when(kafka.send(anyString(),anyString(),any())).thenReturn(CompletableFuture.failedFuture(new RuntimeException("offline")));
  service().recover(); assertThat(row.getRetryCount()).isEqualTo(1); verify(manager).commit(any());verify(manager,never()).rollback(any());
 }
}

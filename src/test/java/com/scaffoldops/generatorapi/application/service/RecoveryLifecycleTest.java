package com.scaffoldops.generatorapi.application.service;
import com.scaffoldops.generatorapi.application.port.out.*;
import com.scaffoldops.generatorapi.application.port.in.UpdateGenerationRequestStatusUseCase;
import com.scaffoldops.generatorapi.domain.model.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.OffsetDateTime;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class RecoveryLifecycleTest {
 private final GenerationRequestRepository repository=mock(GenerationRequestRepository.class);
 private final GenerationRequestService service=new GenerationRequestService(repository,mock(GenerationRequestEventPublisher.class));
 private final UUID id=UUID.randomUUID();
 private void current() {
  var now=OffsetDateTime.now();
  when(repository.findById(id)).thenReturn(Optional.of(new GenerationRequest(id,"billing","spring",true,true,false,false,
   DeploymentTarget.KUBERNETES,GenerationStatus.GENERATION_FAILED,DeploymentStatus.NOT_DEPLOYED,"{}","failed",
   "s3://bucket/project.zip",null,null,now,now,"IMAGE_PUSH",3)));
 }
 @Test void recoveryCompletesExistingRequestWithBothReferences() {
  current();
  var result=service.updateGenerationStatus(id,new UpdateGenerationRequestStatusUseCase.Command(GenerationStatus.GENERATED,
   "recovered",null,"docker.io/service:tag",null,3));
  assertThat(result).isEqualTo(UpdateGenerationRequestStatusUseCase.Result.UPDATED);
  var saved=ArgumentCaptor.forClass(GenerationRequest.class);verify(repository).save(saved.capture());
  assertThat(saved.getValue().artifactRef()).isEqualTo("s3://bucket/project.zip");
  assertThat(saved.getValue().imageRef()).isEqualTo("docker.io/service:tag");
  assertThat(saved.getValue().retryCount()).isEqualTo(3);verify(repository).lockById(id);
 }
 @Test void failureKeepsReservedCountAndOriginalArtifact() {
  current();
  service.updateGenerationStatus(id,new UpdateGenerationRequestStatusUseCase.Command(GenerationStatus.GENERATION_FAILED,
   "push still failed",null,null,"IMAGE_PUSH",3));
  verify(repository).save(argThat(row -> row.artifactRef().equals("s3://bucket/project.zip") && row.imageRef()==null
   && row.retryCount()==3 && row.failureStage().equals("IMAGE_PUSH")));
 }
 @Test void staleRecoveryCannotOverwriteNewAttempt() {
  current();
  assertThat(service.updateGenerationStatus(id,new UpdateGenerationRequestStatusUseCase.Command(GenerationStatus.GENERATED,
   "stale",null,"image:tag",null,2))).isEqualTo(UpdateGenerationRequestStatusUseCase.Result.INVALID_TRANSITION);
  verify(repository,never()).save(any());
 }
 @Test void generatedRequiresNewImageReference() {
  current();
  assertThat(service.updateGenerationStatus(id,new UpdateGenerationRequestStatusUseCase.Command(GenerationStatus.GENERATED,
   "missing image",null," ",null,3))).isEqualTo(UpdateGenerationRequestStatusUseCase.Result.INVALID_REFERENCES);
  verify(repository,never()).save(any());
 }
}

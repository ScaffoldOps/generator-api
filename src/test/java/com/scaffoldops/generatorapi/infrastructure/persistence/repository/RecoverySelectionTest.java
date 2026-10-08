package com.scaffoldops.generatorapi.infrastructure.persistence.repository;
import com.scaffoldops.generatorapi.infrastructure.persistence.entity.GenerationRequestEntity;
import com.scaffoldops.generatorapi.domain.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import java.time.OffsetDateTime;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
@DataJpaTest
class RecoverySelectionTest {
 @Autowired SpringDataGenerationRequestJpaRepository repository;
 private GenerationRequestEntity row(String artifact,String image,String stage,Integer retries,GenerationStatus status) {
  var now=OffsetDateTime.now();
  var row=new GenerationRequestEntity(UUID.randomUUID(),UUID.randomUUID().toString(),"spring",true,true,false,false,
   DeploymentTarget.KUBERNETES,status,DeploymentStatus.NOT_DEPLOYED,"{}",null,artifact,image,null,now,now);
  row.setRetryCount(retries);row.setFailureStage(stage);return repository.saveAndFlush(row);
 }
 @Test void selectsOnlyRecoverableRowsAndHonorsBatchAndBudget() {
  var eligible=row("s3://bucket/project.zip",null,"IMAGE_BUILD",4,GenerationStatus.GENERATION_FAILED);
  row(null,null,"IMAGE_BUILD",0,GenerationStatus.GENERATION_FAILED);
  row("  ",null,"IMAGE_PUSH",0,GenerationStatus.GENERATION_FAILED);
  row("s3://bucket/project.zip","image:tag","IMAGE_BUILD",0,GenerationStatus.GENERATION_FAILED);
  row("s3://bucket/project.zip",null,"ARTIFACT_UPLOAD",0,GenerationStatus.GENERATION_FAILED);
  row("s3://bucket/project.zip",null,"IMAGE_PUSH",5,GenerationStatus.GENERATION_FAILED);
  row("s3://bucket/project.zip",null,"IMAGE_BUILD",0,GenerationStatus.GENERATED);
  var reserved=row("s3://bucket/project.zip",null,"IMAGE_BUILD",0,GenerationStatus.GENERATION_FAILED);
  reserved.reserveRecovery(OffsetDateTime.now().plusMinutes(30));repository.flush();
  assertThat(repository.findRecoverable(5,10,OffsetDateTime.now())).extracting(GenerationRequestEntity::getId).containsExactly(eligible.getId());
  row("s3://bucket/second.zip"," ","IMAGE_PUSH",null,GenerationStatus.GENERATION_FAILED);
  assertThat(repository.findRecoverable(5,1,OffsetDateTime.now())).hasSize(1);
 }
}

package dev.sanda.apifi.code_generator.entity;

import static dev.sanda.datafi.DatafiStaticUtils.writeToJavaFile;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;
import dev.sanda.apifi.service.api_logic.SubscriptionsLogicService;
import dev.sanda.datafi.DatafiStaticUtils;
import dev.sanda.datafi.code_generator.annotated_element_specs.EntityDalSpec;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import lombok.Data;
import lombok.NonNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Data
public class SubscriptionsLogicServicesFactory {

  @NonNull
  private ProcessingEnvironment processingEnv;

  @NonNull
  private String basePackage;

  private static final TypeSpec.Builder typeSpecBuilder = TypeSpec
    .classBuilder("SubscriptionLogicServicesConfig")
    .addAnnotation(Configuration.class);
  private static final ClassName SUBSCRIPTION_LOGIC_SERVICE_TYPE = ClassName.get(
    SubscriptionsLogicService.class
  );

  public void addSubscriptionLogicService(EntityDalSpec entityDalSpec) {
    final ClassName entityType = ClassName.get(entityDalSpec.getElement());
    MethodSpec.Builder builder = MethodSpec
      .methodBuilder(
        DatafiStaticUtils.camelCaseNameOf(entityDalSpec.getElement()) +
        "SubscriptionLogicService"
      )
      .addModifiers(Modifier.PUBLIC)
      .addAnnotation(Bean.class)
      .returns(
        ParameterizedTypeName.get(SUBSCRIPTION_LOGIC_SERVICE_TYPE, entityType)
      )
      .addStatement(
        "return new $T($T.class)",
        SUBSCRIPTION_LOGIC_SERVICE_TYPE,
        entityType
      );
    typeSpecBuilder.addMethod(builder.build());
  }

  public void writeToFile() {
    writeToJavaFile(
      "SubscriptionLogicServicesConfig",
      basePackage,
      typeSpecBuilder,
      processingEnv,
      "SubscriptionLogicService beans"
    );
  }
}

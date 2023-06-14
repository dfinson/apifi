package dev.sanda.apifi.code_generator.entity.graphql_api_builder.factories;

import com.squareup.javapoet.AnnotationSpec;
import dev.sanda.apifi.annotations.WithMethodLevelSecurity;
import dev.sanda.apifi.annotations.WithServiceLevelSecurity;
import dev.sanda.apifi.code_generator.entity.graphql_api_builder.GraphQLApiBuilderParams;
import dev.sanda.apifi.security.SecurityAnnotationsFactory;
import lombok.RequiredArgsConstructor;
import lombok.val;

import javax.tools.Diagnostic;
import java.util.*;
import java.util.stream.Collectors;

import static dev.sanda.apifi.utils.ApifiStaticUtils.pascalCaseNameOf;

/**
 * Factory class for generating security annotations for an API service.
 */
@RequiredArgsConstructor
public class GraphQLAPISecurityAnnotationsFactory {

  private final GraphQLApiBuilderParams params;

  /**
   * Generates a list of security annotations for the API service.
   *
   * @return a list of security annotations.
   */
  public List<AnnotationSpec> generateSecurityAnnotations() {
    List<AnnotationSpec> securityAnnotations = new ArrayList<>();
    val apiSpec = params.getApiSpec();
    // Generate service level security annotations
    val serviceLevelSecurity = apiSpec.getAnnotation(
      WithServiceLevelSecurity.class
    );
    if (serviceLevelSecurity != null) {
      securityAnnotations.addAll(
        SecurityAnnotationsFactory.of(serviceLevelSecurity)
      );
    }

    // Generate method level security annotations
    params.setMethodLevelSecuritiesMap(new HashMap<>());
    val methodLevelSecuritiesMap = params.getMethodLevelSecuritiesMap();
    val methodLevelSecurities = apiSpec.getAnnotationsByType(
      WithMethodLevelSecurity.class
    );
    if (!methodLevelSecurities.isEmpty()) {
      methodLevelSecurities.forEach(security ->
        handleTargetMethodsMapping(security, methodLevelSecuritiesMap)
      );
      securityAnnotations.addAll(
        methodLevelSecuritiesMap
          .values()
          .stream()
          .flatMap(List::stream)
          .collect(Collectors.toList())
      );
    }

    return securityAnnotations;
  }

  /**
   * Handles the mapping of target methods for a given security annotation.
   *
   * @param security the security annotation.
   * @param methodLevelSecuritiesMap the map of method level securities.
   */
  private void handleTargetMethodsMapping(
    WithMethodLevelSecurity security,
    Map<String, List<AnnotationSpec>> methodLevelSecuritiesMap
  ) {
    List<String> targetMethods = getTargetMethods(security);
    List<AnnotationSpec> securityAnnotations = SecurityAnnotationsFactory.of(
      security
    );

    for (String targetMethod : targetMethods) {
      List<AnnotationSpec> securities = getSecuritiesForMethod(
        targetMethod,
        methodLevelSecuritiesMap
      );
      addSecurityAnnotations(securities, securityAnnotations);
    }
  }

  /**
   * Retrieves the target methods for a given security annotation.
   *
   * @param security the security annotation.
   * @return a list of target methods.
   */
  private List<String> getTargetMethods(WithMethodLevelSecurity security) {
    List<String> targetMethods = Arrays
      .stream(security.crudEndpointTargets())
      .map(Object::toString)
      .collect(Collectors.toList());
    targetMethods.addAll(
      Arrays
        .stream(security.subscriptionEndpointTargets())
        .map(Object::toString)
        .collect(Collectors.toList())
    );
    return targetMethods;
  }

  /**
   * Retrieves the securities for a given target method.
   *
   * @param targetMethod the target method.
   * @param methodLevelSecuritiesMap the map of method level securities.
   * @return a list of securities for the target method.
   */
  private List<AnnotationSpec> getSecuritiesForMethod(
    String targetMethod,
    Map<String, List<AnnotationSpec>> methodLevelSecuritiesMap
  ) {
    if (!methodLevelSecuritiesMap.containsKey(targetMethod)) {
      methodLevelSecuritiesMap.put(targetMethod, new ArrayList<>());
    }
    return methodLevelSecuritiesMap.get(targetMethod);
  }

  /**
   * Adds security annotations to the list of securities for a method.
   *
   * @param securities the list of securities for a method.
   * @param securityAnnotations the security annotations to be added.
   */
  private void addSecurityAnnotations(
    List<AnnotationSpec> securities,
    List<AnnotationSpec> securityAnnotations
  ) {
    for (AnnotationSpec securityAnnotation : securityAnnotations) {
      if (!securities.contains(securityAnnotation)) {
        securities.add(securityAnnotation);
      } else {
        params
          .getProcessingEnv()
          .getMessager()
          .printMessage(
            Diagnostic.Kind.ERROR,
            "Illegal attempt to repeat non repeatable security annotation of type: " +
            securityAnnotation.type.toString() +
            " on or in entity of type: " +
            pascalCaseNameOf(params.getApiSpec().getElement())
          );
        return;
      }
    }
  }
}

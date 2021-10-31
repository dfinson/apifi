package dev.sanda.apifi.security;

import com.squareup.javapoet.AnnotationSpec;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.security.RolesAllowed;
import javax.tools.Diagnostic;
import lombok.Setter;
import lombok.val;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.prepost.PreFilter;

public class SecurityAnnotationsFactory<T extends Annotation> {

  @Setter
  private static ProcessingEnvironment processingEnv;

  public static <E extends Annotation> boolean areSecurityAnnotationsPresent(
    E instance,
    String... methodNameSuffixes
  ) {
    if (instance == null) return false;
    return Arrays
      .stream(methodNameSuffixes)
      .anyMatch(suffix -> !of(instance, suffix).isEmpty());
  }

  private static <E extends Annotation> List<AnnotationSpec> of(
    E instance,
    String methodNameSuffix
  ) {
    List<AnnotationSpec> result = new ArrayList<AnnotationSpec>();
    if (instance == null) return result;
    SecurityAnnotationsFactory<E> factoryInstance = new SecurityAnnotationsFactory<E>(
      instance
    );
    factoryInstance.methodNameSuffix = methodNameSuffix;

    String secured = factoryInstance.getValue("secured");
    assert secured != null;
    if (!secured.equals("")) result.add(factoryInstance.secured());

    String[] rolesAllowed = factoryInstance.getRolesAllowedValue();
    assert rolesAllowed != null;
    if (!(rolesAllowed.length == 1 && rolesAllowed[0].equals(""))) result.add(
      factoryInstance.rolesAllowed()
    );

    String preAuthorize = factoryInstance.getValue("preAuthorize");
    assert preAuthorize != null;
    if (!preAuthorize.equals("")) result.add(factoryInstance.preAuthorize());

    String postAuthorize = factoryInstance.getValue("postAuthorize");
    assert postAuthorize != null;
    if (!postAuthorize.equals("")) result.add(factoryInstance.postAuthorize());

    String preFilter = factoryInstance.getValue("preFilter");
    assert preFilter != null;
    if (!preFilter.equals("")) result.add(factoryInstance.preFilter());

    String postFilter = factoryInstance.getValue("postFilter");
    assert postFilter != null;
    if (!postFilter.equals("")) result.add(factoryInstance.postFilter());

    return result;
  }

  public static <E extends Annotation> List<AnnotationSpec> of(
    E instance,
    String... methodNameSuffixes
  ) {
    if (methodNameSuffixes == null || methodNameSuffixes.length == 0) return of(
      instance,
      ""
    ); else return Arrays
      .stream(methodNameSuffixes)
      .map(suffix -> of(instance, suffix))
      .flatMap(List::stream)
      .collect(Collectors.toList());
  }

  public SecurityAnnotationsFactory(T instance) {
    commonSecurityAnnotationsInstance = instance;
    clazz = (Class<T>) instance.getClass();
  }

  private final T commonSecurityAnnotationsInstance;
  private final Class<T> clazz;
  private String methodNameSuffix = "";

  private AnnotationSpec secured() {
    return getAnnotation(Secured.class, "secured");
  }

  private AnnotationSpec rolesAllowed() {
    return AnnotationSpec
      .builder(RolesAllowed.class)
      .addMember("value", "$S", getRolesAllowedValue())
      .build();
  }

  private AnnotationSpec preAuthorize() {
    return getAnnotation(PreAuthorize.class, "preAuthorize");
  }

  private AnnotationSpec postAuthorize() {
    return getAnnotation(PostAuthorize.class, "postAuthorize");
  }

  private AnnotationSpec preFilter() {
    AnnotationSpec.Builder builder = AnnotationSpec
      .builder(PreFilter.class)
      .addMember("value", "$S", getValue("preFilter"));
    val preFilterTarget = getValue("preFilterTarget");
    if (!preFilterTarget.equals("")) builder.addMember(
      "preFilterTarget",
      "$S",
      getValue("preFilterTarget")
    );
    return builder.build();
  }

  private AnnotationSpec postFilter() {
    return getAnnotation(PostFilter.class, "postFilter");
  }

  public AnnotationSpec getAnnotation(
    Class<? extends Annotation> type,
    String methodName
  ) {
    return AnnotationSpec
      .builder(type)
      .addMember("value", "$S", getValue(methodName))
      .build();
  }

  private String getValue(String methodName) {
    try {
      Method method = clazz.getMethod(methodName + methodNameSuffix);
      return (String) method.invoke(commonSecurityAnnotationsInstance);
    } catch (Exception e) {
      processingEnv
        .getMessager()
        .printMessage(
          Diagnostic.Kind.ERROR,
          "Error parsing security annotations: " + e.toString()
        );
    }
    return null;
  }

  private String[] getRolesAllowedValue() {
    try {
      Method method = clazz.getMethod("rolesAllowed" + methodNameSuffix);
      return (String[]) method.invoke(commonSecurityAnnotationsInstance);
    } catch (Exception e) {
      processingEnv
        .getMessager()
        .printMessage(
          Diagnostic.Kind.ERROR,
          "Error parsing security annotations: " + e.toString()
        );
    }
    return null;
  }
}

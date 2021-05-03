package dev.sanda.apifi.code_generator.entity;

import static dev.sanda.datafi.DatafiStaticUtils.camelCaseNameOf;
import static dev.sanda.datafi.DatafiStaticUtils.logCompilationError;

import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.TypeName;
import dev.sanda.apifi.annotations.GraphQLComponent;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

@AllArgsConstructor
public class CustomEndpointsAggregator {

  private final RoundEnvironment roundEnvironment;
  private final ProcessingEnvironment processingEnvironment;

  public Set<FieldSpec> customEndpointServices() {
    return roundEnvironment
      .getElementsAnnotatedWith(GraphQLComponent.class)
      .stream()
      .map(this::toFieldSpec)
      .collect(Collectors.toSet());
  }

  private FieldSpec toFieldSpec(Element element) {
    if (element.getKind().isClass()) {
      return FieldSpec
        .builder(
          TypeName.get(element.asType()),
          camelCaseNameOf(element),
          Modifier.PRIVATE
        )
        .addAnnotation(Autowired.class)
        .build();
    } else if (element instanceof ExecutableElement) {
      return FieldSpec
        .builder(
          TypeName.get(((ExecutableElement) element).getReturnType()),
          beanMethodName((ExecutableElement) element),
          Modifier.PRIVATE
        )
        .addAnnotation(Autowired.class)
        .build();
    } else {
      logCompilationError(
        processingEnvironment,
        element,
        GraphQLComponent.class.getSimpleName() +
        " inapplicable to element of type " +
        element.asType().toString()
      );
      return null;
    }
  }

  private String beanMethodName(ExecutableElement element) {
    return element.getAnnotation(Qualifier.class) != null
      ? element.getAnnotation(Qualifier.class).value()
      : camelCaseNameOf(element);
  }
}

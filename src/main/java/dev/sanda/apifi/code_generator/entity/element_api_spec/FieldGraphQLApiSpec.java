package dev.sanda.apifi.code_generator.entity.element_api_spec;

import dev.sanda.apifi.annotations.*;
import dev.sanda.datafi.code_generator.annotated_element_specs.AnnotatedElementSpec;
import jakarta.persistence.ElementCollection;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import java.lang.annotation.Annotation;

public class FieldGraphQLApiSpec extends AnnotatedElementSpec<VariableElement> {

  public FieldGraphQLApiSpec(VariableElement field, ExecutableElement getter) {
    super(field);
    if (getter != null) addAnnotations(getter);
  }

  @Override
  protected <A extends Annotation> Class<A>[] targetAnnotations() {
    return new Class[] {
      ApiFindAllBy.class,
      ApiFindBy.class,
      ApiFindByUnique.class,
      ElementCollectionApi.class,
      EntityCollectionApi.class,
      ElementCollection.class,
      MapElementCollectionApi.class,
    };
  }
}

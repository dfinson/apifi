package dev.sanda.apifi.generator.entity.element_api_spec;

import dev.sanda.apifi.annotations.*;
import dev.sanda.datafi.code_generator.annotated_element_specs.AnnotatedElementSpec;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.persistence.ElementCollection;
import java.lang.annotation.Annotation;

public class FieldGraphQLApiSpec extends AnnotatedElementSpec<VariableElement> {

    public FieldGraphQLApiSpec(VariableElement field, ExecutableElement getter) {
        super(field);
        if(getter != null)
            addAnnotations(getter);
        this.setHasExtensionElement(getter != null);
    }

    @Override
    protected void setHasExtensionElement(boolean hasExtensionElement) {
        this.hasExtensionElement = hasExtensionElement;
    }

    @Override
    protected <A extends Annotation> Class<A>[] targetAnnotations() {
        return new Class[]{
                ApiFindAllBy.class,
                ApiFindBy.class,
                ApiFindByUnique.class,
                ElementCollectionApi.class,
                EntityCollectionApi.class,
                ElementCollection.class,
                MapElementCollectionApi.class
        };
    }
}

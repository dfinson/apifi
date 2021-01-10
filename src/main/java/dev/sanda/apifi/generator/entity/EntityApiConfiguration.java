package dev.sanda.apifi.generator.entity;

import com.squareup.javapoet.AnnotationSpec;
import dev.sanda.apifi.generator.client.ApifiClientFactory;
import lombok.Data;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import java.util.List;
import java.util.Map;

@Data
public class EntityApiConfiguration {
    private TypeElement entity;
    private List<VariableElement> fields;
    private Map<CRUDEndpoints, Boolean> crudResolvers;
    private ProcessingEnvironment processingEnv;
    private Map<String, TypeElement> entitiesMap;
    private Map<CRUDEndpoints, List<AnnotationSpec>> methodLevelSecuritiesMap;
    private ApifiClientFactory clientFactory;
}

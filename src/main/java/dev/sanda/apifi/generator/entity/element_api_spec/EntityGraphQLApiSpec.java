package dev.sanda.apifi.generator.entity.element_api_spec;

import dev.sanda.apifi.annotations.WithApiFreeTextSearchByFields;
import dev.sanda.apifi.annotations.WithCRUDEndpoints;
import dev.sanda.apifi.annotations.WithMethodLevelSecurity;
import dev.sanda.apifi.annotations.WithServiceLevelSecurity;
import dev.sanda.apifi.generator.entity.CRUDEndpoints;
import dev.sanda.datafi.code_generator.annotated_element_specs.AnnotatedElementSpec;
import lombok.Getter;
import lombok.val;

import javax.lang.model.element.TypeElement;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static dev.sanda.datafi.DatafiStaticUtils.*;

public class EntityGraphQLApiSpec extends AnnotatedElementSpec<TypeElement> {

    @Getter
    private List<FieldGraphQLApiSpec> fieldGraphQLApiSpecs;

    @Getter
    private List<CRUDEndpoints> mergedCrudEndpoints;

    public EntityGraphQLApiSpec(TypeElement entity, TypeElement apiSpecExtension) {
        super(entity);
        if(apiSpecExtension != null)
            addAnnotations(apiSpecExtension);
        setFieldGraphQlApiSpecs(entity, apiSpecExtension);
        setMergedCrudEndpoints(entity, apiSpecExtension);
    }

    private void setMergedCrudEndpoints(TypeElement entity, TypeElement apiSpecExtension) {
        WithCRUDEndpoints entityCrudEndpoints = entity.getAnnotation(WithCRUDEndpoints.class);
        WithCRUDEndpoints apiSpecExtensionCrudEndpoints = null;
        if(apiSpecExtension != null)
            apiSpecExtensionCrudEndpoints = apiSpecExtension.getAnnotation(WithCRUDEndpoints.class);
        Set<CRUDEndpoints> crudEndpoints = new HashSet<>();
        if(entityCrudEndpoints != null)
            crudEndpoints.addAll(Arrays.asList(entityCrudEndpoints.value()));
        if(apiSpecExtensionCrudEndpoints != null)
            crudEndpoints.addAll(Arrays.asList(apiSpecExtensionCrudEndpoints.value()));
        this.mergedCrudEndpoints = new ArrayList<>(crudEndpoints);
    }

    private void setFieldGraphQlApiSpecs(TypeElement entity, TypeElement apiSpecExtension) {
        val fields = getFieldsOf(entity);
        val gettersByFieldName =
                getGettersOf(apiSpecExtension)
                .stream()
                .collect(Collectors.toMap(
                        getter -> toCamelCase(getter.getSimpleName().toString().replaceFirst("get", "")),
                        Function.identity()
                ));
        fieldGraphQLApiSpecs = fields.stream()
                .map(field -> new FieldGraphQLApiSpec(field, gettersByFieldName.get(toCamelCase(field.getSimpleName().toString()))))
                .collect(Collectors.toList());
    }

    @Override
    protected <A extends Annotation> Class<A>[] targetAnnotations() {
        return new Class[]{
                WithApiFreeTextSearchByFields.class,
                WithCRUDEndpoints.class,
                WithMethodLevelSecurity.class,
                WithServiceLevelSecurity.class
        };
    }
}

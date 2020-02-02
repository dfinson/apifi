package dev.sanda.apifi.generator;

import dev.sanda.apifi.annotations.WithCRUDResolvers;
import dev.sanda.apifi.generator.api_builder.CustomResolver;
import dev.sanda.apifi.service.ApiHooksAndCustomResolvers;
import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import java.util.*;
import java.util.stream.Collectors;

import static dev.sanda.apifi.ApifiStaticUtils.isTypeElementOfType;

@Getter
@Data
public class EntitiesInfoCache {
    @NonNull
    private ProcessingEnvironment processingEnv;
    private static final String customResolverCanonicalName = CustomResolver.class.getCanonicalName();
    private Map<String, TypeElement> entitiesApiHooksMap;
    private Map<String, TypeElement> typeElementMap;


    public void setEntitiesApiHooksMap(List<TypeElement> allTypeElements){
        entitiesApiHooksMap =
                allTypeElements.stream()
                        .filter(this::isServiceBean)
                        .filter(this::isApiHooksAndCustomResolvers)
                        .collect(Collectors
                                .toMap(t -> t.getTypeParameters().get(0).getGenericElement().getSimpleName().toString(),
                                        t -> t)
                        );
    }

    private boolean isApiHooksAndCustomResolvers(TypeElement typeElement) {
        return isTypeElementOfType(typeElement, ApiHooksAndCustomResolvers.class.getCanonicalName(), processingEnv);
    }

    public void setTypeElementMap(Set<? extends TypeElement> typeElements){
        typeElementMap = typeElements.stream().collect(Collectors.toMap(t -> t.getSimpleName().toString(), t -> t));
    }
    public boolean exposeDirectly(VariableElement embeddedCollection){
        String typeNameString = getCollectionType(embeddedCollection);
        TypeElement typeElement = typeElementMap.get(typeNameString);
        return typeElement.getAnnotation(WithCRUDResolvers.class) != null;
    }
    public String getCollectionType(VariableElement element){
        return element
                .asType()
                .toString()
                .replaceAll("^.+<", "")
                .replaceAll(">", "");
    }

    private TypeElement getApiHooksAndResolversTypeElement(TypeElement entity, List<TypeElement> allTypeElements) {
        return
                allTypeElements
                        .stream()
                        .filter(this::isServiceBean)
                        .filter(serviceBean -> isTypeElementOfType(serviceBean, customResolverCanonicalName, processingEnv))
                        .filter(serviceBean -> isGenericOf(serviceBean, entity))
                        .findFirst()
                        .orElse(null);
    }

    private boolean isServiceBean(TypeElement element) {
        return
                element.getAnnotation(Component.class) != null ||
                element.getAnnotation(Service.class) != null ||
                element.getAnnotation(Configuration.class) != null;
    }

    private boolean isGenericOf(TypeElement serviceBean, TypeElement entity) {
        for (TypeParameterElement typeParam : serviceBean.getTypeParameters())
            if (typeParam.getGenericElement().asType().equals(entity.asType()))
                return true;
        return false;
    }
}

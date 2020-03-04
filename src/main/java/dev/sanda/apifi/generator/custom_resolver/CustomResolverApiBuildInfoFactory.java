package dev.sanda.apifi.generator.custom_resolver;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;
import dev.sanda.apifi.generator.entity.EntitiesInfoCache;
import dev.sanda.apifi.service.ApiHooksAndCustomResolvers;
import dev.sanda.apifi.service.CustomQueryResolver;
import dev.sanda.apifi.service.CustomResolver;
import dev.sanda.apifi.service.CustomResolverType;
import dev.sanda.datafi.code_generator.query.ReturnPlurality;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.val;
import lombok.var;
import org.springframework.beans.factory.annotation.Qualifier;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static dev.sanda.apifi.ApifiStaticUtils.*;
import static dev.sanda.apifi.service.CustomResolverType.MUTATION;
import static dev.sanda.apifi.service.CustomResolverType.QUERY;

@SuppressWarnings("unchecked")
@RequiredArgsConstructor
public class CustomResolverApiBuildInfoFactory {
    @NonNull
    private ProcessingEnvironment processingEnv;
    @NonNull
    private EntitiesInfoCache entitiesInfoCache;
    private static final String customResolverCanonicalName = CustomResolver.class.getCanonicalName();

    public List<CustomResolverApiBuildInfo> getInstances(TypeElement entity, List<TypeElement> allTypeElements){

        final TypeElement apiHooksAndCustomResolversType =
                allTypeElements
                .stream()
                .filter(elem -> isApiHooksAndCustomResolversOfEntityType(elem, entity))
                .findFirst()
                .orElse(null);
        if(apiHooksAndCustomResolversType == null) return new ArrayList<>();
        return
                apiHooksAndCustomResolversType
                .getEnclosedElements()
                .stream()
                .filter(this::isMethodAndCustomResolver)
                .map(this::generateCustomResolverApiBuildInfo)
                .collect(Collectors.toList());
    }

    private boolean isApiHooksAndCustomResolversOfEntityType(TypeElement typeElement, TypeElement entity) {
        boolean isApiHooksAndCustomResolvers = isAssignableFrom(
                processingEnv,
                typeElement,
                ApiHooksAndCustomResolvers.class.getCanonicalName());
        if(!isApiHooksAndCustomResolvers) return false;
        return
                typeElement
                .getTypeParameters()
                .stream()
                .map(TypeParameterElement::getGenericElement)
                .filter(ge -> ge.equals(entity))
                .count() == 1;
    }


    private CustomResolverApiBuildInfo generateCustomResolverApiBuildInfo(Element customResolverElement) {
        var asExecutable = (ExecutableElement)customResolverElement;
        var result = new CustomResolverApiBuildInfo();
        result.setEntity((TypeElement) customResolverElement.getEnclosingElement());
        result.setCustomResolverArgumentParameters(parseArgumentParameters(asExecutable));
        result.setQualifier(parseBeanQualifier(asExecutable));
        result.setCustomResolverType(determineCustomResolverType(asExecutable));
        result.setReturnPlurality(determineReturnPlurality(asExecutable));
        result.setReturnType(determineReturnType(asExecutable));
        return result;
    }

    private CustomResolverType determineCustomResolverType(ExecutableElement asExecutable) {
        return isTypeElementOfType(
                processingEnv.getTypeUtils().asElement(asExecutable.getReturnType()),
                CustomQueryResolver.class.getCanonicalName(), processingEnv)
               ? QUERY : MUTATION;
    }

    private TypeName determineReturnType(ExecutableElement asExecutable) {
        return ClassName.get(asExecutable.getReturnType());
    }

    private ReturnPlurality determineReturnPlurality(ExecutableElement asExecutable) {
        return isIterable(asExecutable.getReturnType(), processingEnv) ? ReturnPlurality.BATCH : ReturnPlurality.SINGLE;
    }

    private String parseBeanQualifier(ExecutableElement asExecutable) {
        return
                asExecutable.getAnnotation(Qualifier.class) != null ?
                asExecutable.getAnnotation(Qualifier.class).value() :
                asExecutable.getSimpleName().toString();
    }

    private List<CustomResolverArgumentParameter> parseArgumentParameters(ExecutableElement asExecutable) {
        return null;
    }

    private boolean isMethodAndCustomResolver(Element elem) {
        return
                elem.getKind() == ElementKind.METHOD &&
                isTypeElementOfType(
                        (TypeElement) ((ExecutableType)elem).getReturnType(),
                        customResolverCanonicalName,
                        processingEnv);
    }
}

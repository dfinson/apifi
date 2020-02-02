package dev.sanda.apifi.generator.api_builder;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;
import dev.sanda.apifi.generator.EntitiesInfoCache;
import dev.sanda.datafi.code_generator.query.ReturnPlurality;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.var;
import org.springframework.beans.factory.annotation.Qualifier;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import java.util.List;
import java.util.stream.Collectors;

import static dev.sanda.apifi.ApifiStaticUtils.isTypeElementOfType;
import static dev.sanda.apifi.generator.api_builder.CustomResolverType.MUTATION;
import static dev.sanda.apifi.generator.api_builder.CustomResolverType.QUERY;

@SuppressWarnings("unchecked")
@RequiredArgsConstructor
public class CustomResolverApiBuildInfoFactory {
    @NonNull
    private ProcessingEnvironment processingEnv;
    @NonNull
    private EntitiesInfoCache entitiesInfoCache;
    private static final String customResolverCanonicalName = CustomResolver.class.getCanonicalName();

    public List<CustomResolverApiBuildInfo> getInstances(TypeElement entity, List<TypeElement> allTypeElements){

        return
                entitiesInfoCache.getEntitiesApiHooksMap().get(entity.getSimpleName().toString())
                .getEnclosedElements()
                .stream()
                .filter(this::isMethodAndCustomResolver)
                .map(this::generateCustomResolverApiBuildInfo)
                .collect(Collectors.toList());
    }

    private CustomResolverApiBuildInfo generateCustomResolverApiBuildInfo(Element customResolverElement) {
        var asExecutable = (ExecutableElement)customResolverElement;
        var result = new CustomResolverApiBuildInfo();
        result.setEntity((TypeElement) customResolverElement.getEnclosingElement());
        result.setArgumentParameters(parseArgumentParameters(asExecutable));
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
        TypeMirror iterableType =
                processingEnv.getTypeUtils()
                        .erasure(
                                processingEnv
                                        .getElementUtils()
                                        .getTypeElement("java.lang.Iterable")
                                        .asType()
                        );
        return
                processingEnv.getTypeUtils().isAssignable(asExecutable.getReturnType(), iterableType) ?
                ReturnPlurality.BATCH : ReturnPlurality.SINGLE;
    }

    private String parseBeanQualifier(ExecutableElement asExecutable) {
        return
                asExecutable.getAnnotation(Qualifier.class) != null ?
                asExecutable.getAnnotation(Qualifier.class).value() :
                asExecutable.getSimpleName().toString();
    }

    private List<ArgumentParameter> parseArgumentParameters(ExecutableElement asExecutable) {
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

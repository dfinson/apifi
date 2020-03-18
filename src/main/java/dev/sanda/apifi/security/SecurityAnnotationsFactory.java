package dev.sanda.apifi.security;

import com.squareup.javapoet.AnnotationSpec;
import lombok.val;
import lombok.var;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.prepost.PreFilter;
import javax.annotation.security.RolesAllowed;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class SecurityAnnotationsFactory<T extends Annotation> {

    public static <E extends Annotation> boolean areSecurityAnnotationsPresent(E instance, String... methodNameSuffixes){
        return Arrays.stream(methodNameSuffixes).anyMatch(suffix -> !of(instance, suffix).isEmpty());
    }

    private static <E extends Annotation> List<AnnotationSpec> of(E instance, String methodNameSuffix){

        var result = new ArrayList<AnnotationSpec>();
        if(instance == null) return result;
        var factoryInstance = new SecurityAnnotationsFactory<E>(instance);

        var secured = factoryInstance.getValue("secured" + methodNameSuffix);
        if(!secured.equals("")) result.add(factoryInstance.secured());

        var rolesAllowed = factoryInstance.getValue("rolesAllowed" + methodNameSuffix);
        if(!rolesAllowed.equals("")) result.add(factoryInstance.rolesAllowed());

        var preAuthorize = factoryInstance.getValue("preAuthorize" + methodNameSuffix);
        if(!preAuthorize.equals("")) result.add(factoryInstance.preAuthorize());

        var postAuthorize = factoryInstance.getValue("postAuthorize" + methodNameSuffix);
        if(!postAuthorize.equals("")) result.add(factoryInstance.postAuthorize());

        var preFilter = factoryInstance.getValue("preFilter" + methodNameSuffix);
        if(!preFilter.equals("")) result.add(factoryInstance.preFilter());

        var postFilter = factoryInstance.getValue("postFilter" + methodNameSuffix);
        if(!postFilter.equals("")) result.add(factoryInstance.postFilter());

        return result;
    }

    public static <E extends Annotation> List<AnnotationSpec> of(E instance, String... methodNameSuffixes){
        if(methodNameSuffixes == null || methodNameSuffixes.length == 0)
            return of(instance, "");
        else return Arrays
                .stream(methodNameSuffixes)
                .map(suffix -> of(instance, suffix))
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    public SecurityAnnotationsFactory(T instance){
        commonSecurityAnnotationsInstance = instance;
        clazz = (Class<T>) instance.getClass();
    }
    private T commonSecurityAnnotationsInstance;
    private Class<T> clazz;
    private String methodNameSuffix = "";

    private AnnotationSpec secured() {
        return getAnnotation(Secured.class, "secured" + methodNameSuffix);
    }
    private AnnotationSpec rolesAllowed() {
        return getAnnotation(RolesAllowed.class, "rolesAllowed" + methodNameSuffix);
    }
    private AnnotationSpec preAuthorize() {
        return getAnnotation(PreAuthorize.class, "preAuthorize" + methodNameSuffix);
    }
    private AnnotationSpec postAuthorize() {
        return getAnnotation(PostAuthorize.class, "postAuthorize" + methodNameSuffix);
    }
    private AnnotationSpec preFilter() {
        var builder = AnnotationSpec.builder(PreFilter.class)
                .addMember("value", "$S", getValue("preFilter" + methodNameSuffix));
        val preFilterTarget = getValue("preFilterTarget");
        if(!preFilterTarget.equals(""))
            builder.addMember("preFilterTarget", "$S", getValue("preFilterTarget" + methodNameSuffix));
        return builder.build();
    }
    private AnnotationSpec postFilter() {
        return getAnnotation(PostFilter.class, "postFilter" + methodNameSuffix);
    }

    public AnnotationSpec getAnnotation(Class<? extends Annotation> type, String methodName){
        return AnnotationSpec
                .builder(type)
                .addMember("value", "$S", getValue(methodName))
                .build();
    }

    private String getValue(String methodName){
        try {
            Method method = clazz.getMethod(methodName);
            return (String) method.invoke(commonSecurityAnnotationsInstance);
        }catch (Exception ignored){
            return "";
        }
    }
}

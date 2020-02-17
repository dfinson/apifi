package dev.sanda.apifi.security;


import com.squareup.javapoet.AnnotationSpec;
import dev.sanda.apifi.annotations.WithServiceLevelSecurity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.prepost.PreFilter;

import javax.annotation.security.RolesAllowed;
import javax.lang.model.element.Element;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.google.common.collect.Maps.immutableEntry;

/**
 *
 */
public class SecurityAnnotationsHandler {
    
    public Map.Entry<List<AnnotationSpec>, List<Class<? extends Annotation>>> handleCRUD(Element element, List<Class<? extends Annotation>> alreadyAssigned){
        List<AnnotationSpec> result = new ArrayList<>();
        WithServiceLevelSecurity withServiceLevelSecurity = element.getAnnotation(WithServiceLevelSecurity.class);
        if(withServiceLevelSecurity == null) return immutableEntry(result, alreadyAssigned);
        
        if(!withServiceLevelSecurity.secured().equals("") && !alreadyAssigned.contains(SECURED)){
            result.add(securityAnnotation(SECURED, withServiceLevelSecurity.secured()));
            alreadyAssigned.add(SECURED);
        }
        if(!withServiceLevelSecurity.rolesAllowed().equals("")&& !alreadyAssigned.contains(ROLES_ALLOWED)){
            result.add(securityAnnotation(ROLES_ALLOWED, withServiceLevelSecurity.rolesAllowed()));
            alreadyAssigned.add(ROLES_ALLOWED);
        }
        if(!withServiceLevelSecurity.preAuthorize().equals("")&& !alreadyAssigned.contains(PRE_AUTHORIZE)){
            result.add(securityAnnotation(PRE_AUTHORIZE, withServiceLevelSecurity.preAuthorize()));
            alreadyAssigned.add(PRE_AUTHORIZE);
        }
        if(!withServiceLevelSecurity.postAuthorize().equals("")&& !alreadyAssigned.contains(POST_AUTORIZE)){
            result.add(securityAnnotation(POST_AUTORIZE, withServiceLevelSecurity.postAuthorize()));
            alreadyAssigned.add(POST_AUTORIZE);
        }
        if(!withServiceLevelSecurity.preFilter().equals("")&& !alreadyAssigned.contains(PRE_FILTER)){
            AnnotationSpec.Builder builder = AnnotationSpec
                    .builder(PreFilter.class)
                    .addMember("value", "$S", withServiceLevelSecurity.preFilter());
            if(!withServiceLevelSecurity.preFilterTarget().equals("")){
                builder.addMember("filterTarget", "$S", withServiceLevelSecurity.preFilterTarget());
            }
            result.add(builder.build());
            alreadyAssigned.add(PRE_FILTER);
        }
        if(!withServiceLevelSecurity.postFilter().equals("")&& !alreadyAssigned.contains(POST_FILTER)){
            result.add(securityAnnotation(POST_FILTER, withServiceLevelSecurity.postFilter()));
            alreadyAssigned.add(POST_FILTER);
        }
        return immutableEntry(result, alreadyAssigned);
    }
    
    public Map.Entry<List<AnnotationSpec>, List<Class<? extends Annotation>>> handleCreate(Element element, List<Class<? extends Annotation>> alreadyAssigned){
        List<AnnotationSpec> result = new ArrayList<>();
        WithServiceLevelSecurity withServiceLevelSecurity = element.getAnnotation(WithServiceLevelSecurity.class);
        if(withServiceLevelSecurity == null) return immutableEntry(result, alreadyAssigned);

        if(!withServiceLevelSecurity.securedCreate().equals("") && !alreadyAssigned.contains(SECURED)){
            result.add(securityAnnotation(SECURED, withServiceLevelSecurity.securedCreate()));
            alreadyAssigned.add(SECURED);
        }
        if(!withServiceLevelSecurity.rolesAllowedCreate().equals("")&& !alreadyAssigned.contains(ROLES_ALLOWED)){
            result.add(securityAnnotation(ROLES_ALLOWED, withServiceLevelSecurity.rolesAllowedCreate()));
            alreadyAssigned.add(ROLES_ALLOWED);
        }
        if(!withServiceLevelSecurity.preAuthorizeCreate().equals("")&& !alreadyAssigned.contains(PRE_AUTHORIZE)){
            result.add(securityAnnotation(PRE_AUTHORIZE, withServiceLevelSecurity.preAuthorizeCreate()));
            alreadyAssigned.add(PRE_AUTHORIZE);
        }
        if(!withServiceLevelSecurity.postAuthorizeCreate().equals("")&& !alreadyAssigned.contains(POST_AUTORIZE)){
            result.add(securityAnnotation(POST_AUTORIZE, withServiceLevelSecurity.postAuthorizeCreate()));
            alreadyAssigned.add(POST_AUTORIZE);
        }
        if(!withServiceLevelSecurity.preFilterCreate().equals("")&& !alreadyAssigned.contains(PRE_FILTER)){
            AnnotationSpec.Builder builder = AnnotationSpec
                    .builder(PreFilter.class)
                    .addMember("value", "$S", withServiceLevelSecurity.preFilterCreate());
            if(!withServiceLevelSecurity.preFilterTargetCreate().equals("")){
                builder.addMember("filterTarget", "$S", withServiceLevelSecurity.preFilterTargetCreate());
            }
            result.add(builder.build());
            alreadyAssigned.add(PRE_FILTER);
        }
        if(!withServiceLevelSecurity.postFilterCreate().equals("")&& !alreadyAssigned.contains(POST_FILTER)){
            result.add(securityAnnotation(POST_FILTER, withServiceLevelSecurity.postFilterCreate()));
            alreadyAssigned.add(POST_FILTER);
        }
        return immutableEntry(result, alreadyAssigned);
    }
    
    public Map.Entry<List<AnnotationSpec>, List<Class<? extends Annotation>>> handleRead(Element element, List<Class<? extends Annotation>> alreadyAssigned) {
        List<AnnotationSpec> result = new ArrayList<>();
        WithServiceLevelSecurity withServiceLevelSecurity = element.getAnnotation(WithServiceLevelSecurity.class);
        if (withServiceLevelSecurity == null) return immutableEntry(result, alreadyAssigned);

        if (!withServiceLevelSecurity.securedRead().equals("") && !alreadyAssigned.contains(SECURED)) {
            result.add(securityAnnotation(SECURED, withServiceLevelSecurity.securedRead()));
            alreadyAssigned.add(SECURED);
        }
        if (!withServiceLevelSecurity.rolesAllowedRead().equals("") && !alreadyAssigned.contains(ROLES_ALLOWED)) {
            result.add(securityAnnotation(ROLES_ALLOWED, withServiceLevelSecurity.rolesAllowedRead()));
            alreadyAssigned.add(ROLES_ALLOWED);
        }
        if (!withServiceLevelSecurity.preAuthorizeRead().equals("") && !alreadyAssigned.contains(PRE_AUTHORIZE)) {
            result.add(securityAnnotation(PRE_AUTHORIZE, withServiceLevelSecurity.preAuthorizeRead()));
            alreadyAssigned.add(PRE_AUTHORIZE);
        }
        if (!withServiceLevelSecurity.postAuthorizeRead().equals("") && !alreadyAssigned.contains(POST_AUTORIZE)) {
            result.add(securityAnnotation(POST_AUTORIZE, withServiceLevelSecurity.postAuthorizeRead()));
            alreadyAssigned.add(POST_AUTORIZE);
        }
        if (!withServiceLevelSecurity.preFilterRead().equals("") && !alreadyAssigned.contains(PRE_FILTER)) {
            AnnotationSpec.Builder builder = AnnotationSpec
                    .builder(PreFilter.class)
                    .addMember("value", "$S", withServiceLevelSecurity.preFilterRead());
            if (!withServiceLevelSecurity.preFilterTargetRead().equals("")) {
                builder.addMember("filterTarget", "$S", withServiceLevelSecurity.preFilterTargetRead());
            }
            result.add(builder.build());
            alreadyAssigned.add(PRE_FILTER);
        }
        if (!withServiceLevelSecurity.postFilterRead().equals("") && !alreadyAssigned.contains(POST_FILTER)) {
            result.add(securityAnnotation(POST_FILTER, withServiceLevelSecurity.postFilterRead()));
            alreadyAssigned.add(POST_FILTER);
        }
        return immutableEntry(result, alreadyAssigned);
    }
    
    public Map.Entry<List<AnnotationSpec>, List<Class<? extends Annotation>>> handleUpdate(Element element, List<Class<? extends Annotation>> alreadyAssigned) {
        List<AnnotationSpec> result = new ArrayList<>();
        WithServiceLevelSecurity withServiceLevelSecurity = element.getAnnotation(WithServiceLevelSecurity.class);
        if (withServiceLevelSecurity == null) return immutableEntry(result, alreadyAssigned);

        if (!withServiceLevelSecurity.securedUpdate().equals("") && !alreadyAssigned.contains(SECURED)) {
            result.add(securityAnnotation(SECURED, withServiceLevelSecurity.securedUpdate()));
            alreadyAssigned.add(SECURED);
        }
        if (!withServiceLevelSecurity.rolesAllowedUpdate().equals("") && !alreadyAssigned.contains(ROLES_ALLOWED)) {
            result.add(securityAnnotation(ROLES_ALLOWED, withServiceLevelSecurity.rolesAllowedUpdate()));
            alreadyAssigned.add(ROLES_ALLOWED);
        }
        if (!withServiceLevelSecurity.preAuthorizeUpdate().equals("") && !alreadyAssigned.contains(PRE_AUTHORIZE)) {
            result.add(securityAnnotation(PRE_AUTHORIZE, withServiceLevelSecurity.preAuthorizeUpdate()));
            alreadyAssigned.add(PRE_AUTHORIZE);
        }
        if (!withServiceLevelSecurity.postAuthorizeUpdate().equals("") && !alreadyAssigned.contains(POST_AUTORIZE)) {
            result.add(securityAnnotation(POST_AUTORIZE, withServiceLevelSecurity.postAuthorizeUpdate()));
            alreadyAssigned.add(POST_AUTORIZE);
        }
        if (!withServiceLevelSecurity.preFilterCreate().equals("") && !alreadyAssigned.contains(PRE_FILTER)) {
            AnnotationSpec.Builder builder = AnnotationSpec
                    .builder(PreFilter.class)
                    .addMember("value", "$S", withServiceLevelSecurity.preFilterUpdate());
            if (!withServiceLevelSecurity.preFilterTargetUpdate().equals("")) {
                builder.addMember("filterTarget", "$S", withServiceLevelSecurity.preFilterTargetUpdate());
            }
            result.add(builder.build());
            alreadyAssigned.add(PRE_FILTER);
        }
        if (!withServiceLevelSecurity.postFilterUpdate().equals("") && !alreadyAssigned.contains(POST_FILTER)) {
            result.add(securityAnnotation(POST_FILTER, withServiceLevelSecurity.postFilterUpdate()));
            alreadyAssigned.add(POST_FILTER);
        }
        return immutableEntry(result, alreadyAssigned);
    }
    
    public Map.Entry<List<AnnotationSpec>, List<Class<? extends Annotation>>> handleDelete(Element element, List<Class<? extends Annotation>> alreadyAssigned) {
        List<AnnotationSpec> result = new ArrayList<>();
        WithServiceLevelSecurity withServiceLevelSecurity = element.getAnnotation(WithServiceLevelSecurity.class);
        if (withServiceLevelSecurity == null) return immutableEntry(result, alreadyAssigned);

        if (!withServiceLevelSecurity.securedDelete().equals("") && !alreadyAssigned.contains(SECURED)) {
            result.add(securityAnnotation(SECURED, withServiceLevelSecurity.securedDelete()));
            alreadyAssigned.add(SECURED);
        }
        if (!withServiceLevelSecurity.rolesAllowedDelete().equals("") && !alreadyAssigned.contains(ROLES_ALLOWED)) {
            result.add(securityAnnotation(ROLES_ALLOWED, withServiceLevelSecurity.rolesAllowedDelete()));
            alreadyAssigned.add(ROLES_ALLOWED);
        }
        if (!withServiceLevelSecurity.preAuthorizeDelete().equals("") && !alreadyAssigned.contains(PRE_AUTHORIZE)) {
            result.add(securityAnnotation(PRE_AUTHORIZE, withServiceLevelSecurity.preAuthorizeDelete()));
            alreadyAssigned.add(PRE_AUTHORIZE);
        }
        if (!withServiceLevelSecurity.postAuthorizeDelete().equals("") && !alreadyAssigned.contains(POST_AUTORIZE)) {
            result.add(securityAnnotation(POST_AUTORIZE, withServiceLevelSecurity.postAuthorizeDelete()));
            alreadyAssigned.add(POST_AUTORIZE);
        }
        if (!withServiceLevelSecurity.preFilterCreate().equals("") && !alreadyAssigned.contains(PRE_FILTER)) {
            AnnotationSpec.Builder builder = AnnotationSpec
                    .builder(PreFilter.class)
                    .addMember("value", "$S", withServiceLevelSecurity.preFilterDelete());
            if (!withServiceLevelSecurity.preFilterTargetDelete().equals("")) {
                builder.addMember("filterTarget", "$S", withServiceLevelSecurity.preFilterTargetDelete());
            }
            result.add(builder.build());
            alreadyAssigned.add(PRE_FILTER);
        }
        if (!withServiceLevelSecurity.postFilterDelete().equals("") && !alreadyAssigned.contains(POST_FILTER)) {
            result.add(securityAnnotation(POST_FILTER, withServiceLevelSecurity.postFilterDelete()));
            alreadyAssigned.add(POST_FILTER);
        }

        return immutableEntry(result, alreadyAssigned);
    }

    private AnnotationSpec securityAnnotation(Class<? extends Annotation> annotation, String value){
        return AnnotationSpec.builder(annotation)
                .addMember("value", "$S", value)
                .build();
    }
    
    /*
    String secured() default "";
    String rolesAllowed() default "";
    String preAuthorize() default "";
    String postAuthorize() default "";
    String preFilter() default "";
    String preFilterTarget() default "";
    String postFilter() default "";
    * */
    
    private static final Class<? extends Annotation> SECURED = Secured.class;
    private static final Class<? extends Annotation> ROLES_ALLOWED = RolesAllowed.class;
    private static final Class<? extends Annotation> PRE_AUTHORIZE = PreAuthorize.class;
    private static final Class<? extends Annotation> POST_AUTORIZE = PostAuthorize.class;
    private static final Class<? extends Annotation> PRE_FILTER = PreFilter.class;
    private static final Class<? extends Annotation> POST_FILTER = PostFilter.class;
    
}

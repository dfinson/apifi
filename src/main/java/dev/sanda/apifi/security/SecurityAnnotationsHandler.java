package dev.sanda.apifi.security;


import com.squareup.javapoet.AnnotationSpec;
import dev.sanda.apifi.annotations.WithSecurity;
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
        WithSecurity withSecurity = element.getAnnotation(WithSecurity.class);
        if(withSecurity == null) return immutableEntry(result, alreadyAssigned);
        
        if(!withSecurity.secured().equals("") && !alreadyAssigned.contains(SECURED)){
            result.add(securityAnnotation(SECURED, withSecurity.secured()));
            alreadyAssigned.add(SECURED);
        }
        if(!withSecurity.rolesAllowed().equals("")&& !alreadyAssigned.contains(ROLES_ALLOWED)){
            result.add(securityAnnotation(ROLES_ALLOWED, withSecurity.rolesAllowed()));
            alreadyAssigned.add(ROLES_ALLOWED);
        }
        if(!withSecurity.preAuthorize().equals("")&& !alreadyAssigned.contains(PRE_AUTHORIZE)){
            result.add(securityAnnotation(PRE_AUTHORIZE, withSecurity.preAuthorize()));
            alreadyAssigned.add(PRE_AUTHORIZE);
        }
        if(!withSecurity.postAuthorize().equals("")&& !alreadyAssigned.contains(POST_AUTORIZE)){
            result.add(securityAnnotation(POST_AUTORIZE, withSecurity.postAuthorize()));
            alreadyAssigned.add(POST_AUTORIZE);
        }
        if(!withSecurity.preFilter().equals("")&& !alreadyAssigned.contains(PRE_FILTER)){
            AnnotationSpec.Builder builder = AnnotationSpec
                    .builder(PreFilter.class)
                    .addMember("value", "$S", withSecurity.preFilter());
            if(!withSecurity.preFilterTarget().equals("")){
                builder.addMember("filterTarget", "$S", withSecurity.preFilterTarget());
            }
            result.add(builder.build());
            alreadyAssigned.add(PRE_FILTER);
        }
        if(!withSecurity.postFilter().equals("")&& !alreadyAssigned.contains(POST_FILTER)){
            result.add(securityAnnotation(POST_FILTER, withSecurity.postFilter()));
            alreadyAssigned.add(POST_FILTER);
        }
        return immutableEntry(result, alreadyAssigned);
    }
    
    public Map.Entry<List<AnnotationSpec>, List<Class<? extends Annotation>>> handleCreate(Element element, List<Class<? extends Annotation>> alreadyAssigned){
        List<AnnotationSpec> result = new ArrayList<>();
        WithSecurity withSecurity = element.getAnnotation(WithSecurity.class);
        if(withSecurity == null) return immutableEntry(result, alreadyAssigned);

        if(!withSecurity.securedCreate().equals("") && !alreadyAssigned.contains(SECURED)){
            result.add(securityAnnotation(SECURED, withSecurity.securedCreate()));
            alreadyAssigned.add(SECURED);
        }
        if(!withSecurity.rolesAllowedCreate().equals("")&& !alreadyAssigned.contains(ROLES_ALLOWED)){
            result.add(securityAnnotation(ROLES_ALLOWED, withSecurity.rolesAllowedCreate()));
            alreadyAssigned.add(ROLES_ALLOWED);
        }
        if(!withSecurity.preAuthorizeCreate().equals("")&& !alreadyAssigned.contains(PRE_AUTHORIZE)){
            result.add(securityAnnotation(PRE_AUTHORIZE, withSecurity.preAuthorizeCreate()));
            alreadyAssigned.add(PRE_AUTHORIZE);
        }
        if(!withSecurity.postAuthorizeCreate().equals("")&& !alreadyAssigned.contains(POST_AUTORIZE)){
            result.add(securityAnnotation(POST_AUTORIZE, withSecurity.postAuthorizeCreate()));
            alreadyAssigned.add(POST_AUTORIZE);
        }
        if(!withSecurity.preFilterCreate().equals("")&& !alreadyAssigned.contains(PRE_FILTER)){
            AnnotationSpec.Builder builder = AnnotationSpec
                    .builder(PreFilter.class)
                    .addMember("value", "$S", withSecurity.preFilterCreate());
            if(!withSecurity.preFilterTargetCreate().equals("")){
                builder.addMember("filterTarget", "$S", withSecurity.preFilterTargetCreate());
            }
            result.add(builder.build());
            alreadyAssigned.add(PRE_FILTER);
        }
        if(!withSecurity.postFilterCreate().equals("")&& !alreadyAssigned.contains(POST_FILTER)){
            result.add(securityAnnotation(POST_FILTER, withSecurity.postFilterCreate()));
            alreadyAssigned.add(POST_FILTER);
        }
        return immutableEntry(result, alreadyAssigned);
    }
    
    public Map.Entry<List<AnnotationSpec>, List<Class<? extends Annotation>>> handleRead(Element element, List<Class<? extends Annotation>> alreadyAssigned) {
        List<AnnotationSpec> result = new ArrayList<>();
        WithSecurity withSecurity = element.getAnnotation(WithSecurity.class);
        if (withSecurity == null) return immutableEntry(result, alreadyAssigned);

        if (!withSecurity.securedRead().equals("") && !alreadyAssigned.contains(SECURED)) {
            result.add(securityAnnotation(SECURED, withSecurity.securedRead()));
            alreadyAssigned.add(SECURED);
        }
        if (!withSecurity.rolesAllowedRead().equals("") && !alreadyAssigned.contains(ROLES_ALLOWED)) {
            result.add(securityAnnotation(ROLES_ALLOWED, withSecurity.rolesAllowedRead()));
            alreadyAssigned.add(ROLES_ALLOWED);
        }
        if (!withSecurity.preAuthorizeRead().equals("") && !alreadyAssigned.contains(PRE_AUTHORIZE)) {
            result.add(securityAnnotation(PRE_AUTHORIZE, withSecurity.preAuthorizeRead()));
            alreadyAssigned.add(PRE_AUTHORIZE);
        }
        if (!withSecurity.postAuthorizeRead().equals("") && !alreadyAssigned.contains(POST_AUTORIZE)) {
            result.add(securityAnnotation(POST_AUTORIZE, withSecurity.postAuthorizeRead()));
            alreadyAssigned.add(POST_AUTORIZE);
        }
        if (!withSecurity.preFilterRead().equals("") && !alreadyAssigned.contains(PRE_FILTER)) {
            AnnotationSpec.Builder builder = AnnotationSpec
                    .builder(PreFilter.class)
                    .addMember("value", "$S", withSecurity.preFilterRead());
            if (!withSecurity.preFilterTargetRead().equals("")) {
                builder.addMember("filterTarget", "$S", withSecurity.preFilterTargetRead());
            }
            result.add(builder.build());
            alreadyAssigned.add(PRE_FILTER);
        }
        if (!withSecurity.postFilterRead().equals("") && !alreadyAssigned.contains(POST_FILTER)) {
            result.add(securityAnnotation(POST_FILTER, withSecurity.postFilterRead()));
            alreadyAssigned.add(POST_FILTER);
        }
        return immutableEntry(result, alreadyAssigned);
    }
    
    public Map.Entry<List<AnnotationSpec>, List<Class<? extends Annotation>>> handleUpdate(Element element, List<Class<? extends Annotation>> alreadyAssigned) {
        List<AnnotationSpec> result = new ArrayList<>();
        WithSecurity withSecurity = element.getAnnotation(WithSecurity.class);
        if (withSecurity == null) return immutableEntry(result, alreadyAssigned);

        if (!withSecurity.securedUpdate().equals("") && !alreadyAssigned.contains(SECURED)) {
            result.add(securityAnnotation(SECURED, withSecurity.securedUpdate()));
            alreadyAssigned.add(SECURED);
        }
        if (!withSecurity.rolesAllowedUpdate().equals("") && !alreadyAssigned.contains(ROLES_ALLOWED)) {
            result.add(securityAnnotation(ROLES_ALLOWED, withSecurity.rolesAllowedUpdate()));
            alreadyAssigned.add(ROLES_ALLOWED);
        }
        if (!withSecurity.preAuthorizeUpdate().equals("") && !alreadyAssigned.contains(PRE_AUTHORIZE)) {
            result.add(securityAnnotation(PRE_AUTHORIZE, withSecurity.preAuthorizeUpdate()));
            alreadyAssigned.add(PRE_AUTHORIZE);
        }
        if (!withSecurity.postAuthorizeUpdate().equals("") && !alreadyAssigned.contains(POST_AUTORIZE)) {
            result.add(securityAnnotation(POST_AUTORIZE, withSecurity.postAuthorizeUpdate()));
            alreadyAssigned.add(POST_AUTORIZE);
        }
        if (!withSecurity.preFilterCreate().equals("") && !alreadyAssigned.contains(PRE_FILTER)) {
            AnnotationSpec.Builder builder = AnnotationSpec
                    .builder(PreFilter.class)
                    .addMember("value", "$S", withSecurity.preFilterUpdate());
            if (!withSecurity.preFilterTargetUpdate().equals("")) {
                builder.addMember("filterTarget", "$S", withSecurity.preFilterTargetUpdate());
            }
            result.add(builder.build());
            alreadyAssigned.add(PRE_FILTER);
        }
        if (!withSecurity.postFilterUpdate().equals("") && !alreadyAssigned.contains(POST_FILTER)) {
            result.add(securityAnnotation(POST_FILTER, withSecurity.postFilterUpdate()));
            alreadyAssigned.add(POST_FILTER);
        }
        return immutableEntry(result, alreadyAssigned);
    }
    
    public Map.Entry<List<AnnotationSpec>, List<Class<? extends Annotation>>> handleDelete(Element element, List<Class<? extends Annotation>> alreadyAssigned) {
        List<AnnotationSpec> result = new ArrayList<>();
        WithSecurity withSecurity = element.getAnnotation(WithSecurity.class);
        if (withSecurity == null) return immutableEntry(result, alreadyAssigned);

        if (!withSecurity.securedDelete().equals("") && !alreadyAssigned.contains(SECURED)) {
            result.add(securityAnnotation(SECURED, withSecurity.securedDelete()));
            alreadyAssigned.add(SECURED);
        }
        if (!withSecurity.rolesAllowedDelete().equals("") && !alreadyAssigned.contains(ROLES_ALLOWED)) {
            result.add(securityAnnotation(ROLES_ALLOWED, withSecurity.rolesAllowedDelete()));
            alreadyAssigned.add(ROLES_ALLOWED);
        }
        if (!withSecurity.preAuthorizeDelete().equals("") && !alreadyAssigned.contains(PRE_AUTHORIZE)) {
            result.add(securityAnnotation(PRE_AUTHORIZE, withSecurity.preAuthorizeDelete()));
            alreadyAssigned.add(PRE_AUTHORIZE);
        }
        if (!withSecurity.postAuthorizeDelete().equals("") && !alreadyAssigned.contains(POST_AUTORIZE)) {
            result.add(securityAnnotation(POST_AUTORIZE, withSecurity.postAuthorizeDelete()));
            alreadyAssigned.add(POST_AUTORIZE);
        }
        if (!withSecurity.preFilterCreate().equals("") && !alreadyAssigned.contains(PRE_FILTER)) {
            AnnotationSpec.Builder builder = AnnotationSpec
                    .builder(PreFilter.class)
                    .addMember("value", "$S", withSecurity.preFilterDelete());
            if (!withSecurity.preFilterTargetDelete().equals("")) {
                builder.addMember("filterTarget", "$S", withSecurity.preFilterTargetDelete());
            }
            result.add(builder.build());
            alreadyAssigned.add(PRE_FILTER);
        }
        if (!withSecurity.postFilterDelete().equals("") && !alreadyAssigned.contains(POST_FILTER)) {
            result.add(securityAnnotation(POST_FILTER, withSecurity.postFilterDelete()));
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

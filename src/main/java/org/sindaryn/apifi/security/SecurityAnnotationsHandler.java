package org.sindaryn.apifi.security;


import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.MethodSpec;
import org.sindaryn.apifi.annotations.Secure;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.prepost.PreFilter;

import javax.annotation.security.RolesAllowed;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import java.lang.annotation.Annotation;
import java.security.Security;
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
        Secure secure = element.getAnnotation(Secure.class);
        if(secure == null) return immutableEntry(result, alreadyAssigned);
        
        if(!secure.secured().equals("") && !alreadyAssigned.contains(SECURED)){
            result.add(securityAnnotation(SECURED, secure.secured()));
            alreadyAssigned.add(SECURED);
        }
        if(!secure.rolesAllowed().equals("")&& !alreadyAssigned.contains(ROLES_ALLOWED)){
            result.add(securityAnnotation(ROLES_ALLOWED, secure.rolesAllowed()));
            alreadyAssigned.add(ROLES_ALLOWED);
        }
        if(!secure.preAuthorize().equals("")&& !alreadyAssigned.contains(PRE_AUTHORIZE)){
            result.add(securityAnnotation(PRE_AUTHORIZE, secure.preAuthorize()));
            alreadyAssigned.add(PRE_AUTHORIZE);
        }
        if(!secure.postAuthorize().equals("")&& !alreadyAssigned.contains(POST_AUTORIZE)){
            result.add(securityAnnotation(POST_AUTORIZE, secure.postAuthorize()));
            alreadyAssigned.add(POST_AUTORIZE);
        }
        if(!secure.preFilter().equals("")&& !alreadyAssigned.contains(PRE_FILTER)){
            AnnotationSpec.Builder builder = AnnotationSpec
                    .builder(PreFilter.class)
                    .addMember("value", "$S", secure.preFilter());
            if(!secure.preFilterTarget().equals("")){
                builder.addMember("filterTarget", "$S", secure.preFilterTarget());
            }
            result.add(builder.build());
            alreadyAssigned.add(PRE_FILTER);
        }
        if(!secure.postFilter().equals("")&& !alreadyAssigned.contains(POST_FILTER)){
            result.add(securityAnnotation(POST_FILTER, secure.postFilter()));
            alreadyAssigned.add(POST_FILTER);
        }
        return immutableEntry(result, alreadyAssigned);
    }
    
    public Map.Entry<List<AnnotationSpec>, List<Class<? extends Annotation>>> handleCreate(Element element, List<Class<? extends Annotation>> alreadyAssigned){
        List<AnnotationSpec> result = new ArrayList<>();
        Secure secure = element.getAnnotation(Secure.class);
        if(secure == null) return immutableEntry(result, alreadyAssigned);

        if(!secure.securedCreate().equals("") && !alreadyAssigned.contains(SECURED)){
            result.add(securityAnnotation(SECURED, secure.securedCreate()));
            alreadyAssigned.add(SECURED);
        }
        if(!secure.rolesAllowedCreate().equals("")&& !alreadyAssigned.contains(ROLES_ALLOWED)){
            result.add(securityAnnotation(ROLES_ALLOWED, secure.rolesAllowedCreate()));
            alreadyAssigned.add(ROLES_ALLOWED);
        }
        if(!secure.preAuthorizeCreate().equals("")&& !alreadyAssigned.contains(PRE_AUTHORIZE)){
            result.add(securityAnnotation(PRE_AUTHORIZE, secure.preAuthorizeCreate()));
            alreadyAssigned.add(PRE_AUTHORIZE);
        }
        if(!secure.postAuthorizeCreate().equals("")&& !alreadyAssigned.contains(POST_AUTORIZE)){
            result.add(securityAnnotation(POST_AUTORIZE, secure.postAuthorizeCreate()));
            alreadyAssigned.add(POST_AUTORIZE);
        }
        if(!secure.preFilterCreate().equals("")&& !alreadyAssigned.contains(PRE_FILTER)){
            AnnotationSpec.Builder builder = AnnotationSpec
                    .builder(PreFilter.class)
                    .addMember("value", "$S", secure.preFilterCreate());
            if(!secure.preFilterTargetCreate().equals("")){
                builder.addMember("filterTarget", "$S", secure.preFilterTargetCreate());
            }
            result.add(builder.build());
            alreadyAssigned.add(PRE_FILTER);
        }
        if(!secure.postFilterCreate().equals("")&& !alreadyAssigned.contains(POST_FILTER)){
            result.add(securityAnnotation(POST_FILTER, secure.postFilterCreate()));
            alreadyAssigned.add(POST_FILTER);
        }
        return immutableEntry(result, alreadyAssigned);
    }
    
    public Map.Entry<List<AnnotationSpec>, List<Class<? extends Annotation>>> handleRead(Element element, List<Class<? extends Annotation>> alreadyAssigned) {
        List<AnnotationSpec> result = new ArrayList<>();
        Secure secure = element.getAnnotation(Secure.class);
        if (secure == null) return immutableEntry(result, alreadyAssigned);

        if (!secure.securedRead().equals("") && !alreadyAssigned.contains(SECURED)) {
            result.add(securityAnnotation(SECURED, secure.securedRead()));
            alreadyAssigned.add(SECURED);
        }
        if (!secure.rolesAllowedRead().equals("") && !alreadyAssigned.contains(ROLES_ALLOWED)) {
            result.add(securityAnnotation(ROLES_ALLOWED, secure.rolesAllowedRead()));
            alreadyAssigned.add(ROLES_ALLOWED);
        }
        if (!secure.preAuthorizeRead().equals("") && !alreadyAssigned.contains(PRE_AUTHORIZE)) {
            result.add(securityAnnotation(PRE_AUTHORIZE, secure.preAuthorizeRead()));
            alreadyAssigned.add(PRE_AUTHORIZE);
        }
        if (!secure.postAuthorizeRead().equals("") && !alreadyAssigned.contains(POST_AUTORIZE)) {
            result.add(securityAnnotation(POST_AUTORIZE, secure.postAuthorizeRead()));
            alreadyAssigned.add(POST_AUTORIZE);
        }
        if (!secure.preFilterRead().equals("") && !alreadyAssigned.contains(PRE_FILTER)) {
            AnnotationSpec.Builder builder = AnnotationSpec
                    .builder(PreFilter.class)
                    .addMember("value", "$S", secure.preFilterRead());
            if (!secure.preFilterTargetRead().equals("")) {
                builder.addMember("filterTarget", "$S", secure.preFilterTargetRead());
            }
            result.add(builder.build());
            alreadyAssigned.add(PRE_FILTER);
        }
        if (!secure.postFilterRead().equals("") && !alreadyAssigned.contains(POST_FILTER)) {
            result.add(securityAnnotation(POST_FILTER, secure.postFilterRead()));
            alreadyAssigned.add(POST_FILTER);
        }
        return immutableEntry(result, alreadyAssigned);
    }
    
    public Map.Entry<List<AnnotationSpec>, List<Class<? extends Annotation>>> handleUpdate(Element element, List<Class<? extends Annotation>> alreadyAssigned) {
        List<AnnotationSpec> result = new ArrayList<>();
        Secure secure = element.getAnnotation(Secure.class);
        if (secure == null) return immutableEntry(result, alreadyAssigned);

        if (!secure.securedUpdate().equals("") && !alreadyAssigned.contains(SECURED)) {
            result.add(securityAnnotation(SECURED, secure.securedUpdate()));
            alreadyAssigned.add(SECURED);
        }
        if (!secure.rolesAllowedUpdate().equals("") && !alreadyAssigned.contains(ROLES_ALLOWED)) {
            result.add(securityAnnotation(ROLES_ALLOWED, secure.rolesAllowedUpdate()));
            alreadyAssigned.add(ROLES_ALLOWED);
        }
        if (!secure.preAuthorizeUpdate().equals("") && !alreadyAssigned.contains(PRE_AUTHORIZE)) {
            result.add(securityAnnotation(PRE_AUTHORIZE, secure.preAuthorizeUpdate()));
            alreadyAssigned.add(PRE_AUTHORIZE);
        }
        if (!secure.postAuthorizeUpdate().equals("") && !alreadyAssigned.contains(POST_AUTORIZE)) {
            result.add(securityAnnotation(POST_AUTORIZE, secure.postAuthorizeUpdate()));
            alreadyAssigned.add(POST_AUTORIZE);
        }
        if (!secure.preFilterCreate().equals("") && !alreadyAssigned.contains(PRE_FILTER)) {
            AnnotationSpec.Builder builder = AnnotationSpec
                    .builder(PreFilter.class)
                    .addMember("value", "$S", secure.preFilterUpdate());
            if (!secure.preFilterTargetUpdate().equals("")) {
                builder.addMember("filterTarget", "$S", secure.preFilterTargetUpdate());
            }
            result.add(builder.build());
            alreadyAssigned.add(PRE_FILTER);
        }
        if (!secure.postFilterUpdate().equals("") && !alreadyAssigned.contains(POST_FILTER)) {
            result.add(securityAnnotation(POST_FILTER, secure.postFilterUpdate()));
            alreadyAssigned.add(POST_FILTER);
        }
        return immutableEntry(result, alreadyAssigned);
    }
    
    public Map.Entry<List<AnnotationSpec>, List<Class<? extends Annotation>>> handleDelete(Element element, List<Class<? extends Annotation>> alreadyAssigned) {
        List<AnnotationSpec> result = new ArrayList<>();
        Secure secure = element.getAnnotation(Secure.class);
        if (secure == null) return immutableEntry(result, alreadyAssigned);

        if (!secure.securedDelete().equals("") && !alreadyAssigned.contains(SECURED)) {
            result.add(securityAnnotation(SECURED, secure.securedDelete()));
            alreadyAssigned.add(SECURED);
        }
        if (!secure.rolesAllowedDelete().equals("") && !alreadyAssigned.contains(ROLES_ALLOWED)) {
            result.add(securityAnnotation(ROLES_ALLOWED, secure.rolesAllowedDelete()));
            alreadyAssigned.add(ROLES_ALLOWED);
        }
        if (!secure.preAuthorizeDelete().equals("") && !alreadyAssigned.contains(PRE_AUTHORIZE)) {
            result.add(securityAnnotation(PRE_AUTHORIZE, secure.preAuthorizeDelete()));
            alreadyAssigned.add(PRE_AUTHORIZE);
        }
        if (!secure.postAuthorizeDelete().equals("") && !alreadyAssigned.contains(POST_AUTORIZE)) {
            result.add(securityAnnotation(POST_AUTORIZE, secure.postAuthorizeDelete()));
            alreadyAssigned.add(POST_AUTORIZE);
        }
        if (!secure.preFilterCreate().equals("") && !alreadyAssigned.contains(PRE_FILTER)) {
            AnnotationSpec.Builder builder = AnnotationSpec
                    .builder(PreFilter.class)
                    .addMember("value", "$S", secure.preFilterDelete());
            if (!secure.preFilterTargetDelete().equals("")) {
                builder.addMember("filterTarget", "$S", secure.preFilterTargetDelete());
            }
            result.add(builder.build());
            alreadyAssigned.add(PRE_FILTER);
        }
        if (!secure.postFilterDelete().equals("") && !alreadyAssigned.contains(POST_FILTER)) {
            result.add(securityAnnotation(POST_FILTER, secure.postFilterDelete()));
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

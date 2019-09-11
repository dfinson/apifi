package org.sindaryn.apifi.generator;

import com.squareup.javapoet.*;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.sindaryn.apifi.annotations.GraphQLApiEntity;
import org.sindaryn.apifi.annotations.MetaOperations;
import org.sindaryn.apifi.service.EmbeddedCollectionMetaOperations;
import org.sindaryn.datafi.persistence.Archivable;
import org.sindaryn.datafi.reflection.ReflectionCache;
import org.sindaryn.datafi.service.ArchivableDataManager;
import org.sindaryn.datafi.service.DataManager;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

import static org.sindaryn.apifi.StaticUtils.*;
import static org.sindaryn.datafi.StaticUtils.toPascalCase;

/**
 * Helper class for the definition of Javapoet generated class fields
 */

@RequiredArgsConstructor
public class FieldSpecs {

    @NonNull
    private ProcessingEnvironment processingEnv;
    @NonNull
    private EntitiesInfoCache entitiesInfoCache;

    public FieldSpec reflectionCache() {
        return FieldSpec.builder(ReflectionCache.class, reflectionCache, Modifier.PRIVATE)
                .addAnnotation(Autowired.class)
                .build();
    }

    public FieldSpec dataManager(TypeElement entity){
        Class<?> clazz =
                isArchivable(entity, processingEnv) ? ArchivableDataManager.class : DataManager.class;
        return FieldSpec.builder(
                ParameterizedTypeName.get(ClassName.get(clazz), ClassName.get(entity)),
                camelcaseNameOf(entity) + "DataManager")
                .addAnnotation(Autowired.class)
                .addModifiers(Modifier.PRIVATE)
                .build();
    }

    public FieldSpec dataManager(TypeMirror entityType, String prefix){
        return FieldSpec.builder(
                ParameterizedTypeName.get(ClassName.get(DataManager.class), ClassName.get(entityType)),
                prefix + toPascalCase(DataManager.class.getSimpleName()))
                .addAnnotation(Autowired.class)
                .addModifiers(Modifier.PRIVATE)
                .build();
    }

    public FieldSpec metaOps(GraphQLApiEntity apiEntityAnnotation, TypeElement entity) {
        TypeElement metaOps = apiMetaOpsClazz(apiEntityAnnotation);
        return FieldSpec.builder(
                ParameterizedTypeName.get(ClassName.get(metaOps), ClassName.get(entity)),
                metaOpsName(entity))
                .addAnnotation(Autowired.class)
                .addModifiers(Modifier.PRIVATE)
                .build();
    }

    //This is the most concise solution I could find for accessing a class type token
    //within an annotations processor... weird I know.
    private TypeElement apiMetaOpsClazz(GraphQLApiEntity apiEntity){
        try
        {
            apiEntity.apiMetaOperations(); // this should throw
        }
        //the type token is in the exception itself
        catch( MirroredTypeException mte )
        {
            Types TypeUtils = this.processingEnv.getTypeUtils();
            return (TypeElement)TypeUtils.asElement(mte.getTypeMirror());
        }
        return null; // can this ever happen ??
    }

    public FieldSpec embeddedCollectionMetaOps(VariableElement field) {
        TypeElement metaOps = null;
        ParameterizedTypeName metaOpsType = null;
        MetaOperations metaOperations = field.getAnnotation(MetaOperations.class);
        if(metaOperations != null){
            try{
                metaOperations.metaOps();
            }catch (MirroredTypeException mte){
                Types TypeUtils = this.processingEnv.getTypeUtils();
                metaOps = (TypeElement)TypeUtils.asElement(mte.getTypeMirror());
                metaOpsType = ParameterizedTypeName.get(ClassName.get(metaOps));
            }
        }else {
            TypeElement typeElement =
                    entitiesInfoCache
                            .getTypeElementMap()
                            .get(entitiesInfoCache.getCollectionType(field));
            metaOpsType =
                    ParameterizedTypeName.get(
                    ClassName.get(EmbeddedCollectionMetaOperations.class),
                    ClassName.get(typeElement),
                    ClassName.get((TypeElement) field.getEnclosingElement()));
        }
        return
                FieldSpec
                .builder(metaOpsType,
                        camelcaseNameOf(field) +
                              EmbeddedCollectionMetaOperations.class.getSimpleName(),
                        Modifier.PRIVATE)
                        .addAnnotation(Autowired.class)
                .build();
    }
}

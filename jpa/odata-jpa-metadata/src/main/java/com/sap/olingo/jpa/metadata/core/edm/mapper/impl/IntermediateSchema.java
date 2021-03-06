package com.sap.olingo.jpa.metadata.core.edm.mapper.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.EmbeddableType;
import javax.persistence.metamodel.EntityType;
import javax.persistence.metamodel.Metamodel;

import org.apache.olingo.commons.api.edm.provider.CsdlAction;
import org.apache.olingo.commons.api.edm.provider.CsdlComplexType;
import org.apache.olingo.commons.api.edm.provider.CsdlEntityType;
import org.apache.olingo.commons.api.edm.provider.CsdlFunction;
import org.apache.olingo.commons.api.edm.provider.CsdlSchema;
import org.reflections.Reflections;

import com.sap.olingo.jpa.metadata.core.edm.mapper.api.JPAAction;
import com.sap.olingo.jpa.metadata.core.edm.mapper.api.JPAEntityType;
import com.sap.olingo.jpa.metadata.core.edm.mapper.api.JPAFunction;
import com.sap.olingo.jpa.metadata.core.edm.mapper.api.JPAStructuredType;
import com.sap.olingo.jpa.metadata.core.edm.mapper.exception.ODataJPAModelException;

/**
 * <p>For details about Schema metadata see:
 * <a href=
 * "https://docs.oasis-open.org/odata/odata/v4.0/errata02/os/complete/part3-csdl/odata-v4.0-errata02-os-part3-csdl-complete.html#_Toc406397946"
 * >OData Version 4.0 Part 3 - 5 Schema </a>
 * @author Oliver Grande
 *
 */
final class IntermediateSchema extends IntermediateModelElement {
  private final Metamodel jpaMetamodel;
  private final Map<String, IntermediateComplexType> complexTypeListInternalKey;
  private final Map<String, IntermediateEntityType> entityTypeListInternalKey;
  private final Map<String, IntermediateFunction> functionListInternalKey;
  private final Map<String, IntermediateJavaAction> actionListInternalKey;
  private IntermediateEntityContainer container;
  private final Reflections reflections;
  private CsdlSchema edmSchema;

  IntermediateSchema(final JPAEdmNameBuilder nameBuilder, final Metamodel jpaMetamodel, final Reflections reflections)
      throws ODataJPAModelException {

    super(nameBuilder, nameBuilder.buildNamespace());
    this.reflections = reflections;
    this.jpaMetamodel = jpaMetamodel;
    this.complexTypeListInternalKey = buildComplexTypeList();
    this.entityTypeListInternalKey = buildEntityTypeList();
    this.functionListInternalKey = buildFunctionList();
    this.actionListInternalKey = buildActionList();
  }

  @SuppressWarnings("unchecked")
  @Override
  protected void lazyBuildEdmItem() throws ODataJPAModelException {
    edmSchema = new CsdlSchema();
    edmSchema.setNamespace(nameBuilder.buildNamespace());
    edmSchema.setComplexTypes((List<CsdlComplexType>) extractEdmModelElements(complexTypeListInternalKey));
    edmSchema.setEntityTypes((List<CsdlEntityType>) extractEdmModelElements(entityTypeListInternalKey));
    edmSchema.setFunctions((List<CsdlFunction>) extractEdmModelElements(functionListInternalKey));
    edmSchema.setActions((List<CsdlAction>) extractEdmModelElements(actionListInternalKey));
//  edm:Action
//  edm:Annotations
//  edm:Annotation
//  edm:EnumType --> Annotation @Enummerated
//  edm:Term
//  edm:TypeDefinition
    // MUST be the last thing that is done !!!!
    if (container != null)
      edmSchema.setEntityContainer(container.getEdmItem());

  }

  @Override
  CsdlSchema getEdmItem() throws ODataJPAModelException {
    if (edmSchema == null)
      lazyBuildEdmItem();
    return edmSchema;
  }

  IntermediateStructuredType getStructuredType(final Attribute<?, ?> jpaAttribute) {
    IntermediateStructuredType type = complexTypeListInternalKey.get(IntNameBuilder.buildStructuredTypeName(jpaAttribute
        .getJavaType()));
    if (type == null)
      type = entityTypeListInternalKey.get(IntNameBuilder.buildStructuredTypeName(jpaAttribute.getJavaType()));
    return type;
  }

  IntermediateStructuredType getStructuredType(final Class<?> targetClass) {
    IntermediateStructuredType type = entityTypeListInternalKey
        .get(IntNameBuilder.buildStructuredTypeName(targetClass));
    if (type == null)
      type = complexTypeListInternalKey.get(IntNameBuilder.buildStructuredTypeName(targetClass));
    return type;
  }

  IntermediateStructuredType getEntityType(final Class<?> targetClass) {
    return entityTypeListInternalKey.get(IntNameBuilder.buildStructuredTypeName(targetClass));
  }

  IntermediateStructuredType getComplexType(final Class<?> targetClass) {
    return complexTypeListInternalKey.get(IntNameBuilder.buildStructuredTypeName(targetClass));
  }

  JPAStructuredType getComplexType(final String externalName) {
    for (final Map.Entry<String, IntermediateComplexType> complexType : complexTypeListInternalKey.entrySet()) {
      if (complexType.getValue().getExternalName().equals(externalName))
        return complexType.getValue();
    }
    return null;
  }

  JPAEntityType getEntityType(final String externalName) {
    for (final String internalName : entityTypeListInternalKey.keySet()) {
      if (entityTypeListInternalKey.get(internalName).getExternalName().equals(externalName))
        return entityTypeListInternalKey.get(internalName);
    }
    return null;
  }

  List<IntermediateEntityType> getEntityTypes() {
    final List<IntermediateEntityType> entityTypes = new ArrayList<IntermediateEntityType>();
    for (final String internalName : entityTypeListInternalKey.keySet()) {
      entityTypes.add(entityTypeListInternalKey.get(internalName));
    }
    return entityTypes;
  }

  JPAFunction getFunction(final String externalName) {
    for (final String internalName : functionListInternalKey.keySet()) {
      if (functionListInternalKey.get(internalName).getExternalName().equals(externalName)) {
        if (!functionListInternalKey.get(internalName).ignore())
          return functionListInternalKey.get(internalName);
      }
    }
    return null;
  }

  List<JPAFunction> getFunctions() {
    final ArrayList<JPAFunction> functions = new ArrayList<JPAFunction>();
    for (final String internalName : functionListInternalKey.keySet()) {
      functions.add(functionListInternalKey.get(internalName));
    }
    return functions;
  }

  JPAAction getAction(final String externalName) {
    for (final Entry<String, IntermediateJavaAction> action : actionListInternalKey.entrySet()) {
      if (action.getValue().getExternalName().equals(externalName)) {
        if (!action.getValue().ignore())
          return action.getValue();
      }
    }
    return null;
  }

  List<JPAAction> getActions() {
    final ArrayList<JPAAction> actions = new ArrayList<JPAAction>();
    for (final Entry<String, IntermediateJavaAction> action : actionListInternalKey.entrySet()) {
      actions.add(action.getValue());
    }
    return actions;
  }

  void setContainer(final IntermediateEntityContainer container) {
    this.container = container;
  }

  private Map<String, IntermediateComplexType> buildComplexTypeList() throws ODataJPAModelException {
    final HashMap<String, IntermediateComplexType> ctList = new HashMap<String, IntermediateComplexType>();

    for (final EmbeddableType<?> embeddable : this.jpaMetamodel.getEmbeddables()) {
      final IntermediateComplexType ct = new IntermediateComplexType(nameBuilder, embeddable, this);
      ctList.put(ct.internalName, ct);
    }
    return ctList;
  }

  private Map<String, IntermediateEntityType> buildEntityTypeList() throws ODataJPAModelException {
    final HashMap<String, IntermediateEntityType> etList = new HashMap<String, IntermediateEntityType>();

    for (final EntityType<?> entity : this.jpaMetamodel.getEntities()) {
      final IntermediateEntityType et = new IntermediateEntityType(nameBuilder, entity, this);
      etList.put(et.internalName, et);
    }
    return etList;
  }

  private Map<String, IntermediateFunction> buildFunctionList() throws ODataJPAModelException {
    final HashMap<String, IntermediateFunction> funcList = new HashMap<String, IntermediateFunction>();
    // 1. Option: Create Function from Entity Annotations
    final IntermediateFunctionFactory factory = new IntermediateFunctionFactory();
    for (final EntityType<?> entity : this.jpaMetamodel.getEntities()) {

      funcList.putAll(factory.create(nameBuilder, entity, this));
    }
    // 2. Option: Create Function from Java Classes
    funcList.putAll(factory.create(nameBuilder, reflections, this));
    return funcList;
  }

  private Map<String, IntermediateJavaAction> buildActionList() throws ODataJPAModelException {
    final HashMap<String, IntermediateJavaAction> actionList = new HashMap<String, IntermediateJavaAction>();
    final IntermediateActionFactory factory = new IntermediateActionFactory();
    actionList.putAll(factory.create(nameBuilder, reflections, this));
    return actionList;
  }

}

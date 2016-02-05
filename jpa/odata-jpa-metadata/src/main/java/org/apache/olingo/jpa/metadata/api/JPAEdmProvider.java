package org.apache.olingo.jpa.metadata.api;

import java.util.List;

import javax.persistence.EntityManagerFactory;

import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.apache.olingo.commons.api.edm.provider.CsdlAbstractEdmProvider;
import org.apache.olingo.commons.api.edm.provider.CsdlComplexType;
import org.apache.olingo.commons.api.edm.provider.CsdlEntityContainer;
import org.apache.olingo.commons.api.edm.provider.CsdlEntityContainerInfo;
import org.apache.olingo.commons.api.edm.provider.CsdlEntitySet;
import org.apache.olingo.commons.api.edm.provider.CsdlEntityType;
import org.apache.olingo.commons.api.edm.provider.CsdlFunction;
import org.apache.olingo.commons.api.edm.provider.CsdlFunctionImport;
import org.apache.olingo.commons.api.edm.provider.CsdlSchema;
import org.apache.olingo.commons.api.ex.ODataException;
import org.apache.olingo.jpa.metadata.core.edm.mapper.impl.JPAEdmNameBuilder;
import org.apache.olingo.jpa.metadata.core.edm.mapper.impl.ServicDocument;

public class JPAEdmProvider extends CsdlAbstractEdmProvider {

  // final private JPAEdmPostProcessor postProcessor;
  final private JPAEdmNameBuilder nameBuilder;
  // final private List<CsdlSchema> schemas;
  final private ServicDocument serviceDocument;

  public JPAEdmProvider(String namespace, EntityManagerFactory emf, JPAEdmPostProcessor postProcessor)
      throws ODataException {
    super();
    this.nameBuilder = new JPAEdmNameBuilder(namespace);

    // schemas = buildSchemas();
    serviceDocument = new ServicDocument(namespace, emf.getMetamodel(), postProcessor);
  }

  @Override
  public CsdlComplexType getComplexType(FullQualifiedName complexTypeName) throws ODataException {
    for (CsdlSchema schema : serviceDocument.getEdmSchemas()) {
      if (schema.getNamespace().equals(complexTypeName.getNamespace())) {
        return schema.getComplexType(complexTypeName.getName());
      }
    }
    return null;
  }

  @Override
  public CsdlEntityContainer getEntityContainer() throws ODataException {
    return serviceDocument.getEdmEntityContainer();
  }

  @Override
  public CsdlEntityContainerInfo getEntityContainerInfo(FullQualifiedName entityContainerName) throws ODataException {
    // This method is invoked when displaying the Service Document at e.g.
    // http://localhost:8080/DemoService/DemoService.svc
    if (entityContainerName == null || entityContainerName.equals(nameBuilder.buildContainerName())) {
      CsdlEntityContainerInfo entityContainerInfo = new CsdlEntityContainerInfo();
      entityContainerInfo.setContainerName(nameBuilder.buildFQN(nameBuilder.buildContainerName()));
      return entityContainerInfo;
    }
    return null;
  }

  @Override
  public CsdlEntitySet getEntitySet(FullQualifiedName entityContainerFQN, String entitySetName) throws ODataException {
    CsdlEntityContainer container = serviceDocument.getEdmEntityContainer();
    if (entityContainerFQN.equals(nameBuilder.buildFQN(container.getName()))) {
      return container.getEntitySet(entitySetName);
    }
    return null;
  }

  @Override
  public CsdlEntityType getEntityType(FullQualifiedName entityTypeName) throws ODataException {

    for (CsdlSchema schema : serviceDocument.getEdmSchemas()) {
      if (schema.getNamespace().equals(entityTypeName.getNamespace())) {
        return schema.getEntityType(entityTypeName.getName());
      }
    }
    return null;
  }

  @Override
  public CsdlFunctionImport getFunctionImport(FullQualifiedName entityContainerFQN, String functionImportName)
      throws ODataException {
    CsdlEntityContainer container = serviceDocument.getEdmEntityContainer();
    if (entityContainerFQN.equals(nameBuilder.buildFQN(container.getName()))) {
      return container.getFunctionImport(functionImportName);
    }
    return null;
  }

  @Override
  public List<CsdlFunction> getFunctions(FullQualifiedName functionName) throws ODataException {
    for (CsdlSchema schema : serviceDocument.getEdmSchemas()) {
      if (schema.getNamespace().equals(functionName.getNamespace())) {
        return schema.getFunctions(functionName.getName());
      }
    }
    return null;
  }

  @Override
  public List<CsdlSchema> getSchemas() throws ODataException {
    return serviceDocument.getEdmSchemas();
  }

  public final ServicDocument getServiceDocument() {
    return serviceDocument;
  }
}

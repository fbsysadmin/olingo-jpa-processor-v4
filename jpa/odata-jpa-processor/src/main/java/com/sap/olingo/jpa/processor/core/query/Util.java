package com.sap.olingo.jpa.processor.core.query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.olingo.commons.api.edm.EdmBindingTarget;
import org.apache.olingo.commons.api.edm.EdmEntitySet;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.commons.api.edm.EdmType;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.ODataApplicationException;
import org.apache.olingo.server.api.uri.UriParameter;
import org.apache.olingo.server.api.uri.UriResource;
import org.apache.olingo.server.api.uri.UriResourceComplexProperty;
import org.apache.olingo.server.api.uri.UriResourceEntitySet;
import org.apache.olingo.server.api.uri.UriResourceKind;
import org.apache.olingo.server.api.uri.UriResourceLambdaVariable;
import org.apache.olingo.server.api.uri.UriResourceNavigation;
import org.apache.olingo.server.api.uri.UriResourcePartTyped;
import org.apache.olingo.server.api.uri.UriResourceProperty;
import org.apache.olingo.server.api.uri.UriResourceValue;
import org.apache.olingo.server.api.uri.queryoption.ExpandItem;
import org.apache.olingo.server.api.uri.queryoption.ExpandOption;

import com.sap.olingo.jpa.metadata.core.edm.mapper.api.JPAAssociationPath;
import com.sap.olingo.jpa.metadata.core.edm.mapper.api.JPAEntityType;
import com.sap.olingo.jpa.metadata.core.edm.mapper.api.JPAPath;
import com.sap.olingo.jpa.metadata.core.edm.mapper.api.JPAServiceDocument;
import com.sap.olingo.jpa.metadata.core.edm.mapper.exception.ODataJPAModelException;
import com.sap.olingo.jpa.processor.core.exception.ODataJPAQueryException;
import com.sap.olingo.jpa.processor.core.exception.ODataJPAUtilException;

public final class Util {

  public static final String VALUE_RESOURCE = "$VALUE";

  public static EdmEntitySet determineTargetEntitySet(final List<UriResource> resources) {
    return determineTargetEntitySetAndKeys(resources).getEdmEntitySet();
  }

  public static EdmEntitySetInfo determineTargetEntitySetAndKeys(final List<UriResource> resources) {
    EdmEntitySet targetEdmEntitySet = null;
    List<UriParameter> targteKeyPredicates = new ArrayList<UriParameter>();
    StringBuffer naviPropertyName = new StringBuffer();

    for (final UriResource resourceItem : resources) {
      if (resourceItem.getKind() == UriResourceKind.entitySet) {
        targetEdmEntitySet = ((UriResourceEntitySet) resourceItem).getEntitySet();
        targteKeyPredicates = ((UriResourceEntitySet) resourceItem).getKeyPredicates();
      }
      if (resourceItem.getKind() == UriResourceKind.complexProperty) {
        naviPropertyName.append(((UriResourceComplexProperty) resourceItem).getProperty().getName());
        naviPropertyName.append(JPAPath.PATH_SEPERATOR);
      }
      if (resourceItem.getKind() == UriResourceKind.navigationProperty) {
        naviPropertyName.append(((UriResourceNavigation) resourceItem).getProperty().getName());
        targteKeyPredicates = ((UriResourceNavigation) resourceItem).getKeyPredicates();
        final EdmBindingTarget edmBindingTarget = targetEdmEntitySet.getRelatedBindingTarget(naviPropertyName
            .toString());
        if (edmBindingTarget instanceof EdmEntitySet)
          targetEdmEntitySet = (EdmEntitySet) edmBindingTarget;
        naviPropertyName = new StringBuffer();
      }
    }
    return new EdmEntitySetResult(targetEdmEntitySet, targteKeyPredicates);
  }

  /**
   * Finds an entity type from a navigation property
   */
  public static EdmEntityType determineTargetEntityType(final List<UriResource> resources) {
    EdmEntityType targetEdmEntity = null;

    for (final UriResource resourceItem : resources) {
      if (resourceItem.getKind() == UriResourceKind.navigationProperty) {
        // first try the simple way like in the example
        targetEdmEntity = (EdmEntityType) ((UriResourceNavigation) resourceItem).getType();
      }
    }
    return targetEdmEntity;
  }

//  /**
//   * Finds an entity type with which a navigation may starts. Can be used e.g. for filter:
//   * AdministrativeDivisions?$filter=Parent/CodeID eq 'NUTS1' returns AdministrativeDivision;
//   * AdministrativeDivisions(...)/Parent?$filter=Parent/CodeID eq 'NUTS1' returns "Parent"
//   */
//  public static EdmEntityType determineStartEntityType(final List<UriResource> resources) {
//    EdmEntityType targetEdmEntity = null;
//
//    for (final UriResource resourceItem : resources) {
//      if (resourceItem.getKind() == UriResourceKind.navigationProperty) {
//        // first try the simple way like in the example
//        targetEdmEntity = (EdmEntityType) ((UriResourceNavigation) resourceItem).getType();
//      }
//      if (resourceItem.getKind() == UriResourceKind.entitySet) {
//        // first try the simple way like in the example
//        targetEdmEntity = ((UriResourceEntitySet) resourceItem).getEntityType();
//      }
//    }
//    return targetEdmEntity;
//  }

  /**
   * Used for Serializer
   */
  public static UriResourceProperty determineStartNavigationPath(final List<UriResource> resources) {
    UriResourceProperty property = null;
    if (resources != null) {
      for (int i = resources.size() - 1; i >= 0; i--) {
        final UriResource resourceItem = resources.get(i);
        if (resourceItem instanceof UriResourceEntitySet || resourceItem instanceof UriResourceNavigation)
          break;
        property = (UriResourceProperty) resourceItem;
      }
    }
    return property;
  }

  public static String determineProptertyNavigationPath(final List<UriResource> resources) {
    final StringBuffer pathName = new StringBuffer();
    if (resources != null) {
      for (int i = resources.size() - 1; i >= 0; i--) {
        final UriResource resourceItem = resources.get(i);
        if (resourceItem instanceof UriResourceEntitySet || resourceItem instanceof UriResourceNavigation
            || resourceItem instanceof UriResourceLambdaVariable)
          break;
        if (resourceItem instanceof UriResourceValue) {
          pathName.insert(0, VALUE_RESOURCE);
          pathName.insert(0, JPAPath.PATH_SEPERATOR);
        } else if (resourceItem instanceof UriResourceProperty) {
          final UriResourceProperty property = (UriResourceProperty) resourceItem;
          pathName.insert(0, property.getProperty().getName());
          pathName.insert(0, JPAPath.PATH_SEPERATOR);
        }
      }
      if (pathName.length() > 0)
        pathName.deleteCharAt(0);
    }
    return pathName.toString();
  }

  public static JPAAssociationPath determineAssoziation(final JPAServiceDocument sd, final EdmType naviStart,
      final StringBuffer associationName) throws ODataApplicationException {
    JPAEntityType naviStartType;

    try {
      naviStartType = sd.getEntity(naviStart);
      return naviStartType.getAssociationPath(associationName.toString());
    } catch (ODataJPAModelException e) {
      throw new ODataJPAUtilException(ODataJPAUtilException.MessageKeys.UNKNOWN_NAVI_PROPERTY,
          HttpStatusCode.BAD_REQUEST);
    }
  }

  public static Map<JPAExpandItem, JPAAssociationPath> determineAssoziations(final JPAServiceDocument sd,
      final List<UriResource> startResourceList, final ExpandOption expandOption) throws ODataApplicationException {

    final Map<JPAExpandItem, JPAAssociationPath> pathList =
        new HashMap<JPAExpandItem, JPAAssociationPath>();
    final StringBuffer associationNamePrefix = new StringBuffer();

    UriResource startResourceItem = null;
    if (startResourceList != null && expandOption != null) {
      // Example1 : /Organizations('3')/AdministrativeInformation?$expand=Created/User
      // Example2 : /Organizations('3')/AdministrativeInformation?$expand=*
      // Association name needs AdministrativeInformation as prefix
      for (int i = startResourceList.size() - 1; i >= 0; i--) {
        startResourceItem = startResourceList.get(i);
        if (startResourceItem instanceof UriResourceEntitySet || startResourceItem instanceof UriResourceNavigation) {
          break;
        }
        associationNamePrefix.insert(0, JPAAssociationPath.PATH_SEPERATOR);
        associationNamePrefix.insert(0, ((UriResourceProperty) startResourceItem).getProperty().getName());
      }
      // Example1 : ?$expand=Created/User (Property/NavigationProperty)
      // Example2 : ?$expand=Parent/CodeID (NavigationProperty/Property)
      // Example3 : ?$expand=Parent,Children (NavigationProperty, NavigationProperty)
      // Example4 : ?$expand=*
      // Example5 : ?$expand=*/$ref,Parent
      // Example6 : ?$expand=Parent($levels=2)
      StringBuffer associationName;
      for (final ExpandItem item : expandOption.getExpandItems()) {
        if (item.isStar()) {
          final EdmEntitySet edmEntitySet = determineTargetEntitySet(startResourceList);
          try {
            final JPAEntityType jpaEntityType = sd.getEntity(edmEntitySet.getName());
            final List<JPAAssociationPath> associationPaths = jpaEntityType.getAssociationPathList();
            for (final JPAAssociationPath path : associationPaths) {
              pathList.put(new JPAExpandItemWrapper(item, (JPAEntityType) path.getTargetType()), path);
            }
          } catch (ODataJPAModelException e) {
            throw new ODataJPAUtilException(ODataJPAUtilException.MessageKeys.UNKNOWN_ENTITY_TYPE,
                HttpStatusCode.BAD_REQUEST);
          }
        } else {
          final List<UriResource> targetResourceList = item.getResourcePath().getUriResourceParts();
          associationName = new StringBuffer();
          associationName.append(associationNamePrefix);
          UriResource targetResourceItem = null;
          for (int i = 0; i < targetResourceList.size(); i++) {
            targetResourceItem = targetResourceList.get(i);
            if (targetResourceItem.getKind() != UriResourceKind.navigationProperty) {
              associationName.append(((UriResourceProperty) targetResourceItem).getProperty().getName());
              associationName.append(JPAAssociationPath.PATH_SEPERATOR);
            } else {
              associationName.append(((UriResourceNavigation) targetResourceItem).getProperty().getName());
              break;
            }
          }
          if (item.getLevelsOption() != null)
            pathList.put(new JPAExpandLevelWrapper(sd, expandOption), Util.determineAssoziation(sd,
                ((UriResourcePartTyped) startResourceItem).getType(), associationName));
          else
            pathList.put(new JPAExpandItemWrapper(sd, item), Util.determineAssoziation(sd,
                ((UriResourcePartTyped) startResourceItem).getType(), associationName));
        }
      }
    }
    return pathList;
  }

  public static List<JPANavigationProptertyInfo> determineAssoziations(final JPAServiceDocument sd,
      final List<UriResource> resourceParts) throws ODataApplicationException {

    final List<JPANavigationProptertyInfo> pathList = new ArrayList<JPANavigationProptertyInfo>();

    StringBuffer associationName = null;
    UriResourceNavigation navigation = null;
    if (resourceParts != null && hasNavigation(resourceParts)) {
      for (int i = resourceParts.size() - 1; i >= 0; i--) {
        if (resourceParts.get(i) instanceof UriResourceNavigation && navigation == null) {
          navigation = (UriResourceNavigation) resourceParts.get(i);
          associationName = new StringBuffer();
          associationName.insert(0, navigation.getProperty().getName());
        } else {
          if (resourceParts.get(i) instanceof UriResourceComplexProperty) {
            associationName.insert(0, JPAPath.PATH_SEPERATOR);
            associationName.insert(0, ((UriResourceComplexProperty) resourceParts.get(i)).getProperty().getName());
          }
          if (resourceParts.get(i) instanceof UriResourceNavigation
              || resourceParts.get(i) instanceof UriResourceEntitySet) {
            pathList.add(new JPANavigationProptertyInfo((UriResourcePartTyped) resourceParts.get(i),
                determineAssoziationPath(sd, ((UriResourcePartTyped) resourceParts.get(i)), associationName), null));
            if (resourceParts.get(i) instanceof UriResourceNavigation) {
              navigation = (UriResourceNavigation) resourceParts.get(i);
              associationName = new StringBuffer();
              associationName.insert(0, navigation.getProperty().getName());
            }
          }
        }
      }
    }
    return pathList;
  }

  public static boolean hasNavigation(final List<UriResource> uriResourceParts) {
    if (uriResourceParts != null) {
      for (int i = uriResourceParts.size() - 1; i >= 0; i--) {
        if (uriResourceParts.get(i) instanceof UriResourceNavigation)
          return true;
      }
    }
    return false;
  }

  public static JPAAssociationPath determineAssoziationPath(final JPAServiceDocument sd,
      final UriResourcePartTyped naviStart, final StringBuffer associationName) throws ODataApplicationException {

    JPAEntityType naviStartType;
    try {
      if (naviStart instanceof UriResourceEntitySet)
        naviStartType = sd.getEntity(((UriResourceEntitySet) naviStart).getType());
      else
        naviStartType = sd.getEntity(((UriResourceNavigation) naviStart).getProperty().getType());
      return naviStartType.getAssociationPath(associationName.toString());
    } catch (ODataJPAModelException e) {
      throw new ODataJPAUtilException(ODataJPAUtilException.MessageKeys.UNKNOWN_NAVI_PROPERTY,
          HttpStatusCode.BAD_REQUEST);
    }
  }

  public static List<UriParameter> determineKeyPredicates(final UriResource uriResourceItem)
      throws ODataApplicationException {

    if (uriResourceItem instanceof UriResourceEntitySet)
      return ((UriResourceEntitySet) uriResourceItem).getKeyPredicates();
    else if (uriResourceItem instanceof UriResourceNavigation)
      return ((UriResourceNavigation) uriResourceItem).getKeyPredicates();
    else
      throw new ODataJPAQueryException(ODataJPAQueryException.MessageKeys.NOT_SUPPORTED_RESOURCE_TYPE,
          HttpStatusCode.BAD_REQUEST,
          uriResourceItem.getKind().name());
  }
}

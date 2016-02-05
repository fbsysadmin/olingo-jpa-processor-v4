package org.apache.olingo.jpa.processor.core.query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.Tuple;
import javax.persistence.criteria.AbstractQuery;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.From;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Order;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Selection;
import javax.persistence.criteria.Subquery;

import org.apache.olingo.commons.api.edm.EdmNavigationProperty;
import org.apache.olingo.commons.api.edm.EdmProperty;
import org.apache.olingo.commons.api.edm.EdmType;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.jpa.metadata.core.edm.mapper.api.JPAAssociationAttribute;
import org.apache.olingo.jpa.metadata.core.edm.mapper.api.JPAAttribute;
import org.apache.olingo.jpa.metadata.core.edm.mapper.api.JPADescriptionAttribute;
import org.apache.olingo.jpa.metadata.core.edm.mapper.api.JPAElement;
import org.apache.olingo.jpa.metadata.core.edm.mapper.api.JPAEntityType;
import org.apache.olingo.jpa.metadata.core.edm.mapper.api.JPAOnConditionItem;
import org.apache.olingo.jpa.metadata.core.edm.mapper.api.JPAPath;
import org.apache.olingo.jpa.metadata.core.edm.mapper.api.JPAStructuredType;
import org.apache.olingo.jpa.metadata.core.edm.mapper.exception.ODataJPAModelException;
import org.apache.olingo.jpa.metadata.core.edm.mapper.impl.JPAAssociationPath;
import org.apache.olingo.jpa.metadata.core.edm.mapper.impl.ServicDocument;
import org.apache.olingo.jpa.processor.core.api.Util;
import org.apache.olingo.server.api.ODataApplicationException;
import org.apache.olingo.server.api.uri.UriInfoResource;
import org.apache.olingo.server.api.uri.UriParameter;
import org.apache.olingo.server.api.uri.UriResource;
import org.apache.olingo.server.api.uri.UriResourceComplexProperty;
import org.apache.olingo.server.api.uri.UriResourceCount;
import org.apache.olingo.server.api.uri.UriResourceEntitySet;
import org.apache.olingo.server.api.uri.UriResourceNavigation;
import org.apache.olingo.server.api.uri.UriResourcePartTyped;
import org.apache.olingo.server.api.uri.UriResourcePrimitiveProperty;
import org.apache.olingo.server.api.uri.queryoption.ExpandItem;
import org.apache.olingo.server.api.uri.queryoption.OrderByItem;
import org.apache.olingo.server.api.uri.queryoption.OrderByOption;
import org.apache.olingo.server.api.uri.queryoption.SelectOption;
import org.apache.olingo.server.api.uri.queryoption.expression.Expression;
import org.apache.olingo.server.api.uri.queryoption.expression.Member;

public abstract class JPAExecutableQuery extends JPAAbstractQuery {
  protected final Locale locale;
  protected final UriInfoResource uriResource;
  protected static final String SELECT_ALL = "*";
  protected final CriteriaQuery<Tuple> cq;
  protected final Root<?> root;

  public JPAExecutableQuery(ServicDocument sd, EdmType edmType, EntityManager em,
      Map<String, List<String>> requestHeaders, UriInfoResource uriResource) throws ODataApplicationException {
    super(sd, edmType, em);
    this.locale = determineLocale(requestHeaders);
    this.uriResource = uriResource;
    this.cq = cb.createTupleQuery();
    this.root = cq.from(jpaEntity.getTypeClass());
  }

  protected Locale determineLocale(Map<String, List<String>> headers) {
    // TODO Make this replaceable so the default can be overwritten
    // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html (14.4 accept language header
    // example: Accept-Language: da, en-gb;q=0.8, en;q=0.7)
    List<String> languageHeaders = headers.get("accept-language");
    if (languageHeaders != null) {
      String languageHeader = languageHeaders.get(0);
      if (languageHeader != null) {
        String[] localeList = languageHeader.split(",");
        String locale = localeList[0];
        String[] languCountry = locale.split("-");
        if (languCountry.length == 2)
          return new Locale(languCountry[0], languCountry[1]);
        else if (languCountry.length == 1)
          return new Locale(languCountry[0]);
        else
          return Locale.ENGLISH;
      }
    }
    return Locale.ENGLISH;
  }

  protected List<JPAPath> buildEntityPathList(JPAEntityType jpaEntity) throws ODataApplicationException {

    try {
      return jpaEntity.getPathList();
    } catch (ODataJPAModelException e) {
      throw new ODataApplicationException("Mapping Error", 500, Locale.ENGLISH, e);
    }

  }

  protected List<JPAPath> buildPathList(JPAEntityType jpaEntity, String select) throws ODataApplicationException {
    List<JPAPath> jpaPathList = new ArrayList<JPAPath>();

    String[] selectList;
    try {
      List<? extends JPAAttribute> jpaKeyList = jpaEntity.getKey();
      selectList = select.split(","); // OData separator for $select

      for (String selectItem : selectList) {
        JPAPath selectItemPath = jpaEntity.getPath(selectItem);
        if (((JPAAttribute) selectItemPath.getLeaf()).isComplex()) {
          // Complex Type
          List<JPAPath> c = jpaEntity.searchChildPath(selectItemPath);
          jpaPathList.addAll(c);
        } else
          // Primitive Type
          jpaPathList.add(selectItemPath);
        if (((JPAAttribute) selectItemPath.getLeaf()).isKey()) {
          jpaKeyList.remove((JPAAttribute) selectItemPath.getLeaf());
        }
      }
      for (JPAAttribute key : jpaKeyList) {
        jpaPathList.add(jpaEntity.getPath(key.getExternalName()));
      }
    } catch (ODataJPAModelException e) {
      throw new ODataApplicationException("Mapping Error", 500, Locale.ENGLISH, e);
    }
    return jpaPathList;
  }

  /**
   * The value of the $select query option is a comma-separated list of <b>properties</b>, qualified action names,
   * qualified function names, the <b>star operator (*)</b>, or the star operator prefixed with the namespace or alias
   * of the schema in order to specify all operations defined in the schema. See:
   * <a
   * href=
   * "http://docs.oasis-open.org/odata/odata/v4.0/errata02/os/complete/part1-protocol/odata-v4.0-errata02-os-part1-protocol-complete.html#_Toc406398297"
   * >OData Version 4.0 Part 1 - 11.2.4.1 System Query Option $select</a> <p>
   * See also:
   * <a
   * href=
   * "http://docs.oasis-open.org/odata/odata/v4.0/errata02/os/complete/part2-url-conventions/odata-v4.0-errata02-os-part2-url-conventions-complete.html#_Toc406398163"
   * >OData Version 4.0 Part 2 - 5.1.3 System Query Option $select</a>
   * 
   * @param jpaConversionTargetEntity
   * @param joinTables
   * @param select
   * @return
   * @throws ODataApplicationException
   */
  protected List<Selection<?>> createSelectClause(final HashMap<String, From<?, ?>> joinTables,
      List<JPAPath> jpaPathList) throws ODataApplicationException {
    List<Selection<?>> selections = new ArrayList<Selection<?>>();

    // Build select clause
    for (JPAPath jpaPath : jpaPathList) {
      Path<?> p = root;
      for (JPAElement jpaPathElement : jpaPath.getPath())
        if (jpaPathElement instanceof JPADescriptionAttribute) {
          Join<?, ?> join = (Join<?, ?>) joinTables.get(jpaPathElement.getInternalName());
          p = join.get(((JPADescriptionAttribute) jpaPathElement).getDescriptionAttribute().getInternalName());
        } else
          p = p.get(jpaPathElement.getInternalName());
      p.alias(jpaPath.getAlias());
      selections.add(p);
    }
    return selections;
  }

  @Override
  protected Root<?> getRoot() {
    return root;
  }

  @Override
  public AbstractQuery<?> getQuery() {
    return cq;
  }

  protected List<JPAPath> buildSelectionPathList(final UriInfoResource uriResource) throws ODataApplicationException {
    List<JPAPath> jpaPathList = null;
    // TODO It is also possible to request all actions or functions available for each returned entity:
    // http://host/service/Products?$select=DemoService.*
    // Convert uri select options into a list of jpa attributes
    String selectionText = null;
    List<UriResource> resources = uriResource.getUriResourceParts();

    selectionText = Util.determineProptertyNavigationPath(resources);
    // TODO Combine path selection and $select e.g. Organizations('4')/Address?$select=Country,Region
    if (selectionText == null || selectionText.isEmpty()) {
      SelectOption select = uriResource.getSelectOption();
      if (select != null)
        selectionText = select.getText();
    }

    if (selectionText != null && !selectionText.equals(SELECT_ALL) && !selectionText.isEmpty())
      jpaPathList = buildPathList(jpaEntity, selectionText);
    else
      jpaPathList = buildEntityPathList(jpaEntity);
    // Add Fields that are required for Expand
    Map<ExpandItem, JPAAssociationPath> associationPathList = Util.determineAssoziations(sd, uriResource
        .getUriResourceParts(), uriResource.getExpandOption());
    if (!associationPathList.isEmpty()) {
      Collections.sort(jpaPathList);
      for (ExpandItem item : associationPathList.keySet()) {
        JPAAssociationPath associationPath = associationPathList.get(item);
        try {
          for (JPAOnConditionItem joinItem : associationPath.getJoinColumnsList()) {
            int insertIndex = Collections.binarySearch(jpaPathList, joinItem.getLeftPath());
            if (insertIndex < 0)
              jpaPathList.add(Math.abs(insertIndex), joinItem.getLeftPath());
          }
        } catch (ODataJPAModelException e) {
          throw new ODataApplicationException("Error when trying to process Join Columns",
              HttpStatusCode.INTERNAL_SERVER_ERROR
                  .ordinal(), Locale.ENGLISH, e);
        }
      }
    }
    return jpaPathList;

  }

  protected javax.persistence.criteria.Expression<Boolean> createWhere(HashMap<String, From<?, ?>> joinTables)
      throws ODataApplicationException {

    javax.persistence.criteria.Expression<Boolean> whereCondition = null;

    List<UriResource> resources = uriResource.getUriResourceParts();
    UriResourcePartTyped resourceItem = null;
    // Given key: Organizations('1')
    if (resources != null) {
      for (int i = resources.size() - 1; i >= 0; i--) {
        resourceItem = (UriResourcePartTyped) (resources.get(i));
        if (resourceItem instanceof UriResourceEntitySet || resourceItem instanceof UriResourceNavigation)
          break;
      }
      List<UriParameter> keyPredicates = determineKeyPredicates(resourceItem);
      whereCondition = createWhereByKey(root, whereCondition, keyPredicates);
    }
    // Navigation: AdministrativeDivisions(DivisionCode='BE2',CodeID='1',CodePublisher='NUTS')/Parent
    Subquery<?> subQuery = buildNavigationSubQueries(root);
    if (subQuery != null) {
      if (whereCondition == null)
        whereCondition = cb.exists(subQuery);
      else
        whereCondition = cb.and(whereCondition, cb.exists(subQuery));
    }
    // TODO Given filter:
    return whereCondition;
  }

  Subquery<?> buildNavigationSubQueries(Root<?> root) throws ODataApplicationException {
    Subquery<?> s = null;
    // AbstractQuery<?> parent = cq;

    List<UriResource> resourceParts = uriResource.getUriResourceParts();
    JPAAssociationPath association = null;
    UriResourceNavigation navigation = null;

    // No navigation
    if (!hasNavigation(resourceParts))
      return s;

    /*
     * In case of multiple navigation steps a inner navigation has a dependency to the upper and to the lower query:
     * SELECT * FROM upper WHERE EXISTS(
     * SELECT ... FROM inner WHERE upper = inner
     * AND EXISTS( SELECT ... FROM lower
     * WHERE inner = lower))
     * This is solved by a two steps approach
     */
    StringBuffer associationName = null;
    JPAAbstractQuery parent = this;
    List<JPANavigationQuery> queryList = new ArrayList<JPANavigationQuery>();
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
          association = determineAssoziation(((UriResourcePartTyped) resourceParts.get(i)), // navigation,
              associationName);
          queryList.add(new JPANavigationQuery(sd, resourceParts.get(i), parent, em, association));
          parent = queryList.get(queryList.size() - 1);
          if (resourceParts.get(i) instanceof UriResourceNavigation) {
            navigation = (UriResourceNavigation) resourceParts.get(i);
            associationName = new StringBuffer();
            associationName.insert(0, navigation.getProperty().getName());
          }
        }
      }
    }
    Subquery<?> childQuery = null;
    for (int i = queryList.size() - 1; i >= 0; i--) {
      childQuery = queryList.get(i).getSubQueryExists(childQuery);
    }
    return childQuery;
  }

  boolean hasNavigation(List<UriResource> uriResourceParts) {
    if (uriResourceParts != null) {
      for (int i = uriResourceParts.size() - 1; i >= 0; i--) {
        if (uriResourceParts.get(i) instanceof UriResourceNavigation)
          return true;
      }
    }
    return false;
  }

  protected JPAAssociationPath determineAssoziation(UriResourcePartTyped naviStart,
      StringBuffer associationName) throws ODataApplicationException {
    JPAEntityType naviStartType;
    try {
      if (naviStart instanceof UriResourceEntitySet)
        naviStartType = sd.getEntity(((UriResourceEntitySet) naviStart).getType());
      else
        naviStartType = sd.getEntity(((UriResourceNavigation) naviStart).getProperty().getType());
      return naviStartType.getAssociationPath(associationName.toString());
    } catch (ODataJPAModelException e) {
      // TODO Update error handling
      throw new ODataApplicationException("Unknown navigation property", HttpStatusCode.INTERNAL_SERVER_ERROR
          .ordinal(), Locale.ENGLISH, e);
    }
  }

  protected abstract List<JPANavigationQuery> asSubQueries(JPAAbstractQuery superordinateQuery,
      JPAAssociationPath assoziation) throws ODataApplicationException;

  protected abstract <T extends Object> Subquery<T> asSubQuery(JPAAbstractQuery parent, JPAAssociationPath assoziation)
      throws ODataApplicationException;

  protected List<JPAPath> extractDescriptionAttributes(final List<JPAPath> jpaPathList)
      throws ODataApplicationException {
    List<JPAPath> result = new ArrayList<JPAPath>();
    // List<JPAPath> jpaPathList = buildSelectionPathList(select);
    for (JPAPath p : jpaPathList)
      if (p.getLeaf() instanceof JPADescriptionAttribute)
        result.add(p);
    return result;
  }
  // private javax.persistence.criteria.Expression<Boolean> createOnFromAssoziation(Root<?> left, Join<?, ?> right,
  // JPAAssociationAttribute association) throws ODataApplicationException {
  // javax.persistence.criteria.Expression<Boolean> onCondition = null;
  // try {
  // for (JPAOnConditionItem onItem : association.getJoinColumns()) {
  // javax.persistence.criteria.Expression<Boolean> equalCondition =
  // cb.equal(left.get(onItem.getLeftAttribute().getInternalName()), right.get(onItem
  // .getRightAttribute().getInternalName()));
  // if (onCondition == null)
  // onCondition = equalCondition;
  // else
  // onCondition = cb.and(onCondition, equalCondition);
  // }
  // } catch (ODataJPAModelException e) {
  // throw new ODataApplicationException("Mapping Error", 500, Locale.ENGLISH, e);
  // }
  // return onCondition;
  // }

  /**
   * 
   * @param orderByTarget
   * @param descriptionFields List of the requested fields that of type description
   * @param queryRoot
   * @return
   * @throws ODataApplicationException
   */
  protected HashMap<String, From<?, ?>> createFromClause(List<JPAAssociationAttribute> orderByTarget,
      List<JPAPath> descriptionFields) throws ODataApplicationException {
    HashMap<String, From<?, ?>> joinTables = new HashMap<String, From<?, ?>>();
    // 1. Create root
    joinTables.put(jpaEntity.getInternalName(), root);
    // 2. OrderBy navigation property
    for (JPAAssociationAttribute orderBy : orderByTarget) {
      Join<?, ?> join = root.join(orderBy.getInternalName(), JoinType.LEFT);
      // Take on condition from JPA metadata; no explicit on
      joinTables.put(orderBy.getInternalName(), join);
    }
    for (JPAPath descriptionFieldPath : descriptionFields) {
      //
      JPADescriptionAttribute desciptionField = ((JPADescriptionAttribute) descriptionFieldPath.getLeaf());
      List<JPAElement> pathList = descriptionFieldPath.getPath();
      Join<?, ?> join = null;
      JoinType jt;
      for (int i = 0; i < pathList.size(); i++) {
        if (i == pathList.size() - 1)
          jt = JoinType.LEFT;
        else
          jt = JoinType.INNER;
        if (i == 0)
          join = root.join(pathList.get(i).getInternalName(), jt);
        else if (i < pathList.size()) {
          join = join.join(pathList.get(i).getInternalName(), jt);
        }
      }
      if (desciptionField.isLocationJoin())
        join.on(cb.equal(join.get(desciptionField.getInternalName()), locale.toString()));
      else
        join.on(cb.equal(join.get(desciptionField.getLocaleFieldName()), locale.getLanguage()));
      joinTables.put(desciptionField.getInternalName(), join);
    }
    return joinTables;
  }

  /**
   * If asc or desc is not specified, the service MUST order by the specified property in ascending order.
   * See:
   * <a
   * href=
   * "http://docs.oasis-open.org/odata/odata/v4.0/errata02/os/complete/part1-protocol/odata-v4.0-errata02-os-part1-protocol-complete.html#_Toc406398305"
   * >OData Version 4.0 Part 1 - 11.2.5.2 System Query Option $orderby</a> <p>
   * 
   * @throws ODataJPAModelException
   * 
   */
  protected List<Order> createOrderList(final HashMap<String, From<?, ?>> joinTables, final OrderByOption orderByOption)
      throws ODataApplicationException {
    // .../Organizations?$orderby=Address/Country --> one item, two resourcePaths
    // [...ComplexProperty,...PrimitiveProperty]
    // .../Organizations?$orderby=Roles/$count --> one item, two resourcePaths [...NavigationProperty,...Count]
    // .../Organizations?$orderby=Roles/$count desc,Address/Country asc -->two items
    //
    // SQL example to order by number of entities of the
    // SELECT t0."BusinessPartnerID" ,COUNT(t1."BusinessPartnerID")
    // FROM {oj "OLINGO"."org.apache.olingo.jpa::BusinessPartner" t0
    // LEFT OUTER JOIN "OLINGO"."org.apache.olingo.jpa::BusinessPartnerRole" t1
    // ON (t1."BusinessPartnerID" = t0."BusinessPartnerID")}
    // WHERE (t0."Type" = ?)
    // GROUP BY t0."BusinessPartnerID"
    // ORDER BY COUNT(t1."BusinessPartnerID") DESC

    // TODO Functions and orderBy: Part 1 - 11.5.3.1 Invoking a Function
    List<Order> orders = new ArrayList<Order>();
    if (orderByOption != null) {
      for (OrderByItem orderByItem : orderByOption.getOrders()) {
        Expression expression = orderByItem.getExpression();
        if (expression instanceof Member) {
          UriInfoResource resourcePath = ((Member) expression).getResourcePath();
          JPAStructuredType type = jpaEntity;
          Path<?> p = joinTables.get(jpaEntity.getInternalName());
          for (UriResource uriResource : resourcePath.getUriResourceParts()) {
            if (uriResource instanceof UriResourcePrimitiveProperty) {
              EdmProperty edmProperty = ((UriResourcePrimitiveProperty) uriResource).getProperty();
              try {
                JPAAttribute attribute = (JPAAttribute) type.getPath(edmProperty.getName()).getLeaf();
                p = p.get(attribute.getInternalName());
              } catch (ODataJPAModelException e) {
                throw new ODataApplicationException("Property not found", HttpStatusCode.BAD_REQUEST.ordinal(),
                    Locale.ENGLISH, e);
              }
              if (orderByItem.isDescending())
                orders.add(cb.desc(p));
              else
                orders.add(cb.asc(p));
            } else if (uriResource instanceof UriResourceComplexProperty) {
              EdmProperty edmProperty = ((UriResourceComplexProperty) uriResource).getProperty();
              try {
                JPAAttribute attribute = (JPAAttribute) type.getPath(edmProperty.getName()).getLeaf();
                p = p.get(attribute.getInternalName());
                type = attribute.getStructuredType();
              } catch (ODataJPAModelException e) {
                throw new ODataApplicationException("Property not found", HttpStatusCode.BAD_REQUEST.ordinal(),
                    Locale.ENGLISH, e);
              }
            } else if (uriResource instanceof UriResourceNavigation) {
              EdmNavigationProperty edmNaviProperty = ((UriResourceNavigation) uriResource).getProperty();
              From<?, ?> join;
              try {
                join = joinTables.get(jpaEntity.getAssociationPath(edmNaviProperty.getName()).getLeaf()
                    .getInternalName());
              } catch (ODataJPAModelException e) {
                throw new ODataApplicationException("Property not found", HttpStatusCode.BAD_REQUEST.ordinal(),
                    Locale.ENGLISH, e);
              }
              if (orderByItem.isDescending())
                orders.add(cb.desc(cb.count(join)));

              else
                orders.add(cb.asc(cb.count(join)));
            } else if (uriResource instanceof UriResourceCount) {}
          }
        }
      }
    }
    return orders;
  }
}

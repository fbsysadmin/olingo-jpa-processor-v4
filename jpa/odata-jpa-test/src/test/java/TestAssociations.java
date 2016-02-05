import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.Tuple;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;

import org.apache.olingo.jpa.processor.core.testmodel.AdministrativeDivision;
import org.apache.olingo.jpa.processor.core.testmodel.BusinessPartner;
import org.apache.olingo.jpa.processor.core.testmodel.BusinessPartnerRole;
import org.apache.olingo.jpa.processor.core.testmodel.Country;
import org.apache.olingo.jpa.processor.core.testmodel.DataSourceHelper;
import org.apache.olingo.jpa.processor.core.testmodel.Region;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class TestAssociations {
  protected static final String PUNIT_NAME = "org.apache.olingo.jpa";
  private static final String ENTITY_MANAGER_DATA_SOURCE = "javax.persistence.nonJtaDataSource";
  private static EntityManagerFactory emf;
  private EntityManager em;
  private CriteriaBuilder cb;

  @BeforeClass
  public static void setupClass() {
    Map<String, Object> properties = new HashMap<String, Object>();
    properties.put(ENTITY_MANAGER_DATA_SOURCE, DataSourceHelper.createDataSource(
        DataSourceHelper.DB_H2));
    emf = Persistence.createEntityManagerFactory(PUNIT_NAME, properties);
  }

  @Before
  public void setup() {
    em = emf.createEntityManager();
    cb = em.getCriteriaBuilder();
  }

  @Test
  public void getBuPaRoles() {
    CriteriaQuery<Tuple> cq = cb.createTupleQuery();
    Root<BusinessPartner> root = cq.from(BusinessPartner.class);

    cq.multiselect(root.get("roles").alias("roles"));
    TypedQuery<Tuple> tq = em.createQuery(cq);
    List<Tuple> result = tq.getResultList();
    BusinessPartnerRole role = (BusinessPartnerRole) result.get(0).get("roles");
    assertNotNull(role);
  }

  @Test
  public void getBuPaLocation() {
    CriteriaQuery<Tuple> cq = cb.createTupleQuery();
    Root<BusinessPartner> root = cq.from(BusinessPartner.class);

    cq.multiselect(root.get("locationName").alias("L"));
    TypedQuery<Tuple> tq = em.createQuery(cq);
    List<Tuple> result = tq.getResultList();
    Country act = (Country) result.get(0).get("L");
    assertNotNull(act);
  }

  @Test
  public void getRoleBuPa() {
    CriteriaQuery<Tuple> cq = cb.createTupleQuery();
    Root<BusinessPartnerRole> root = cq.from(BusinessPartnerRole.class);

    cq.multiselect(root.get("businessPartner").alias("BuPa"));
    TypedQuery<Tuple> tq = em.createQuery(cq);
    List<Tuple> result = tq.getResultList();
    BusinessPartner bp = (BusinessPartner) result.get(0).get("BuPa");
    assertNotNull(bp);
  }

  @Test
  public void getBuPaCountryName() {
    CriteriaQuery<Tuple> cq = cb.createTupleQuery();
    Root<BusinessPartner> root = cq.from(BusinessPartner.class);

    cq.multiselect(root.get("address").get("countryName").alias("CN"));
    TypedQuery<Tuple> tq = em.createQuery(cq);
    List<Tuple> result = tq.getResultList();
    Country region = (Country) result.get(0).get("CN");
    assertNotNull(region);
  }

  @Test
  public void getBuPaRegionName() {
    CriteriaQuery<Tuple> cq = cb.createTupleQuery();
    Root<BusinessPartner> root = cq.from(BusinessPartner.class);

    cq.multiselect(root.get("address").get("regionName").alias("RN"));
    TypedQuery<Tuple> tq = em.createQuery(cq);
    List<Tuple> result = tq.getResultList();
    Region region = (Region) result.get(0).get("RN");
    assertNotNull(region);
  }

  @Test
  public void getAdministrativeDivisionParent() {
    CriteriaQuery<Tuple> cq = cb.createTupleQuery();
    Root<AdministrativeDivision> root = cq.from(AdministrativeDivision.class);

    cq.multiselect(root.get("parent").alias("P"));
    TypedQuery<Tuple> tq = em.createQuery(cq);
    List<Tuple> result = tq.getResultList();
    AdministrativeDivision act = (AdministrativeDivision) result.get(0).get("P");
    assertNotNull(act);
  }

  @Test
  public void getAdministrativeDivisionOneParent() {
    CriteriaQuery<Tuple> cq = cb.createTupleQuery();
    Root<AdministrativeDivision> root = cq.from(AdministrativeDivision.class);
    root.alias("Source");
    cq.multiselect(root.get("parent").alias("P"));
    // cq.select((Selection<? extends Tuple>) root);
    cq.where(cb.and(
        cb.equal(root.get("codePublisher"), "NUTS"),
        cb.and(
            cb.equal(root.get("codeID"), "3"),
            cb.equal(root.get("divisionCode"), "BE251"))));
    TypedQuery<Tuple> tq = em.createQuery(cq);
    List<Tuple> result = tq.getResultList();
    AdministrativeDivision act = (AdministrativeDivision) result.get(0).get("P");
    assertNotNull(act);
    assertEquals("2", act.getCodeID());
    assertEquals("BE25", act.getDivisionCode());
  }

  @Test
  public void getAdministrativeDivisionChildrenOfOneParent() {
    CriteriaQuery<Tuple> cq = cb.createTupleQuery();
    Root<AdministrativeDivision> root = cq.from(AdministrativeDivision.class);
    root.alias("Source");
    cq.multiselect(root.get("children").alias("C"));
    cq.where(cb.and(
        cb.equal(root.get("codePublisher"), "NUTS"),
        cb.and(
            cb.equal(root.get("codeID"), "2"),
            cb.equal(root.get("divisionCode"), "BE25"))));
    cq.orderBy(cb.desc(root.get("divisionCode")));
    TypedQuery<Tuple> tq = em.createQuery(cq);
    List<Tuple> result = tq.getResultList();
    AdministrativeDivision act = (AdministrativeDivision) result.get(0).get("C");
    assertNotNull(act);
    assertEquals(8, result.size());
    assertEquals("3", act.getCodeID());
    assertEquals("BE251", act.getDivisionCode());
  }
}

package org.tests.genkey;

import io.ebean.DB;
import io.ebean.Transaction;
import org.junit.jupiter.api.Test;
import org.tests.model.basic.TOne;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestGeneratedKeys {

  @Test
  public void testJdbcBatchInsert() {

    TOne c = new TOne();
    c.setName("Banana");
    c.setDescription("Test Gen Key");

    TOne c1 = new TOne();
    c1.setName("Two");
    c1.setDescription("Test Gen Key Two");

    try (Transaction txn = DB.beginTransaction()) {
      DB.save(c);
      DB.save(c1);
      txn.commit();
    }
    Integer id = c.getId();
    assertNotNull(id);

    Integer id1 = c1.getId();
    assertNotNull(id1);
  }

}

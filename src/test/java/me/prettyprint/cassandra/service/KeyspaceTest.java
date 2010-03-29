package me.prettyprint.cassandra.service;

import static me.prettyprint.cassandra.utils.StringUtils.bytes;
import static me.prettyprint.cassandra.utils.StringUtils.string;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import me.prettyprint.cassandra.service.CassandraClient.FailoverPolicy;
import me.prettyprint.cassandra.testutils.EmbeddedServerHelper;

import org.apache.cassandra.service.Cassandra;
import org.apache.cassandra.service.Column;
import org.apache.cassandra.service.ColumnParent;
import org.apache.cassandra.service.ColumnPath;
import org.apache.cassandra.service.InvalidRequestException;
import org.apache.cassandra.service.NotFoundException;
import org.apache.cassandra.service.SlicePredicate;
import org.apache.cassandra.service.SliceRange;
import org.apache.cassandra.service.SuperColumn;
import org.apache.cassandra.service.TimedOutException;
import org.apache.cassandra.service.UnavailableException;
import org.apache.thrift.TException;
import org.apache.thrift.transport.TTransportException;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * For the tests we assume the following structure:
 *
 * &lt;Keyspaces&gt; &lt;Keyspace Name="Keyspace1"&gt; &lt;ColumnFamily
 * CompareWith="BytesType" Name="Standard1" FlushPeriodInMinutes="60"/&gt;
 * &lt;ColumnFamily CompareWith="UTF8Type" Name="Standard2"/&gt;
 * &lt;ColumnFamily CompareWith="TimeUUIDType" Name="StandardByUUID1"/&gt;
 * &lt;ColumnFamily ColumnType="Super" CompareWith="UTF8Type"
 * CompareSubcolumnsWith="UTF8Type" Name="Super1"/&gt;
 *
 * @author Ran Tavory (rantav@gmail.com)
 *
 */
public class KeyspaceTest {

  private static EmbeddedServerHelper embedded;

  private CassandraClient client;
  private Keyspace keyspace;
  private CassandraClientPool pools;
  private CassandraClientMonitor monitor;

  /**
   * Set embedded cassandra up and spawn it in a new thread.
   *
   * @throws TTransportException
   * @throws IOException
   * @throws InterruptedException
   */
  @BeforeClass
  public static void setup() throws TTransportException, IOException, InterruptedException {
    embedded = new EmbeddedServerHelper();
    embedded.setup();
  }

  @AfterClass
  public static void teardown() throws IOException {
    embedded.teardown();
  }

  @Before
  public void setupCase() throws TTransportException, TException, IllegalArgumentException,
      NotFoundException, UnknownHostException {
    pools = mock(CassandraClientPool.class);
    monitor = mock(CassandraClientMonitor.class);
    client = new CassandraClientFactory(pools, new CassandraHost("localhost", 9170), monitor).create();
    keyspace = client.getKeyspace("Keyspace1", 1, CassandraClient.DEFAULT_FAILOVER_POLICY);
  }

  @Test
  public void testInsertAndGetAndRemove() throws IllegalArgumentException, NoSuchElementException,
      IllegalStateException, NotFoundException, Exception {

    // insert value
    ColumnPath cp = new ColumnPath("Standard1", null, bytes("testInsertAndGetAndRemove"));
    for (int i = 0; i < 100; i++) {
      keyspace.insert("testInsertAndGetAndRemove_" + i, cp,
          bytes("testInsertAndGetAndRemove_value_" + i));
    }

    // get value
    for (int i = 0; i < 100; i++) {
      Column col = keyspace.getColumn("testInsertAndGetAndRemove_" + i, cp);
      assertNotNull(col);
      String value = string(col.getValue());
      assertEquals("testInsertAndGetAndRemove_value_" + i, value);
    }

    // remove value
    for (int i = 0; i < 100; i++) {
      keyspace.remove("testInsertAndGetAndRemove_" + i, cp);
    }

    // get already removed value
    for (int i = 0; i < 100; i++) {
      try {
        keyspace.getColumn("testInsertAndGetAndRemove_" + i, cp);
        fail("the value should already being deleted");
      } catch (NotFoundException e) {
        // good
      }
    }
  }

  @Test
  public void testValideColumnPath() throws UnavailableException, TException, TimedOutException {
    // Try to insert invalid columns
    // insert value
    ColumnPath cp = new ColumnPath("Standard1", null, bytes("testValideColumnPath"));
    try {
      keyspace.insert("testValideColumnPath", cp, bytes("testValideColumnPath_value"));
      keyspace.remove("testValideColumnPath", cp);
    } catch (InvalidRequestException e) {
      fail("Should not have thrown an error for Standard1");
    }

    cp = new ColumnPath("CFdoesNotExist", null, bytes("testInsertAndGetAndRemove"));
    try {
      keyspace.insert("testValideColumnPath", cp, bytes("testValideColumnPath_value"));
      fail("Should have failed with CFdoesNotExist");
    } catch (InvalidRequestException e) {
      // ok
    }

    cp = new ColumnPath("Standard1", bytes("testInsertAndGetAndRemove"), null);
    try {
      keyspace.insert("testValideColumnPath", cp, bytes("testValideColumnPath_value"));
      fail("Should have failed with supercolumn");
    } catch (InvalidRequestException e) {
      // ok
    }
  }

  @Test
  public void testBatchInsertColumn() throws IllegalArgumentException, NoSuchElementException,
      IllegalStateException, NotFoundException, TException, Exception {
    for (int i = 0; i < 10; i++) {
      HashMap<String, List<Column>> cfmap = new HashMap<String, List<Column>>(10);
      ArrayList<Column> list = new ArrayList<Column>(10);
      for (int j = 0; j < 10; j++) {
        Column col = new Column(bytes("testBatchInsertColumn_" + j),
            bytes("testBatchInsertColumn_value_" + j), System.currentTimeMillis());
        list.add(col);
      }
      cfmap.put("Standard1", list);

      keyspace.batchInsert("testBatchInsertColumn_" + i, cfmap, null);
    }

    // get value
    for (int i = 0; i < 10; i++) {
      for (int j = 0; j < 10; j++) {
        ColumnPath cp = new ColumnPath("Standard1", null, bytes("testBatchInsertColumn_" + j));
        Column col = keyspace.getColumn("testBatchInsertColumn_" + i, cp);
        assertNotNull(col);
        String value = string(col.getValue());
        assertEquals("testBatchInsertColumn_value_" + j, value);

      }
    }

    // remove value
    for (int i = 0; i < 10; i++) {
      for (int j = 0; j < 10; j++) {
        ColumnPath cp = new ColumnPath("Standard1", null, bytes("testBatchInsertColumn_" + j));
        keyspace.remove("testBatchInsertColumn_" + i, cp);
      }
    }
  }

  @Test
  public void testGetClient() {
    assertEquals(client, keyspace.getClient());
  }

  @Test
  public void testGetSuperColumn() throws IllegalArgumentException, NoSuchElementException,
      IllegalStateException, NotFoundException, TException, Exception {
    HashMap<String, List<SuperColumn>> cfmap = new HashMap<String, List<SuperColumn>>(10);
    ArrayList<Column> list = new ArrayList<Column>(100);
    for (int j = 0; j < 10; j++) {
      Column col = new Column(bytes("testGetSuperColumn_" + j), bytes("testGetSuperColumn_value_"
          + j), System.currentTimeMillis());
      list.add(col);
    }
    ArrayList<SuperColumn> superlist = new ArrayList<SuperColumn>(1);
    SuperColumn sc = new SuperColumn(bytes("SuperColumn_1"), list);
    superlist.add(sc);
    cfmap.put("Super1", superlist);
    keyspace.batchInsert("testGetSuperColumn_1", null, cfmap);

    ColumnPath cp = new ColumnPath("Super1", bytes("SuperColumn_1"), null);
    try {
      SuperColumn superc = keyspace.getSuperColumn("testGetSuperColumn_1", cp);
      assertNotNull(superc);
      assertNotNull(superc.getColumns());
      assertEquals(10, superc.getColumns().size());
    } finally {
      keyspace.remove("testGetSuperColumn_1", cp);
    }
  }

  @Test
  public void testGetSlice() throws IllegalArgumentException, NoSuchElementException,
      IllegalStateException, NotFoundException, TException, Exception {
    // insert value
    ArrayList<String> columnnames = new ArrayList<String>(100);
    for (int i = 0; i < 100; i++) {
      ColumnPath cp = new ColumnPath("Standard2", null, bytes("testGetSlice_" + i));
      keyspace.insert("testGetSlice", cp, bytes("testGetSlice_Value_" + i));
      columnnames.add("testGetSlice_" + i);
    }

    // get value
    ColumnParent clp = new ColumnParent("Standard2", null);
    SliceRange sr = new SliceRange(new byte[0], new byte[0], false, 150);
    SlicePredicate sp = new SlicePredicate(null, sr);
    List<Column> cols = keyspace.getSlice("testGetSlice", clp, sp);

    assertNotNull(cols);
    assertEquals(100, cols.size());

    Collections.sort(columnnames);
    ArrayList<String> gotlist = new ArrayList<String>(100);
    for (int i = 0; i < 100; i++) {
      gotlist.add(string(cols.get(i).getName()));
    }
    assertEquals(columnnames, gotlist);

    ColumnPath cp = new ColumnPath("Standard2", null, null);
    keyspace.remove("testGetSlice_", cp);
  }

  @Test
  public void testGetSuperSlice() throws IllegalArgumentException, NoSuchElementException,
      IllegalStateException, NotFoundException, TException, Exception {
    // insert value
    for (int i = 0; i < 100; i++) {
      ColumnPath cp = new ColumnPath("Super1", bytes("SuperColumn_1"), bytes("testGetSuperSlice_"
          + i));

      ColumnPath cp2 = new ColumnPath("Super1", bytes("SuperColumn_2"), bytes("testGetSuperSlice_"
          + i));

      keyspace.insert("testGetSuperSlice", cp, bytes("testGetSuperSlice_Value_" + i));
      keyspace.insert("testGetSuperSlice", cp2, bytes("testGetSuperSlice_Value_" + i));
    }

    // get value
    ColumnParent clp = new ColumnParent("Super1", null);
    SliceRange sr = new SliceRange(new byte[0], new byte[0], false, 150);
    SlicePredicate sp = new SlicePredicate(null, sr);
    List<SuperColumn> cols = keyspace.getSuperSlice("testGetSuperSlice", clp, sp);

    assertNotNull(cols);
    assertEquals(2, cols.size());

    ColumnPath cp = new ColumnPath("Super1", null, null);
    keyspace.remove("testGetSuperSlice", cp);
  }

  @Test
  public void testMultigetColumn() throws IllegalArgumentException, NoSuchElementException,
      IllegalStateException, NotFoundException, TException, Exception {
    // insert value
    ColumnPath cp = new ColumnPath("Standard1", null, bytes("testMultigetColumn"));
    ArrayList<String> keys = new ArrayList<String>(100);
    for (int i = 0; i < 100; i++) {
      keyspace.insert("testMultigetColumn_" + i, cp, bytes("testMultigetColumn_value_" + i));
      keys.add("testMultigetColumn_" + i);
    }

    // get value
    Map<String, Column> ms = keyspace.multigetColumn(keys, cp);
    for (int i = 0; i < 100; i++) {
      Column cl = ms.get(keys.get(i));
      assertNotNull(cl);
      assertEquals("testMultigetColumn_value_" + i, string(cl.getValue()));
    }

    // remove value
    for (int i = 0; i < 100; i++) {
      keyspace.remove("testMultigetColumn_" + i, cp);
    }
  }

  @Test
  public void testMultigetSuperColumn() throws IllegalArgumentException, NoSuchElementException,
      IllegalStateException, NotFoundException, TException, Exception {
    HashMap<String, List<SuperColumn>> cfmap = new HashMap<String, List<SuperColumn>>(10);
    ArrayList<Column> list = new ArrayList<Column>(100);
    for (int j = 0; j < 10; j++) {
      Column col = new Column(bytes("testMultigetSuperColumn_" + j),
          bytes("testMultigetSuperColumn_value_" + j), System.currentTimeMillis());
      list.add(col);
    }
    ArrayList<SuperColumn> superlist = new ArrayList<SuperColumn>(1);
    SuperColumn sc = new SuperColumn(bytes("SuperColumn_1"), list);
    superlist.add(sc);
    cfmap.put("Super1", superlist);
    keyspace.batchInsert("testMultigetSuperColumn_1", null, cfmap);

    ColumnPath cp = new ColumnPath("Super1", bytes("SuperColumn_1"), null);
    try {
      List<String> keys = new ArrayList<String>();
      keys.add("testMultigetSuperColumn_1");
      Map<String, SuperColumn> superc = keyspace.multigetSuperColumn(keys, cp);
      assertNotNull(superc);
      assertEquals(1, superc.size());
      assertEquals(10, superc.get("testMultigetSuperColumn_1").columns.size());
    } finally {
      keyspace.remove("testMultigetSuperColumn_1", cp);
    }
  }

  @Test
  public void testMultigetSlice() throws IllegalArgumentException, NoSuchElementException,
      IllegalStateException, NotFoundException, TException, Exception {
    // insert value
    ColumnPath cp = new ColumnPath("Standard1", null, bytes("testMultigetSlice"));
    ArrayList<String> keys = new ArrayList<String>(100);
    for (int i = 0; i < 100; i++) {
      keyspace.insert("testMultigetSlice_" + i, cp, bytes("testMultigetSlice_value_" + i));
      keys.add("testMultigetSlice_" + i);
    }
    // get value
    ColumnParent clp = new ColumnParent("Standard1", null);
    SliceRange sr = new SliceRange(new byte[0], new byte[0], false, 150);
    SlicePredicate sp = new SlicePredicate(null, sr);
    Map<String, List<Column>> ms = keyspace.multigetSlice(keys, clp, sp);
    for (int i = 0; i < 100; i++) {
      List<Column> cl = ms.get(keys.get(i));
      assertNotNull(cl);
      assertEquals(1, cl.size());
      assertTrue(string(cl.get(0).getValue()).startsWith("testMultigetSlice_"));
    }

    // remove value
    for (int i = 0; i < 100; i++) {
      keyspace.remove("testMultigetSlice_" + i, cp);
    }
  }

  @Test
  public void testMultigetSlice_1() throws IllegalArgumentException, NoSuchElementException,
      IllegalStateException, NotFoundException, TException, Exception {
    HashMap<String, List<SuperColumn>> cfmap = new HashMap<String, List<SuperColumn>>(10);
    ArrayList<Column> list = new ArrayList<Column>(100);
    for (int j = 0; j < 10; j++) {
      Column col = new Column(bytes("testMultigetSuperSlice_" + j),
          bytes("testMultigetSuperSlice_value_" + j), System.currentTimeMillis());
      list.add(col);
    }
    ArrayList<SuperColumn> superlist = new ArrayList<SuperColumn>(1);
    SuperColumn sc = new SuperColumn(bytes("SuperColumn_1"), list);
    SuperColumn sc2 = new SuperColumn(bytes("SuperColumn_2"), list);
    superlist.add(sc);
    superlist.add(sc2);
    cfmap.put("Super1", superlist);
    keyspace.batchInsert("testMultigetSuperSlice_1", null, cfmap);
    keyspace.batchInsert("testMultigetSuperSlice_2", null, cfmap);
    keyspace.batchInsert("testMultigetSuperSlice_3", null, cfmap);

    try {
      List<String> keys = new ArrayList<String>();
      keys.add("testMultigetSuperSlice_1");
      keys.add("testMultigetSuperSlice_2");
      keys.add("testMultigetSuperSlice_3");

      ColumnParent clp = new ColumnParent("Super1", bytes("SuperColumn_1"));
      SliceRange sr = new SliceRange(new byte[0], new byte[0], false, 150);
      SlicePredicate sp = new SlicePredicate(null, sr);
      Map<String, List<Column>> superc = keyspace.multigetSlice(keys, clp, sp);

      assertNotNull(superc);
      assertEquals(3, superc.size());
      List<Column> scls = superc.get("testMultigetSuperSlice_1");
      assertNotNull(scls);
      assertEquals(10, scls.size());

    } finally {
      // insert value
      ColumnPath cp = new ColumnPath("Super1", null, null);
      keyspace.remove("testMultigetSuperSlice_1", cp);
      keyspace.remove("testMultigetSuperSlice_2", cp);
      keyspace.remove("testMultigetSuperSlice_3", cp);
    }
  }

  @Test
  public void testMultigetSuperSlice() throws IllegalArgumentException, NoSuchElementException,
      IllegalStateException, NotFoundException, TException, Exception {
    HashMap<String, List<SuperColumn>> cfmap = new HashMap<String, List<SuperColumn>>(10);
    ArrayList<Column> list = new ArrayList<Column>(100);
    for (int j = 0; j < 10; j++) {
      Column col = new Column(bytes("testMultigetSuperSlice_" + j),
          bytes("testMultigetSuperSlice_value_" + j), System.currentTimeMillis());
      list.add(col);
    }
    ArrayList<SuperColumn> superlist = new ArrayList<SuperColumn>(1);
    SuperColumn sc = new SuperColumn(bytes("SuperColumn_1"), list);
    SuperColumn sc2 = new SuperColumn(bytes("SuperColumn_2"), list);
    superlist.add(sc);
    superlist.add(sc2);
    cfmap.put("Super1", superlist);
    keyspace.batchInsert("testMultigetSuperSlice_1", null, cfmap);
    keyspace.batchInsert("testMultigetSuperSlice_2", null, cfmap);
    keyspace.batchInsert("testMultigetSuperSlice_3", null, cfmap);

    try {
      List<String> keys = new ArrayList<String>();
      keys.add("testMultigetSuperSlice_1");
      keys.add("testMultigetSuperSlice_2");
      keys.add("testMultigetSuperSlice_3");

      ColumnParent clp = new ColumnParent("Super1", null);
      SliceRange sr = new SliceRange(new byte[0], new byte[0], false, 150);
      SlicePredicate sp = new SlicePredicate(null, sr);
      Map<String, List<SuperColumn>> superc = keyspace.multigetSuperSlice(keys, clp, sp); // throw

      assertNotNull(superc);
      assertEquals(3, superc.size());
      List<SuperColumn> scls = superc.get("testMultigetSuperSlice_1");
      assertNotNull(scls);
      assertEquals(2, scls.size());
      assertNotNull(scls.get(0).getColumns());
      assertEquals(10, scls.get(0).getColumns().size());
      assertNotNull(scls.get(0).getColumns().get(0).value);
    } finally {
      // insert value
      ColumnPath cp = new ColumnPath("Super1", null, null);
      keyspace.remove("testMultigetSuperSlice_1", cp);
    }
  }

  @Test
  public void testMultigetSuperSlice_1() throws IllegalArgumentException, NoSuchElementException,
      IllegalStateException, NotFoundException, TException, Exception {
    HashMap<String, List<SuperColumn>> cfmap = new HashMap<String, List<SuperColumn>>(10);
    ArrayList<Column> list = new ArrayList<Column>(100);
    for (int j = 0; j < 10; j++) {
      Column col = new Column(bytes("testMultigetSuperSlice_" + j),
          bytes("testMultigetSuperSlice_value_" + j), System.currentTimeMillis());
      list.add(col);
    }
    ArrayList<SuperColumn> superlist = new ArrayList<SuperColumn>(1);
    SuperColumn sc = new SuperColumn(bytes("SuperColumn_1"), list);
    SuperColumn sc2 = new SuperColumn(bytes("SuperColumn_2"), list);
    superlist.add(sc);
    superlist.add(sc2);
    cfmap.put("Super1", superlist);
    keyspace.batchInsert("testMultigetSuperSlice_1", null, cfmap);
    keyspace.batchInsert("testMultigetSuperSlice_2", null, cfmap);
    keyspace.batchInsert("testMultigetSuperSlice_3", null, cfmap);

    try {
      List<String> keys = new ArrayList<String>();
      keys.add("testMultigetSuperSlice_1");
      keys.add("testMultigetSuperSlice_2");
      keys.add("testMultigetSuperSlice_3");

      ColumnParent clp = new ColumnParent("Super1", bytes("SuperColumn_1"));
      SliceRange sr = new SliceRange(new byte[0], new byte[0], false, 150);
      SlicePredicate sp = new SlicePredicate(null, sr);
      Map<String, List<SuperColumn>> superc = keyspace.multigetSuperSlice(keys, clp, sp); // throw

      assertNotNull(superc);
      assertEquals(3, superc.size());
      List<SuperColumn> scls = superc.get("testMultigetSuperSlice_1");
      assertNotNull(scls);
      assertEquals(1, scls.size());
      assertNotNull(scls.get(0).getColumns());
      assertEquals(10, scls.get(0).getColumns().size());
      assertNotNull(scls.get(0).getColumns().get(0).value);
    } finally {
      // insert value
      ColumnPath cp = new ColumnPath("Super1", null, null);
      keyspace.remove("testMultigetSuperSlice_1", cp);
    }
  }

  @Test
  public void testDescribeKeyspace() throws NotFoundException, TException {
    Map<String, Map<String, String>> description = keyspace.describeKeyspace();
    assertNotNull(description);
    assertEquals(4, description.size());
  }

  @Test
  public void testGetCount() throws IllegalArgumentException, NoSuchElementException,
      IllegalStateException, NotFoundException, TException, Exception {
    // insert values
    for (int i = 0; i < 100; i++) {
      ColumnPath cp = new ColumnPath("Standard1", null, bytes("testInsertAndGetAndRemove_" + i));
      keyspace.insert("testGetCount", cp, bytes("testInsertAndGetAndRemove_value_" + i));
    }

    // get value
    ColumnParent clp = new ColumnParent("Standard1", null);
    int count = keyspace.getCount("testGetCount", clp);
    assertEquals(100, count);

    ColumnPath cp = new ColumnPath("Standard1", null, null);
    keyspace.remove("testGetCount", cp);
  }

  @Test
  public void testGetRangeSlice() throws InvalidRequestException, UnavailableException, TException,
      TimedOutException, NotFoundException {
    for (int i = 0; i < 10; i++) {
      ColumnPath cp = new ColumnPath("Standard2", null, bytes("testGetRangeSlice_" + i));
      keyspace.insert("testGetRangeSlice0", cp, bytes("testGetRangeSlice_Value_" + i));
      keyspace.insert("testGetRangeSlice1", cp, bytes("testGetRangeSlice_Value_" + i));
      keyspace.insert("testGetRangeSlice2", cp, bytes("testGetRangeSlice_Value_" + i));
    }

    // get value
    ColumnParent clp = new ColumnParent("Standard2", null);
    SliceRange sr = new SliceRange(new byte[0], new byte[0], false, 150);
    SlicePredicate sp = new SlicePredicate(null, sr);
    Map<String, List<Column>> keySlices = keyspace.getRangeSlice(clp, sp, "testGetRangeSlice0",
        "testGetRangeSlice3", 5);

    assertNotNull(keySlices);
    assertEquals(3, keySlices.size());
    assertNotNull("testGetRangeSlice0 is null", keySlices.get("testGetRangeSlice0"));
    assertEquals("testGetRangeSlice_Value_0", string(keySlices.get("testGetRangeSlice0").get(0).getValue()));
    assertEquals(10, keySlices.get("testGetRangeSlice1").size());

    ColumnPath cp = new ColumnPath("Standard2", null, null);
    keyspace.remove("testGetRanageSlice0", cp);
    keyspace.remove("testGetRanageSlice1", cp);
    keyspace.remove("testGetRanageSlice2", cp);
  }

  @Test
  public void testGetSuperRangeSlice() throws InvalidRequestException, UnavailableException, TException,
      TimedOutException, NotFoundException {
    for (int i = 0; i < 10; i++) {
      ColumnPath cp = new ColumnPath("Super1", bytes("SuperColumn_1"),
          bytes("testGetSuperRangeSlice_" + i));
      keyspace.insert("testGetSuperRangeSlice0", cp, bytes("testGetSuperRangeSlice_Value_" + i));
      keyspace.insert("testGetSuperRangeSlice1", cp, bytes("testGetSuperRangeSlice_Value_" + i));
    }

    // get value
    ColumnParent clp = new ColumnParent("Super1", null);
    SliceRange sr = new SliceRange(new byte[0], new byte[0], false, 150);
    SlicePredicate sp = new SlicePredicate(null, sr);
    Map<String, List<SuperColumn>> keySlices = keyspace.getSuperRangeSlice(clp, sp,
        "testGetSuperRangeSlice0", "testGetSuperRangeSlice3", 5);

    assertNotNull(keySlices);
    assertEquals(2, keySlices.size());
    assertNotNull("testGetSuperRangSlice0 is null", keySlices.get("testGetSuperRangeSlice0"));
    assertEquals("testGetSuperRangeSlice_Value_0",
        string(keySlices.get("testGetSuperRangeSlice0").get(0).getColumns().get(0).getValue()));
    assertEquals(1, keySlices.get("testGetSuperRangeSlice1").size());
    assertEquals(10, keySlices.get("testGetSuperRangeSlice1").get(0).getColumns().size());

    ColumnPath cp = new ColumnPath("Super1", null, null);
    keyspace.remove("testGetSuperRangeSlice0", cp);
    keyspace.remove("testGetSuperRangeSlice1", cp);
  }

  @Test
  public void testGetConsistencyLevel() {
    assertEquals(1, keyspace.getConsistencyLevel());
  }

  @Test
  public void testGetKeyspaceName() {
    assertEquals("Keyspace1", keyspace.getName());
  }

  @Test
  public void testFailover() throws IllegalStateException, PoolExhaustedException, Exception {
    CassandraClient h1client = mock(CassandraClient.class);
    CassandraClient h2client = mock(CassandraClient.class);
    CassandraClient h3client = mock(CassandraClient.class);
    Cassandra.Client h1cassandra = mock(Cassandra.Client.class);
    Cassandra.Client h2cassandra = mock(Cassandra.Client.class);
    Cassandra.Client h3cassandra = mock(Cassandra.Client.class);
    String keyspaceName = "Keyspace1";
    Map<String, Map<String, String>> keyspaceDesc = new HashMap<String, Map<String, String>>();
    Map<String, String> keyspace1Desc = new HashMap<String, String>();
    keyspace1Desc.put(Keyspace.CF_TYPE, Keyspace.CF_TYPE_STANDARD);
    keyspaceDesc.put("Standard1", keyspace1Desc);
    int consistencyLevel = 1;
    ColumnPath cp = new ColumnPath("Standard1", null, bytes("testFailover"));
    CassandraClientPool clientPools = mock(CassandraClientPool.class);
    CassandraClientMonitor monitor = mock(CassandraClientMonitor.class);

    // The token map represents the list of available servers.
    Map<String, String> tokenMap = new HashMap<String, String>();
    tokenMap.put("t1", "h1");
    tokenMap.put("t2", "h2");
    tokenMap.put("t3", "h3");

    when(h1client.getCassandra()).thenReturn(h1cassandra);
    when(h2client.getCassandra()).thenReturn(h2cassandra);
    when(h3client.getCassandra()).thenReturn(h3cassandra);
    when(h1client.getTokenMap(anyBoolean())).thenReturn(tokenMap);
    when(h2client.getTokenMap(anyBoolean())).thenReturn(tokenMap);
    when(h3client.getTokenMap(anyBoolean())).thenReturn(tokenMap);
    when(h1client.getPort()).thenReturn(111);
    when(h2client.getPort()).thenReturn(111);
    when(h3client.getPort()).thenReturn(111);
    when(h1client.getUrl()).thenReturn("h1");
    when(h1client.getIp()).thenReturn("ip1");
    when(h2client.getUrl()).thenReturn("h2");
    when(h2client.getIp()).thenReturn("ip2");
    when(h3client.getUrl()).thenReturn("h3");
    when(h3client.getIp()).thenReturn("ip3");
    when(clientPools.borrowClient("h1", 111)).thenReturn(h1client);
    when(clientPools.borrowClient("h2", 111)).thenReturn(h2client);
    when(clientPools.borrowClient("h3", 111)).thenReturn(h3client);

    // Create one positive pass without failures
    FailoverPolicy failoverPolicy = FailoverPolicy.FAIL_FAST;
    Keyspace ks = new KeyspaceImpl(h1client, keyspaceName, keyspaceDesc, consistencyLevel,
        failoverPolicy, clientPools, monitor);

    ks.insert("key", cp, bytes("value"));

    // now fail the call and make sure it fails fast
    doThrow(new TimedOutException()).when(h1cassandra).insert(anyString(), anyString(),
        (ColumnPath) anyObject(), (byte[]) anyObject(), anyLong(), anyInt());
    try {
      ks.insert("key", cp, bytes("value"));
      fail("Should not have gotten here. The method should have failed with TimedOutException; "
          + "FAIL_FAST");
    } catch (TimedOutException e) {
      // ok
    }

    // Now try the ON_FAIL_TRY_ONE_NEXT_AVAILABLE policy
    // h1 fails, h3 succeeds
    failoverPolicy = FailoverPolicy.ON_FAIL_TRY_ONE_NEXT_AVAILABLE;
    ks = new KeyspaceImpl(h1client, keyspaceName, keyspaceDesc, consistencyLevel, failoverPolicy,
        clientPools, monitor);

    ks.insert("key", cp, bytes("value"));
    verify(h3cassandra).insert(anyString(), anyString(), (ColumnPath) anyObject(),
        (byte[]) anyObject(), anyLong(), anyInt());
    verify(clientPools).borrowClient("h3", 111);

    // make both h1 and h3 fail
    ks = new KeyspaceImpl(h1client, keyspaceName, keyspaceDesc, consistencyLevel, failoverPolicy,
        clientPools, monitor);
    doThrow(new TimedOutException()).when(h3cassandra).insert(anyString(), anyString(),
        (ColumnPath) anyObject(), (byte[]) anyObject(), anyLong(), anyInt());
    try {
      ks.insert("key", cp, bytes("value"));
      fail("Should not have gotten here. The method should have failed with TimedOutException; "
          + "ON_FAIL_TRY_ONE_NEXT_AVAILABLE");
    } catch (TimedOutException e) {
      // ok
    }

    // Now try the full cycle
    // h1 fails, h3 fails, h2 succeeds
    failoverPolicy = FailoverPolicy.ON_FAIL_TRY_ALL_AVAILABLE;
    ks = new KeyspaceImpl(h1client, keyspaceName, keyspaceDesc, consistencyLevel, failoverPolicy,
        clientPools, monitor);

    ks.insert("key", cp, bytes("value"));
    verify(h2cassandra).insert(anyString(), anyString(), (ColumnPath) anyObject(),
        (byte[]) anyObject(), anyLong(), anyInt());

    // now fail them all. h1 fails, h2 fails, h3 fails
    ks = new KeyspaceImpl(h1client, keyspaceName, keyspaceDesc, consistencyLevel, failoverPolicy,
        clientPools, monitor);
    doThrow(new TimedOutException()).when(h2cassandra).insert(anyString(), anyString(),
        (ColumnPath) anyObject(), (byte[]) anyObject(), anyLong(), anyInt());
    try {
      ks.insert("key", cp, bytes("value"));
      fail("Should not have gotten here. The method should have failed with TimedOutException; "
          + "ON_FAIL_TRY_ALL_AVAILABLE");
    } catch (TimedOutException e) {
      // ok
    }
  }

  @Test
  public void testFailover2() throws IllegalStateException, PoolExhaustedException, Exception {
    CassandraClient h1client = mock(CassandraClient.class);
    CassandraClient h2client = mock(CassandraClient.class);
    Cassandra.Client h1cassandra = mock(Cassandra.Client.class);
    Cassandra.Client h2cassandra = mock(Cassandra.Client.class);
    String keyspaceName = "Keyspace1";
    Map<String, Map<String, String>> keyspaceDesc = new HashMap<String, Map<String, String>>();
    Map<String, String> keyspace1Desc = new HashMap<String, String>();
    keyspace1Desc.put(Keyspace.CF_TYPE, Keyspace.CF_TYPE_STANDARD);
    keyspaceDesc.put("Standard1", keyspace1Desc);
    int consistencyLevel = 1;
    ColumnPath cp = new ColumnPath("Standard1", null, bytes("testFailover"));
    CassandraClientPool clientPools = mock(CassandraClientPool.class);
    CassandraClientMonitor monitor = mock(CassandraClientMonitor.class);

    // The token map represents the list of available servers.
    Map<String, String> tokenMap = new HashMap<String, String>();
    tokenMap.put("t1", "h1");
    tokenMap.put("t2", "h2");

    when(h1client.getCassandra()).thenReturn(h1cassandra);
    when(h2client.getCassandra()).thenReturn(h2cassandra);
    when(h1client.getTokenMap(anyBoolean())).thenReturn(tokenMap);
    when(h2client.getTokenMap(anyBoolean())).thenReturn(tokenMap);
    when(h1client.getPort()).thenReturn(111);
    when(h2client.getPort()).thenReturn(111);
    when(h1client.getUrl()).thenReturn("h1");
    when(h1client.getIp()).thenReturn("ip1");
    when(h2client.getUrl()).thenReturn("h2");
    when(h2client.getIp()).thenReturn("ip2");
    when(clientPools.borrowClient("h1", 111)).thenReturn(h1client);
    when(clientPools.borrowClient("h2", 111)).thenReturn(h2client);

    FailoverPolicy failoverPolicy = FailoverPolicy.ON_FAIL_TRY_ALL_AVAILABLE;
    Keyspace ks = new KeyspaceImpl(h1client, keyspaceName, keyspaceDesc, consistencyLevel,
        failoverPolicy, clientPools, monitor);

    // fail the call, use a transport exception
    doThrow(new TTransportException()).when(h1cassandra).insert(anyString(), anyString(),
        (ColumnPath) anyObject(), (byte[]) anyObject(), anyLong(), anyInt());

    ks.insert("key", cp, bytes("value"));

    // Make sure the client is invalidated
    verify(clientPools, times(2)).invalidateClient(h1client);

    // make sure the next call is to h2
    verify(h2client).getCassandra();

    // Now run another insert on the same keyspace to make sure it can handle next writes.
    ks.insert("key2", cp, bytes("value2"));
  }
}

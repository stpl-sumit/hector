package me.prettyprint.cassandra.service;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

public class CassandraHostFactoryTest {

  @Before
  public void setup() {
    
  }
  
  @Test
  public void parseMinimalToCassandraHostSingle() {
    CassandraHost[] cassandraHosts = CassandraHostFactory.createHosts(SINGLE_HOST_CONFIG_MINIMAL);
    assertEquals(1,cassandraHosts.length);
    assertEquals(SINGLE_HOST_CONFIG_MINIMAL,cassandraHosts[0].getUrlPort());
  }
  
  @Test
  public void parseFullConfigToCassandraHostSingle() {
    CassandraHost[] cassandraHosts = CassandraHostFactory.createHosts(SINGLE_HOST_CONFIG_FULL);
    assertEquals(1,cassandraHosts.length);
    assertEquals("localhost:9171",cassandraHosts[0].getUrlPort());
    assertEquals(20, cassandraHosts[0].getMaxActive());
    assertEquals(10, cassandraHosts[0].getMaxIdle());
    assertEquals(5000, cassandraHosts[0].getMaxWaitTimeWhenExhausted());
    assertEquals(ExhaustedPolicy.WHEN_EXHAUSTED_GROW, cassandraHosts[0].getExhaustedPolicy());
  }
  
  @Test
  public void parseSomeConfigToCassandraHostSingle() {
    CassandraHost[] cassandraHosts = CassandraHostFactory.createHosts(SINGLE_HOST_CONFIG_SOME);
    assertEquals(1,cassandraHosts.length);
    assertEquals("localhost:9172",cassandraHosts[0].getUrlPort());
    assertEquals(50, cassandraHosts[0].getMaxActive());
    assertEquals(5, cassandraHosts[0].getMaxIdle());
    assertEquals(-1, cassandraHosts[0].getMaxWaitTimeWhenExhausted());
    assertEquals(ExhaustedPolicy.WHEN_EXHAUSTED_GROW, cassandraHosts[0].getExhaustedPolicy());
  }
  
  @Test(expected=IllegalArgumentException.class)
  public void parseMissingDelim() {
    CassandraHost[] cassandraHosts = CassandraHostFactory.createHosts(SINGLE_HOST_CONFIG_MISSING_DELIM);
  }
  
  @Test(expected=NumberFormatException.class)
  public void parseTransposedValues() {
    CassandraHost[] cassandraHosts = CassandraHostFactory.createHosts(SINGLE_HOST_CONFIG_TRANSPOSED_VALUES);
  }
  
  @Test
  public void parseMultipleToCassandraHost() {
    CassandraHost[] cassandraHosts = CassandraHostFactory.createHosts(SINGLE_HOST_CONFIG_MINIMAL + 
        ";" + SINGLE_HOST_CONFIG_FULL +
        ";" + SINGLE_HOST_CONFIG_SOME);
    assertEquals(3,cassandraHosts.length);
    // no need to test all, just a sampling
    assertEquals("localhost:9172",cassandraHosts[2].getUrlPort());
    assertEquals(20, cassandraHosts[1].getMaxActive());
    assertEquals(5, cassandraHosts[2].getMaxIdle());
    assertEquals(5000, cassandraHosts[1].getMaxWaitTimeWhenExhausted());
    assertEquals(ExhaustedPolicy.WHEN_EXHAUSTED_BLOCK, cassandraHosts[0].getExhaustedPolicy());
  }
  
  private static final String SINGLE_HOST_CONFIG_MINIMAL = 
    "localhost:9170";
  
  private static final String SINGLE_HOST_CONFIG_FULL = 
    "localhost:9171,20,10,5000,WHEN_EXHAUSTED_GROW;";
  
  private static final String SINGLE_HOST_CONFIG_SOME = 
    "localhost:9172,,5,,WHEN_EXHAUSTED_GROW";
  
  private static final String SINGLE_HOST_CONFIG_MISSING_DELIM = 
    "localhost:9170,,5,WHEN_EXHAUSTED_GROW";
  
  private static final String SINGLE_HOST_CONFIG_TRANSPOSED_VALUES = 
    "localhost:9170,,5,WHEN_EXHAUSTED_GROW,5000";
}

package me.prettyprint.cassandra.service;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;

public class CassandraHostFactory {
   
  public static CassandraHost[] createHosts(String hostConfigs) {
    List<CassandraHost> cassandraHosts = new ArrayList<CassandraHost>();
    String[] hostsConfigs = StringUtils.split(hostConfigs, ';');
    for (String host : hostsConfigs) {
      String[] config = StringUtils.splitPreserveAllTokens(host, ',');
      CassandraHost cassandraHost = new CassandraHost(config[0]);
      if (config.length > 1) {
        if (config.length != 5) {
          throw new IllegalArgumentException("Host configuration string did not have the right nuber of ',' delimiters: " + config);
        }
        if (StringUtils.isNotBlank(config[1]))
          cassandraHost.setMaxActive(Integer.valueOf(config[1]));
        if (StringUtils.isNotBlank(config[2]))
          cassandraHost.setMaxIdle(Integer.valueOf(config[2]));
        if (StringUtils.isNotBlank(config[3]))
          cassandraHost.setMaxWaitTimeWhenExhausted(Long.valueOf(config[3]));
        if (StringUtils.isNotBlank(config[4]))
          cassandraHost.setExhaustedPolicy(ExhaustedPolicy.valueOf(config[4]));
      }
      
      cassandraHosts.add(cassandraHost);
    }
    return cassandraHosts.toArray(new CassandraHost[]{});
  }
}

package me.prettyprint.cassandra.service;

import java.util.Set;

import org.apache.commons.pool.PoolableObjectFactory;
import org.apache.thrift.TException;

/**
 * A cassandra client pool per one cassandra host.
 *
 * To obtain new CassandraClient object invoke borrowClient(). Once the application
 * is done, releaseClient().
 *
 *Example code:
 *
 * <pre>
 *
 * CassandraClient client = clientpool.borrowClient();
 * try {
 *   // do something with client and buessiness logic
 * } catch (Exception e) {
 *   // process exception
 * } finally {
 *   clientpool.releaseClient(client);
 * }
 * </pre>
 *
 * @author rantav
 */
/*package*/ interface CassandraClientPoolByHost {



  /**
   * Obtain a client instance from the pool.
   *
   * If there's an available client in the pool, a client is immediately returned.
   * If there's no available client then the behavior depends on whether the pool is exhausted and
   * its exhausted policy.
   * If the pool is not exhausted a new client is created and returned.
   * If it is exhausted, then a call may either fail with {@link PoolExhaustedException} if the
   * policy is {@link ExhaustedPolicy#WHEN_EXHAUSTED_FAIL}, return a new client and grow the pool
   * if the policy is {@link ExhaustedPolicy#WHEN_EXHAUSTED_GROW} or block until a next client is
   * available at the pool, if the policy is {@link ExhaustedPolicy#WHEN_EXHAUSTED_BLOCK}.
   *
   * @return an instance from this pool.
   * @throws IllegalStateException
   *           after {@link #close close} has been called on this pool.
   * @throws Exception
   *           when {@link PoolableObjectFactory#makeObject makeObject} throws
   *           an exception.
   * @throws PoolExhaustedException
   *           when the pool is exhausted and cannot or will not return another
   *           instance.
   */
  CassandraClient borrowClient() throws Exception, PoolExhaustedException,
      IllegalStateException;

  /**
   * Returns a client to pool.
   * The client must was an instance previously borrowed from this pool by borrowClient().
   *
   * @param client
   * @throws Exception if the client was not borrowed from this pool.
   */
  void releaseClient(CassandraClient client) throws Exception;

  /**
   * Returns the number of currently available client number.
   *
   * Note that this number follows the following rule:
   * AvailableNum = PooledClientNumber - UsingNum
   * which means that after initialization the value may be 0, however, as the pool grows by further
   * allocations this value may increase.
   *
   * @return available client in pool
   */
  int getNumIdle();

  /**
   * Returns the number of clients that may be borrowed before the pool is exhausted.
   *
   * Note that this is a different number then getAvailableNum.
   * Before any client is borrowed this number will be the total pool size.
   *
   * @return Number of clients that may be borrowed before the pool is exhausted.
   */
  int getNumBeforeExhausted();

  /**
   * Returns the number of currently borrowed clients.
   *
   * @return
   */
  int getNumActive();

  /**
   * Closes the pool, frees all resources.
   * Calling borrowClient() or releaseClient() after invoking this method on a pool will
   * cause them to throw an IllegalStateException.
   */
  void close();

  boolean isExhausted();

  String getName();

  int getNumBlockedThreads();

  void updateKnownHosts() throws TException;

  Set<String> getKnownHosts();

  /**
   * Take the client out of the pool.
   * Use it if the client has errors.
   */
  void invalidateClient(CassandraClient client);

  /**
   * @return Gets the set of all currently alive clients in the pool. This includes clients that
   *    are borrowed as well as clients that are currently kept in the pool.
   */
  Set<CassandraClient> getLiveClients();
}

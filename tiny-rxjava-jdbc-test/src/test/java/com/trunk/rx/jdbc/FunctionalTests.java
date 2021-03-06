package com.trunk.rx.jdbc;

import com.trunk.rx.jdbc.h2.H2ConnectionProvider;
import com.trunk.rx.jdbc.jooq.sql.Execute;
import com.trunk.rx.jdbc.jooq.sql.InsertReturning;
import com.trunk.rx.jdbc.jooq.sql.Select;
import com.trunk.rx.jdbc.pg.PgConnectionProvider;
import com.trunk.rx.jdbc.test.LiquibaseBootstrap;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SQLDialect;
import org.jooq.Table;
import org.jooq.impl.SQLDataType;
import org.testng.annotations.Test;
import rx.observers.TestSubscriber;

import java.math.BigInteger;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;
import static org.jooq.impl.DSL.using;
import static rx.Observable.error;

/**
 * Since we're testing across a number of packages, we can run our tests of the whole system here.
 */
public class FunctionalTests {

  private static final Table<Record> TEST = table("test");
  private static final Table<Record> TEST_AUTOID = table("test_autoid");
  private static final Field<Integer> ID = field("id", SQLDataType.INTEGER);
  private static final Field<String> NAME = field("name", SQLDataType.VARCHAR);

  @Test
  public void shouldWorkWithAutoCommit() throws Exception {
    TestSubscriber<Object> t = new TestSubscriber<>();
    ConnectionPool.from(new H2ConnectionProvider("FuncTest-shouldWorkWithAutoCommit"))
      .execute(
        connection ->
          Execute.using(connection, c -> using(c, SQLDialect.H2).createTable(TEST).column(ID, ID.getDataType()))
            .cast(Object.class)
            .concatWith(
              Execute.using(connection, c -> using(c, SQLDialect.H2).insertInto(TEST, ID).values(1))
            )
            .concatWith(
              Execute.using(connection, c -> using(c, SQLDialect.H2).insertInto(TEST, ID).values(2))
            )
            .concatWith(
              Select.using(connection, c -> using(c, SQLDialect.H2).select().from(TEST), r -> r.getValue(0))
            )
      )
      .withAutoCommit()
      .subscribe(t);

    t.assertNoErrors();
    t.assertCompleted();
    t.assertValues(0, 1, 1, 1, 2);
  }

  @Test
  public void shouldWorkWithSingleCommit() throws Exception {
    TestSubscriber<Object> t = new TestSubscriber<>();
    ConnectionPool.from(new H2ConnectionProvider("FuncTest-shouldWorkWithSingleCommit"))
      .execute(
        connection ->
          Execute.using(connection, c -> using(c, SQLDialect.H2).createTable(TEST).column(ID, ID.getDataType()))
            .cast(Object.class)
            .concatWith(
              Execute.using(connection, c -> using(c, SQLDialect.H2).insertInto(TEST, ID).values(1))
            )
            .concatWith(
              Execute.using(connection, c -> using(c, SQLDialect.H2).insertInto(TEST, ID).values(2))
            )
            .concatWith(
              Select.using(connection, c -> using(c, SQLDialect.H2).select().from(TEST), r -> r.getValue(0))
            )
      )
      .withSingleTransaction()
      .subscribe(t);

    t.awaitTerminalEvent();

    t.assertNoErrors();
    t.assertCompleted();
    t.assertValues(0, 1, 1, 1, 2);
  }

  @Test
  public void shouldWorkWithTransactionPerEvent() throws Exception {
    TestSubscriber<Object> t = new TestSubscriber<>();
    ConnectionPool.from(new H2ConnectionProvider("FuncTest-shouldWorkWithCommitPerEvent"))
      .execute(
        connection ->
          Execute.using(connection, c -> using(c, SQLDialect.H2).createTable(TEST).column(ID, ID.getDataType()))
            .cast(Object.class)
            .concatWith(
              Execute.using(connection, c -> using(c, SQLDialect.H2).insertInto(TEST, ID).values(1))
            )
            .concatWith(
              Execute.using(connection, c -> using(c, SQLDialect.H2).insertInto(TEST, ID).values(2))
            )
            .concatWith(
              Select.using(connection, c -> using(c, SQLDialect.H2).select().from(TEST), r -> r.getValue(0))
            )
      )
      .withTransactionPerEvent()
      .subscribe(t);

    t.assertNoErrors();
    t.assertCompleted();
    t.assertValues(0, 1, 1, 1, 2);
  }

  @Test
  public void autoCommitShouldStoreUpToFailureForAllUpdates() throws Exception {
    TestSubscriber<Integer> t = new TestSubscriber<>();
    ConnectionPool pool = ConnectionPool.from(new H2ConnectionProvider("FuncTest-autoCommitShouldStoreUpToFailureForAllUpdates"));
    pool
      .execute(
        connection ->
          Execute.using(connection, c -> using(c, SQLDialect.H2).createTable(TEST).column(ID, ID.getDataType()))
            .cast(Object.class)
            .concatWith(
              Execute.using(connection, c -> using(c, SQLDialect.H2).insertInto(TEST, ID).values(1))
            )
            .concatWith(
              Execute.using(connection, c -> using(c, SQLDialect.H2).insertInto(TEST, ID).values(2))
            )
            .concatWith(
              // Only emits a single event to end subscriber
              Execute.using(connection, c -> using(c, SQLDialect.H2).insertInto(TEST, ID).values(3))
                .concatWith(
                  error(new RuntimeException())
                )
                .toList()
            )
            .concatWith(
              Execute.using(connection, c -> using(c, SQLDialect.H2).insertInto(TEST, ID).values(4))
            )
      )
      .subscribe(o -> {}, throwable -> {});

    pool.execute(
      connection ->
        Select.using(connection, c -> using(c, SQLDialect.H2).select().from(TEST), r -> r.getValue(0, Integer.class))
    )
      .subscribe(t);

    t.assertNoErrors();
    t.assertCompleted();
    t.assertValues(1, 2, 3);
  }

  @Test
  public void transactionPerEventShouldStoreUpToFailure() throws Exception {
    TestSubscriber<Integer> t = new TestSubscriber<>();
    ConnectionPool pool = ConnectionPool.from(new H2ConnectionProvider("FuncTest-transactionPerEventShouldStoreUpToFailure"));
    pool
      .execute(
        connection ->
          Execute.using(connection, c -> using(c, SQLDialect.H2).createTable(TEST).column(ID, ID.getDataType()))
            .cast(Object.class)
            .concatWith(
              Execute.using(connection, c -> using(c, SQLDialect.H2).insertInto(TEST, ID).values(1))
            )
            .concatWith(
              Execute.using(connection, c -> using(c, SQLDialect.H2).insertInto(TEST, ID).values(2))
            )
            .concatWith(
              // Only emits a single event to end subscriber
              Execute.using(connection, c -> using(c, SQLDialect.H2).insertInto(TEST, ID).values(3))
                .concatWith(
                  error(new RuntimeException())
                )
                .toList()
            )
            .concatWith(
              Execute.using(connection, c -> using(c, SQLDialect.H2).insertInto(TEST, ID).values(4))
            )
      )
      .withTransactionPerEvent()
      .subscribe(o -> {}, throwable -> {});

    pool.execute(
      connection ->
        Select.using(connection, c -> using(c, SQLDialect.H2).select().from(TEST), r -> r.getValue(0, Integer.class))
    )
      .subscribe(t);

    t.assertNoErrors();
    t.assertCompleted();
    t.assertValues(1, 2);
  }

  @Test
  public void singleTransactionShouldStoreNothingOnError() throws Exception {
    TestSubscriber<Integer> t = new TestSubscriber<>();
    ConnectionPool pool = ConnectionPool.from(new H2ConnectionProvider("FuncTest-singleTransactionShouldStoreNothingOnError"));
    pool
      .execute(
        connection ->
          Execute.using(connection, c -> using(c, SQLDialect.H2).createTable(TEST).column(ID, ID.getDataType()))
      )
      .subscribe();
    pool
      .execute(
        connection ->
          Execute.using(connection, c -> using(c, SQLDialect.H2).insertInto(TEST, ID).values(1))
            .concatWith(
              Execute.using(connection, c -> using(c, SQLDialect.H2).insertInto(TEST, ID).values(2))
            )
            .concatWith(
              error(new RuntimeException())
            )
            .concatWith(
              Execute.using(connection, c -> using(c, SQLDialect.H2).insertInto(TEST, ID).values(3))
            )
      )
      .withSingleTransaction()
      .subscribe(o -> {}, throwable -> {});

    pool
      .execute(
        connection ->
          Select.using(connection, c -> using(c, SQLDialect.H2).select().from(TEST), r -> r.getValue(0, Integer.class))
      )
      .subscribe(t);

    t.assertNoErrors();
    t.assertCompleted();
    t.assertNoValues();
  }

  @Test
  public void singleTransactionShouldStoreNothingOnEarlyUnsubscribe() throws Exception {
    TestSubscriber<Integer> t = new TestSubscriber<>();
    ConnectionPool pool = ConnectionPool.from(new H2ConnectionProvider("FuncTest-singleTransactionShouldStoreNothing"));
    pool
      .execute(
        connection ->
          Execute.using(connection, c -> using(c, SQLDialect.H2).createTable(TEST).column(ID, ID.getDataType()))
      )
      .subscribe();
    pool
      .execute(
        connection ->
          Execute.using(connection, c -> using(c, SQLDialect.H2).insertInto(TEST, ID).values(1))
            .concatWith(
              Execute.using(connection, c -> using(c, SQLDialect.H2).insertInto(TEST, ID).values(2))
            )
            .concatWith(
              Execute.using(connection, c -> using(c, SQLDialect.H2).insertInto(TEST, ID).values(3))
            )
      )
      .withSingleTransaction()
      .take(2)
      .subscribe(o -> {}, throwable -> {});

    pool
      .execute(
        connection ->
          Select.using(connection, c -> using(c, SQLDialect.H2).select().from(TEST), r -> r.getValue(0, Integer.class))
      )
      .subscribe(t);

    t.assertNoErrors();
    t.assertCompleted();
    t.assertNoValues();
  }

  @Test
  public void shouldWorkAcrossConnectionsOnSamePool() throws Exception {
    TestSubscriber<Object> t = new TestSubscriber<>();
    ConnectionPool pool = ConnectionPool.from(new H2ConnectionProvider("FuncTest-shouldWorkAcrossConnectionsOnSamePool"));
    pool.execute(
      connection ->
        Execute.using(connection, c -> using(c, SQLDialect.H2).createTable(TEST).column(ID, ID.getDataType()))
          .cast(Object.class)
    )
      .concatWith(
        pool.execute(
          connection ->
            Execute.using(connection, c -> using(c, SQLDialect.H2).insertInto(TEST, ID).values(1))
        )
          .withSingleTransaction()
      )
      .concatWith(
        pool.execute(
          connection ->
            Execute.using(connection, c -> using(c, SQLDialect.H2).insertInto(TEST, ID).values(2))
        )
          .withTransactionPerEvent()
      )
      .concatWith(
        pool.execute(
          connection ->
            Select.using(connection, c -> using(c, SQLDialect.H2).select().from(TEST), r -> r.getValue(0))
        )
      )
      .subscribe(t);

    t.assertNoErrors();
    t.assertCompleted();
    t.assertValues(0, 1, 1, 1, 2);
  }

  @Test
  public void shouldReturnValuesAfterInsert() throws Exception {
    TestSubscriber<Integer> t = new TestSubscriber<>();
    String host = System.getenv("DB_HOST");
    String database =  System.getenv("DB_PASSWORD");
    String username = System.getenv("DB_USER");
    String password = System.getenv("DB_PASSWORD");
    ConnectionPool.from(
      LiquibaseBootstrap.using(
        new PgConnectionProvider(host, database, username, password, 4)
      )
    )
      .execute(
        connection ->
          Execute.using(connection, c -> using(c, SQLDialect.POSTGRES).delete(TEST_AUTOID))
            .ignoreElements()
            .concatWith(
              Execute.using(
                connection,
                c ->
                  using(c, SQLDialect.POSTGRES).alterSequence("test_autoid_id_seq").restartWith(BigInteger.ONE)
              )
              .ignoreElements()
            )
            .concatWith(
              InsertReturning.using(
                connection,
                c ->
                  using(c, SQLDialect.POSTGRES)
                    .insertInto(TEST_AUTOID)
                    .set(NAME, "foo")
                    .returning(ID),
                record -> record.getValue(ID)
              )
            )
            .concatWith(
              InsertReturning.using(
                connection,
                c ->
                  using(c, SQLDialect.POSTGRES)
                    .insertInto(TEST_AUTOID)
                    .columns(NAME)
                    .values("bar")
                    .values("baz")
                    .returning(ID),
                record -> record.get(ID)
              )
            )

      )
      .subscribe(t);

    t.assertNoErrors();
    t.assertCompleted();
    t.assertValues(1, 2, 3);
  }
}

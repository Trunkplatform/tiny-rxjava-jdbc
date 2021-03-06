package com.trunk.rx.jdbc.jooq.sql;

import org.jooq.InsertResultStep;
import org.jooq.Query;
import org.jooq.Record;

import java.sql.Connection;

/**
 * A functional interface to defer the creation of
 * {@link Query Queries} that return an {@link InsertResultStep}
 * until needed.
 *
 * @see InsertReturning
 */
@FunctionalInterface
public interface InsertReturningQueryBuilder<T extends InsertResultStep<? extends Record>> {
  T build(Connection connection);
}

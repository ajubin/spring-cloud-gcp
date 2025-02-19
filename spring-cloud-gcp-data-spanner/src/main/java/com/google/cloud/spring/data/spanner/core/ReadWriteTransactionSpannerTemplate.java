/*
 * Copyright 2017-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.spring.data.spanner.core;

import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.Mutation;
import com.google.cloud.spanner.ReadContext;
import com.google.cloud.spanner.Statement;
import com.google.cloud.spanner.TimestampBound;
import com.google.cloud.spanner.TransactionContext;
import com.google.cloud.spring.data.spanner.core.admin.SpannerSchemaUtils;
import com.google.cloud.spring.data.spanner.core.convert.SpannerEntityProcessor;
import com.google.cloud.spring.data.spanner.core.mapping.SpannerDataException;
import com.google.cloud.spring.data.spanner.core.mapping.SpannerMappingContext;
import java.util.Collection;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A {@link SpannerTemplate} that performs all operations in a single transaction. This template is
 * not intended for the user to directly instantiate.
 *
 * @since 1.1
 */
class ReadWriteTransactionSpannerTemplate extends SpannerTemplate {

  private TransactionContext transactionContext;

  ReadWriteTransactionSpannerTemplate(
      Supplier<DatabaseClient> databaseClient,
      SpannerMappingContext mappingContext,
      SpannerEntityProcessor spannerEntityProcessor,
      SpannerMutationFactory spannerMutationFactory,
      SpannerSchemaUtils spannerSchemaUtils,
      TransactionContext transactionContext) {
    super(
        databaseClient,
        mappingContext,
        spannerEntityProcessor,
        spannerMutationFactory,
        spannerSchemaUtils);
    this.transactionContext = transactionContext;
  }

  @Override
  protected void applyMutations(Collection<Mutation> mutations) {
    this.transactionContext.buffer(mutations);
  }

  @Override
  protected ReadContext getReadContext() {
    return this.transactionContext;
  }

  @Override
  public long executeDmlStatement(Statement statement) {
    return this.transactionContext.executeUpdate(statement);
  }

  @Override
  public long executePartitionedDmlStatement(Statement statement) {
    throw new SpannerDataException(
        "A read-write transaction template cannot execute partitioned DML.");
  }

  @Override
  protected ReadContext getReadContext(TimestampBound timestampBound) {
    throw new SpannerDataException(
        "Getting stale snapshot read contexts is not supported"
            + " in read-write transaction templates.");
  }

  @Override
  public <T> T performReadWriteTransaction(Function<SpannerTemplate, T> operations) {
    throw new SpannerDataException(
        "A read-write transaction is already under execution. "
            + "Opening sub-transactions is not supported!");
  }

  @Override
  public <T> T performReadOnlyTransaction(
      Function<SpannerTemplate, T> operations, SpannerReadOptions readOptions) {
    throw new SpannerDataException(
        "A read-write transaction is already under execution. "
            + "Opening sub-transactions is not supported!");
  }
}

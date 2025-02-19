/*
 * Copyright 2017-2020 the original author or authors.
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

package com.google.cloud.spring.data.datastore.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.cloud.Timestamp;
import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.FullEntity;
import com.google.cloud.datastore.KeyFactory;
import com.google.cloud.spring.data.datastore.core.convert.DatastoreServiceObjectToKeyFactory;
import com.google.cloud.spring.data.datastore.core.convert.DefaultDatastoreEntityConverter;
import com.google.cloud.spring.data.datastore.core.convert.ObjectToKeyFactory;
import com.google.cloud.spring.data.datastore.core.mapping.DatastoreMappingContext;
import com.google.cloud.spring.data.datastore.core.mapping.Entity;
import com.google.cloud.spring.data.datastore.repository.config.EnableDatastoreAuditing;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.domain.AuditorAware;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

/** Tests the auditing features of the template. */
@RunWith(SpringRunner.class)
@ContextConfiguration
public class DatastoreTemplateAuditingTests {

  private static final LocalDateTime LONG_AGO = LocalDate.parse("2000-01-01").atStartOfDay();

  @Autowired DatastoreTemplate datastoreTemplate;
  @Autowired Datastore datastore;

  @Test
  public void testModifiedNullProperties() {
    TestEntity testEntity = new TestEntity();
    testEntity.id = "a";
    // intentionally leaving the other two audit properties untouched.

    assertThat(this.datastoreTemplate.save(testEntity)).isNotNull();
  }

  @Test
  public void testModifiedPrevProperties() {
    TestEntity testEntity = new TestEntity();
    testEntity.id = "a";
    testEntity.lastTouched = LONG_AGO;
    testEntity.lastUser = "person";

    assertThat(this.datastoreTemplate.saveAll(Collections.singletonList(testEntity)))
        .isNotNull()
        .hasSize(1);
  }

  @Test
  public void testInTransaction() {
    when(datastore.runInTransaction(any()))
        .thenAnswer(
            invocation -> {
              Datastore.TransactionCallable<?> callable = invocation.getArgument(0);
              return callable.run(datastore);
            });

    TestEntity testEntity = new TestEntity();
    testEntity.id = "a";

    assertThat(
            (TestEntity)
                this.datastoreTemplate.performTransaction(
                    operations -> operations.save(testEntity)))
        .isNotNull();
  }

  /** Spring config for the tests. */
  @Configuration
  @EnableDatastoreAuditing
  static class Config {

    @Bean
    public DatastoreMappingContext datastoreMappingContext() {
      return new DatastoreMappingContext();
    }

    @Bean
    public Datastore datastore() {
      return mock(Datastore.class);
    }

    @Bean
    public DatastoreTemplate datastoreTemplate(
        DatastoreMappingContext datastoreMappingContext, Datastore datastore) {

      when(datastore.newKeyFactory()).thenReturn(new KeyFactory("project"));

      ObjectToKeyFactory objectToKeyFactory =
          new DatastoreServiceObjectToKeyFactory(() -> datastore);

      DatastoreTemplate datastoreTemplate =
          new DatastoreTemplate(
              () -> datastore,
              new DefaultDatastoreEntityConverter(datastoreMappingContext, objectToKeyFactory),
              datastoreMappingContext,
              objectToKeyFactory);

      when(datastore.put((FullEntity<?>[]) any()))
          .thenAnswer(
              invocation -> {
                FullEntity testEntity = invocation.getArgument(0);
                assertThat(testEntity.getTimestamp("lastTouched")).isNotNull();
                assertThat(testEntity.getTimestamp("lastTouched"))
                    .isGreaterThan(Timestamp.of(java.sql.Timestamp.valueOf(LONG_AGO)));
                assertThat(testEntity.getString("lastUser")).isEqualTo("test_user");
                return null;
              });

      return datastoreTemplate;
    }

    @Bean
    public AuditorAware<String> auditorProvider() {
      return () -> Optional.of("test_user");
    }
  }

  @Entity(name = "custom_test_table")
  private static class TestEntity {
    @Id String id;

    @LastModifiedBy String lastUser;

    @LastModifiedDate LocalDateTime lastTouched;
  }
}

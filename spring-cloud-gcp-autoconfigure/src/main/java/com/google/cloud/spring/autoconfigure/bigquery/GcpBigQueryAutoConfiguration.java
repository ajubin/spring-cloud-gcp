/*
 * Copyright 2017-2019 the original author or authors.
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

package com.google.cloud.spring.autoconfigure.bigquery;

import com.google.api.gax.core.CredentialsProvider;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.spring.autoconfigure.core.GcpContextAutoConfiguration;
import com.google.cloud.spring.bigquery.core.BigQueryTemplate;
import com.google.cloud.spring.core.DefaultCredentialsProvider;
import com.google.cloud.spring.core.GcpProjectIdProvider;
import com.google.cloud.spring.core.UserAgentHeaderProvider;
import java.io.IOException;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Provides client objects for interfacing with BigQuery. */
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter(GcpContextAutoConfiguration.class)
@ConditionalOnProperty(value = "spring.cloud.gcp.bigquery.enabled", matchIfMissing = true)
@ConditionalOnClass({BigQuery.class, BigQueryTemplate.class})
@EnableConfigurationProperties(GcpBigQueryProperties.class)
public class GcpBigQueryAutoConfiguration {

  private final String projectId;

  private final CredentialsProvider credentialsProvider;

  private final String datasetName;

  GcpBigQueryAutoConfiguration(
      GcpBigQueryProperties gcpBigQueryProperties,
      GcpProjectIdProvider projectIdProvider,
      CredentialsProvider credentialsProvider)
      throws IOException {

    this.projectId =
        (gcpBigQueryProperties.getProjectId() != null)
            ? gcpBigQueryProperties.getProjectId()
            : projectIdProvider.getProjectId();

    this.credentialsProvider =
        (gcpBigQueryProperties.getCredentials().hasKey()
            ? new DefaultCredentialsProvider(gcpBigQueryProperties)
            : credentialsProvider);

    this.datasetName = gcpBigQueryProperties.getDatasetName();
  }

  @Bean
  @ConditionalOnMissingBean
  public BigQuery bigQuery() throws IOException {
    BigQueryOptions bigQueryOptions =
        BigQueryOptions.newBuilder()
            .setProjectId(this.projectId)
            .setCredentials(this.credentialsProvider.getCredentials())
            .setHeaderProvider(new UserAgentHeaderProvider(GcpBigQueryAutoConfiguration.class))
            .build();
    return bigQueryOptions.getService();
  }

  @Bean
  @ConditionalOnMissingBean
  public BigQueryTemplate bigQueryTemplate(BigQuery bigQuery) {
    return new BigQueryTemplate(bigQuery, this.datasetName);
  }
}

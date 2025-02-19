/*
 * Copyright 2021-2021 the original author or authors.
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

package com.google.cloud.spring.autoconfigure.pubsub.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import com.google.api.gax.batching.FlowControlSettings;
import com.google.api.gax.batching.FlowController;
import com.google.api.gax.core.ExecutorProvider;
import com.google.api.gax.retrying.RetrySettings;
import com.google.api.gax.rpc.StatusCode.Code;
import com.google.cloud.spring.autoconfigure.core.GcpContextAutoConfiguration;
import com.google.cloud.spring.autoconfigure.pubsub.GcpPubSubAutoConfiguration;
import com.google.cloud.spring.autoconfigure.pubsub.GcpPubSubProperties;
import com.google.cloud.spring.core.DefaultGcpProjectIdProvider;
import com.google.cloud.spring.core.GcpProjectIdProvider;
import com.google.cloud.spring.pubsub.PubSubAdmin;
import com.google.cloud.spring.pubsub.core.PubSubConfiguration;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.threeten.bp.Duration;

public class PubSubAutoConfigurationIntegrationTests {

  private static final Log LOGGER =
      LogFactory.getLog(PubSubAutoConfigurationIntegrationTests.class);

  private static GcpProjectIdProvider projectIdProvider;

  private ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withPropertyValues(
              "spring.cloud.gcp.pubsub.subscriber.retryableCodes=INTERNAL",
              "spring.cloud.gcp.pubsub.subscription.test-sub-1.executor-threads=3",
              "spring.cloud.gcp.pubsub.subscription.test-sub-1.retry.total-timeout-seconds=600",
              "spring.cloud.gcp.pubsub.subscription.test-sub-1.retry.initial-retry-delay-seconds=100",
              "spring.cloud.gcp.pubsub.subscription.test-sub-1.retry.retry-delay-multiplier=1.3",
              "spring.cloud.gcp.pubsub.subscription.test-sub-1.retry.max-retry-delay-seconds=600",
              "spring.cloud.gcp.pubsub.subscription.test-sub-1.retry.max-attempts=1",
              "spring.cloud.gcp.pubsub.subscription.test-sub-1.retry.initial-rpc-timeout-seconds=600",
              "spring.cloud.gcp.pubsub.subscription.test-sub-1.retry.rpc-timeout-multiplier=1",
              "spring.cloud.gcp.pubsub.subscription.test-sub-1.retry.max-rpc-timeout-seconds=600",
              "spring.cloud.gcp.pubsub.subscription.test-sub-2.executor-threads=1",
              "spring.cloud.gcp.pubsub.subscription.test-sub-2.max-ack-extension-period=0",
              "spring.cloud.gcp.pubsub.subscription.test-sub-2.parallel-pull-count=1",
              "spring.cloud.gcp.pubsub.subscription.test-sub-2.flow-control.max-outstanding-element-Count=1",
              "spring.cloud.gcp.pubsub.subscription.test-sub-2.flow-control.max-outstanding-request-Bytes=1",
              "spring.cloud.gcp.pubsub.subscription.test-sub-2.flow-control.limit-exceeded-behavior=Ignore")
          .withConfiguration(
              AutoConfigurations.of(
                  GcpContextAutoConfiguration.class, GcpPubSubAutoConfiguration.class));

  @BeforeClass
  public static void enableTests() {
    assumeThat(System.getProperty("it.pubsub")).isEqualTo("true");
    projectIdProvider = new DefaultGcpProjectIdProvider();
  }

  @Test
  public void testPull() {

    this.contextRunner.run(
        context -> {
          PubSubAdmin pubSubAdmin = context.getBean(PubSubAdmin.class);
          String topicName = "test-topic";
          String subscriptionName = "test-sub-1";
          if (pubSubAdmin.getTopic(topicName) != null) {
            pubSubAdmin.deleteTopic(topicName);
          }
          if (pubSubAdmin.getSubscription(subscriptionName) != null) {
            pubSubAdmin.deleteSubscription(subscriptionName);
          }

          pubSubAdmin.createTopic(topicName);
          pubSubAdmin.createSubscription(subscriptionName, topicName, 10);

          PubSubTemplate pubSubTemplate = context.getBean(PubSubTemplate.class);

          pubSubTemplate.publish(topicName, "message1");
          pubSubTemplate.pull(subscriptionName, 4, false);

          // Validate auto-config properties
          GcpPubSubProperties gcpPubSubProperties = context.getBean(GcpPubSubProperties.class);
          RetrySettings expectedRetrySettings =
              RetrySettings.newBuilder()
                  .setTotalTimeout(Duration.ofSeconds(600L))
                  .setInitialRetryDelay(Duration.ofSeconds(100L))
                  .setRetryDelayMultiplier(1.3)
                  .setMaxRetryDelay(Duration.ofSeconds(600L))
                  .setMaxAttempts(1)
                  .setInitialRpcTimeout(Duration.ofSeconds(600L))
                  .setRpcTimeoutMultiplier(1)
                  .setMaxRpcTimeout(Duration.ofSeconds(600L))
                  .build();
          PubSubConfiguration.Retry retry =
              gcpPubSubProperties.computeSubscriberRetrySettings(
                  subscriptionName, projectIdProvider.getProjectId());
          assertThat(retry.getTotalTimeoutSeconds()).isEqualTo(600L);
          assertThat(retry.getInitialRetryDelaySeconds()).isEqualTo(100L);
          assertThat(retry.getRetryDelayMultiplier()).isEqualTo(1.3);
          assertThat(retry.getMaxRetryDelaySeconds()).isEqualTo(600L);
          assertThat(retry.getMaxAttempts()).isEqualTo(1);
          assertThat(retry.getInitialRpcTimeoutSeconds()).isEqualTo(600L);
          assertThat(retry.getRpcTimeoutMultiplier()).isEqualTo(1);
          assertThat(retry.getMaxRpcTimeoutSeconds()).isEqualTo(600L);
          ThreadPoolTaskScheduler scheduler =
              (ThreadPoolTaskScheduler) context.getBean("threadPoolScheduler_test-sub-1");
          assertThat(scheduler).isNotNull();
          assertThat(scheduler.getThreadNamePrefix()).isEqualTo("gcp-pubsub-subscriber-test-sub-1");
          assertThat(scheduler.isDaemon()).isTrue();
          assertThat(
                  (ThreadPoolTaskScheduler)
                      context.getBean("globalPubSubSubscriberThreadPoolScheduler"))
              .isNotNull();
          assertThat((ExecutorProvider) context.getBean("subscriberExecutorProvider-test-sub-1"))
              .isNotNull();
          assertThat((ExecutorProvider) context.getBean("globalSubscriberExecutorProvider"))
              .isNotNull();
          assertThat(
                  gcpPubSubProperties.computeRetryableCodes(
                      "test-sub-1", projectIdProvider.getProjectId()))
              .isEqualTo(new Code[] {Code.INTERNAL});
          assertThat((RetrySettings) context.getBean("subscriberRetrySettings-test-sub-1"))
              .isEqualTo(expectedRetrySettings);

          pubSubAdmin.deleteSubscription(subscriptionName);
          pubSubAdmin.deleteTopic(topicName);
        });
  }

  @Test
  public void testSubscribe() {
    this.contextRunner.run(
        context -> {
          PubSubAdmin pubSubAdmin = context.getBean(PubSubAdmin.class);
          PubSubTemplate pubSubTemplate = context.getBean(PubSubTemplate.class);

          String topicName = "test-topic";
          String subscriptionName = "test-sub-2";
          if (pubSubAdmin.getTopic(topicName) != null) {
            pubSubAdmin.deleteTopic(topicName);
          }
          if (pubSubAdmin.getSubscription(subscriptionName) != null) {
            pubSubAdmin.deleteSubscription(subscriptionName);
          }

          assertThat(pubSubAdmin.getTopic(topicName)).isNull();
          assertThat(pubSubAdmin.getSubscription(subscriptionName)).isNull();
          pubSubAdmin.createTopic(topicName);
          pubSubAdmin.createSubscription(subscriptionName, topicName);
          pubSubTemplate.publish(topicName, "tatatatata").get();
          pubSubTemplate.subscribe(
              subscriptionName,
              message -> {
                LOGGER.info(
                    "Message received from "
                        + subscriptionName
                        + " subscription: "
                        + message.getPubsubMessage().getData().toStringUtf8());
                message.ack();
              });

          // Validate auto-config properties
          GcpPubSubProperties gcpPubSubProperties = context.getBean(GcpPubSubProperties.class);
          PubSubConfiguration.FlowControl flowControl =
              gcpPubSubProperties.computeSubscriberFlowControlSettings(
                  subscriptionName, projectIdProvider.getProjectId());
          FlowControlSettings flowControlSettings =
              FlowControlSettings.newBuilder()
                  .setMaxOutstandingElementCount(1L)
                  .setMaxOutstandingRequestBytes(1L)
                  .setLimitExceededBehavior(FlowController.LimitExceededBehavior.Ignore)
                  .build();
          assertThat(
                  (FlowControlSettings) context.getBean("subscriberFlowControlSettings-test-sub-2"))
              .isEqualTo(flowControlSettings);
          assertThat(flowControl.getMaxOutstandingElementCount()).isEqualTo(1L);
          assertThat(flowControl.getMaxOutstandingRequestBytes()).isEqualTo(1L);
          assertThat(flowControl.getLimitExceededBehavior())
              .isEqualTo(FlowController.LimitExceededBehavior.Ignore);
          assertThat(
                  gcpPubSubProperties.computeMaxAckExtensionPeriod(
                      subscriptionName, projectIdProvider.getProjectId()))
              .isZero();
          assertThat(
                  gcpPubSubProperties.computeParallelPullCount(
                      subscriptionName, projectIdProvider.getProjectId()))
              .isEqualTo(1);
          ThreadPoolTaskScheduler scheduler =
              (ThreadPoolTaskScheduler) context.getBean("threadPoolScheduler_test-sub-2");
          assertThat(scheduler).isNotNull();
          assertThat(scheduler.getThreadNamePrefix()).isEqualTo("gcp-pubsub-subscriber-test-sub-2");
          assertThat(scheduler.isDaemon()).isTrue();
          assertThat(
                  (ThreadPoolTaskScheduler)
                      context.getBean("globalPubSubSubscriberThreadPoolScheduler"))
              .isNotNull();
          assertThat((ExecutorProvider) context.getBean("subscriberExecutorProvider-test-sub-2"))
              .isNotNull();
          assertThat((ExecutorProvider) context.getBean("globalSubscriberExecutorProvider"))
              .isNotNull();

          pubSubAdmin.deleteSubscription(subscriptionName);
          pubSubAdmin.deleteTopic(topicName);
        });
  }
}

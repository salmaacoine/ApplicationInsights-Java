// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.applicationinsights.agent.internal.profiler.triggers;

import com.microsoft.applicationinsights.agent.internal.configuration.Configuration;
import com.microsoft.applicationinsights.alerting.aiconfig.AlertingConfig;
import com.microsoft.applicationinsights.alerting.alert.AlertBreach;
import com.microsoft.applicationinsights.alerting.analysis.TimeSource;
import com.microsoft.applicationinsights.alerting.analysis.aggregations.Aggregation;
import com.microsoft.applicationinsights.alerting.analysis.aggregations.ThresholdBreachRatioAggregation;
import com.microsoft.applicationinsights.alerting.analysis.filter.AlertRequestFilter;
import com.microsoft.applicationinsights.alerting.analysis.pipelines.AlertPipeline;
import com.microsoft.applicationinsights.alerting.analysis.pipelines.SingleAlertPipeline;
import com.microsoft.applicationinsights.alerting.config.AlertConfiguration;
import com.microsoft.applicationinsights.alerting.config.AlertMetricType;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Constructs an AlertPipeline for processing span telemetry data. */
class RequestAlertPipelineBuilder {

  private static final Logger logger = LoggerFactory.getLogger(RequestAlertPipelineBuilder.class);

  private RequestAlertPipelineBuilder() {}

  /** Form a single trigger context from configuration. */
  static AlertPipeline build(
      Configuration.RequestTrigger configuration,
      Consumer<AlertBreach> alertAction,
      TimeSource timeSource) {

    if (configuration.profileDuration < 30) {
      logger.warn(
          "A profile duration of "
              + configuration.profileDuration
              + " seconds was requested, profiles must be a minimum of 30 seconds. This configuration has been set to 30 seconds");
      configuration.profileDuration = 30;
    }

    AlertingConfig.RequestTrigger requestTriggerConfiguration =
        buildRequestTriggerConfiguration(configuration);

    AlertRequestFilter filter = AlertRequestFilterBuilder.build(configuration.filter);

    Aggregation aggregation = getAggregation(configuration, timeSource);

    // TODO make threshold and throttling responsive to type argument

    AlertConfiguration config =
        AlertConfiguration.builder()
            .setType(AlertMetricType.REQUEST)
            .setEnabled(true)
            .setThreshold(configuration.threshold.value)
            .setProfileDurationSeconds(configuration.profileDuration)
            .setCooldownSeconds(configuration.throttling.value)
            .setRequestTrigger(requestTriggerConfiguration)
            .build();

    return SingleAlertPipeline.create(filter, aggregation, config, alertAction);
  }

  // visible for tests
  static AlertingConfig.RequestTrigger buildRequestTriggerConfiguration(
      Configuration.RequestTrigger configuration) {

    AlertingConfig.RequestTriggerType type =
        AlertingConfig.RequestTriggerType.valueOf(configuration.type.name());

    AlertingConfig.RequestFilter filter =
        new AlertingConfig.RequestFilter()
            .setType(AlertingConfig.RequestFilterType.valueOf(configuration.filter.type.name()))
            .setValue(configuration.filter.value);

    AlertingConfig.RequestAggregationConfig requestAggregationConfig =
        new AlertingConfig.RequestAggregationConfig()
            .setThresholdMillis(configuration.aggregation.configuration.thresholdMillis)
            .setMinimumSamples(configuration.aggregation.configuration.minimumSamples);

    AlertingConfig.RequestAggregation aggregation =
        new AlertingConfig.RequestAggregation()
            .setType(
                AlertingConfig.RequestAggregationType.valueOf(
                    configuration.aggregation.type.name()))
            .setWindowSizeMillis(configuration.aggregation.windowSizeMillis)
            .setConfiguration(requestAggregationConfig);

    AlertingConfig.RequestTriggerThreshold requestTriggerThreshold =
        new AlertingConfig.RequestTriggerThreshold()
            .setType(
                AlertingConfig.RequestTriggerThresholdType.valueOf(
                    configuration.threshold.type.name()))
            .setValue(configuration.threshold.value);

    AlertingConfig.RequestTriggerThrottling throttling =
        new AlertingConfig.RequestTriggerThrottling()
            .setType(
                AlertingConfig.RequestTriggerThrottlingType.valueOf(
                    configuration.throttling.type.name()))
            .setValue(configuration.throttling.value);

    return new AlertingConfig.RequestTrigger()
        .setName(configuration.name)
        .setType(type)
        .setFilter(filter)
        .setAggregation(aggregation)
        .setThreshold(requestTriggerThreshold)
        .setThrottling(throttling)
        .setProfileDuration(configuration.profileDuration);
  }

  @Nullable
  private static Aggregation getAggregation(
      Configuration.RequestTrigger configuration, TimeSource timeSource) {
    if (configuration.aggregation.type == Configuration.RequestAggregationType.BREACH_RATIO) {
      return new ThresholdBreachRatioAggregation(
          configuration.aggregation.configuration.thresholdMillis,
          configuration.aggregation.configuration.minimumSamples,
          configuration.aggregation.windowSizeMillis / 1000,
          timeSource,
          false);
    }
    return null;
  }
}

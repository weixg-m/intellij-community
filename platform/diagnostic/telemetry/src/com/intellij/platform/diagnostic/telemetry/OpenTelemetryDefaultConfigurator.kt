// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry

import com.intellij.openapi.util.ShutDownTracker
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.platform.diagnostic.telemetry.otExporters.AggregatedMetricsExporter
import com.intellij.platform.diagnostic.telemetry.otExporters.AggregatedSpansProcessor
import com.intellij.platform.diagnostic.telemetry.otExporters.CsvMetricsExporter
import com.intellij.util.ConcurrencyUtil
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.common.AttributesBuilder
import io.opentelemetry.sdk.OpenTelemetrySdkBuilder
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.jetbrains.annotations.ApiStatus
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@ApiStatus.Internal
open class OpenTelemetryDefaultConfigurator(@JvmField protected val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Default),
                                            @JvmField protected val otelSdkBuilder: OpenTelemetrySdkBuilder,
                                            @JvmField protected val serviceName: String = "",
                                            @JvmField protected val serviceVersion: String = "",
                                            @JvmField protected val serviceNamespace: String = "",
                                            customResourceBuilder: ((AttributesBuilder) -> Unit)? = null,
                                            enableMetricsByDefault: Boolean) {
  private val metricsReportingPath = if (enableMetricsByDefault) OpenTelemetryUtils.metricsReportingPath() else null
  private val shutdownCompletionTimeout: Long = 10
  private val resource: Resource = Resource.create(
    Attributes.builder()
      .put(ResourceAttributes.SERVICE_NAME, serviceName)
      .put(ResourceAttributes.SERVICE_VERSION, serviceVersion)
      .put(ResourceAttributes.SERVICE_NAMESPACE, serviceNamespace)
      .put(ResourceAttributes.OS_TYPE, SystemInfoRt.OS_NAME)
      .put(ResourceAttributes.OS_VERSION, SystemInfoRt.OS_VERSION)
      .put(ResourceAttributes.HOST_ARCH, System.getProperty("os.arch"))
      .put(ResourceAttributes.SERVICE_INSTANCE_ID, DateTimeFormatter.ISO_INSTANT.format(Instant.now()))
      .also {
        customResourceBuilder?.invoke(it)
      }
      .build()
  )

  internal val aggregatedMetricsExporter: AggregatedMetricsExporter = AggregatedMetricsExporter()
  internal val aggregatedSpansProcessor: AggregatedSpansProcessor = AggregatedSpansProcessor(mainScope)

  private fun isMetricsEnabled(): Boolean = metricsReportingPath != null

  private fun registerSpanExporters(spanExporters: List<AsyncSpanExporter>) {
    if (spanExporters.isEmpty()) {
      return
    }

    val tracerProvider = SdkTracerProvider.builder()
      .addSpanProcessor(BatchSpanProcessor(coroutineScope = mainScope, spanExporters = spanExporters))
      .addSpanProcessor(aggregatedSpansProcessor)
      .setResource(resource)
      .build()

    otelSdkBuilder.setTracerProvider(tracerProvider)
    ShutDownTracker.getInstance().registerShutdownTask {
      tracerProvider.shutdown().join(shutdownCompletionTimeout, TimeUnit.SECONDS)
    }
  }

  private fun registerMetricsExporter(metricsExporters: List<MetricsExporterEntry>) {
    val registeredMetricsReaders = SdkMeterProvider.builder()
    // can't reuse standard BoundedScheduledExecutorService because this library uses unsupported `scheduleAtFixedRate`
    val pool = Executors.newScheduledThreadPool(1, ConcurrencyUtil.newNamedThreadFactory("PeriodicMetricReader"))
    for (entry in metricsExporters) {
      for (metricExporter in entry.metrics) {
        val metricsReader = PeriodicMetricReader.builder(metricExporter).setExecutor(pool).setInterval(entry.duration).build()
        registeredMetricsReaders.registerMetricReader(metricsReader)
      }
    }
    val meterProvider = registeredMetricsReaders.setResource(resource).build()
    otelSdkBuilder.setMeterProvider(meterProvider)
    ShutDownTracker.getInstance().registerShutdownTask(meterProvider::shutdown)
  }

  open fun createMetricsExporters(): List<MetricsExporterEntry> {
    metricsReportingPath ?: return emptyList()

    val result = mutableListOf<MetricsExporterEntry>()
    result.add(MetricsExporterEntry(
      metrics = listOf(
        FilteredMetricsExporter(
          CsvMetricsExporter(
            RollingFileSupplier(metricsReportingPath))) { metric -> metric.belongsToScope(PlatformMetrics) }
      ),
      duration = Duration.ofMinutes(1))
    )

    result.add(MetricsExporterEntry(listOf(aggregatedMetricsExporter), Duration.ofMinutes(1)))
    return result
  }

  open fun createSpanExporters(): List<AsyncSpanExporter> = emptyList()

  fun getConfiguredSdkBuilder(): OpenTelemetrySdkBuilder {
    registerSpanExporters(spanExporters = createSpanExporters())
    if (isMetricsEnabled()) {
      registerMetricsExporter(createMetricsExporters())
    }
    return otelSdkBuilder
  }
}
// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.monitoring

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords
import com.intellij.openapi.vfs.newvfs.persistent.VFSLoadException
import com.intellij.openapi.vfs.newvfs.persistent.VFSLoadException.ErrorCategory.*
import java.util.concurrent.TimeUnit.MILLISECONDS

/**
 * Reports VFS initialization conditions to FUS -- once, on app start.
 * <p/>
 * 'Initialization conditions' are: VFS version, init attempts, and cause for VFS rebuild, if any
 */
class VFSInitializationConditionsToFusReporter : ProjectActivity {

  override suspend fun execute(project: Project) {
    val vfsImplementationVersion = FSRecords.getVersion()
    val initializationFailures = FSRecords.initializationFailures()
    val wasCreateANew = FSRecords.wasCreateANew()
    val creationTimestamp = FSRecords.getCreationTimestamp()
    val totalInitializationDurationMs = FSRecords.totalInitializationDuration(MILLISECONDS)

    val rebuildCause = if (wasCreateANew && initializationFailures.isEmpty()) {
      INITIAL
    }
    else if (initializationFailures.isEmpty()) {
      NONE
    }
    else {
      initializationFailures
        .filterIsInstance<VFSLoadException>()
        .map { it.category() }
        .firstOrNull()?:UNRECOGNIZED
    }

    VfsUsageCollector.logVfsInitialization(
      vfsImplementationVersion,
      creationTimestamp,
      initializationFailures.size + 1,
      rebuildCause,
      totalInitializationDurationMs
    )
  }
}

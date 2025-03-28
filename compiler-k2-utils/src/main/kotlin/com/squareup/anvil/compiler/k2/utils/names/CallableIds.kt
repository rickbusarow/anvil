package com.squareup.anvil.compiler.k2.utils.names

import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId

public fun CallableId.requireClassId(): ClassId =
  requireNotNull(classId) { "ClassId is null: $this" }

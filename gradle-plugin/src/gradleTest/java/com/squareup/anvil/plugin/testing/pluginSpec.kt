package com.squareup.anvil.plugin.testing

import com.rickbusarow.kase.KaseTestEnvironmentDsl
import com.rickbusarow.kase.gradle.dsl.PluginsSpec
import com.rickbusarow.kase.gradle.dsl.model.PluginApplication
import com.squareup.anvil.plugin.buildProperties.anvilPluginID
import com.squareup.anvil.plugin.buildProperties.anvilVersion

/**
 * shorthand for `id("$anvilPluginID", version = version, apply = true)`
 *
 * @param version the version of the Anvil plugin to use.  defaults to the current version.
 * @param apply if `false`, the rendered statement will include `apply false`
 * @see com.squareup.anvil.plugin.buildProperties.anvilPluginID
 * @see com.squareup.anvil.plugin.buildProperties.anvilVersion
 */
@KaseTestEnvironmentDsl
fun PluginsSpec.anvilWithVersion(
  version: String = anvilVersion,
  apply: Boolean = true,
): PluginsSpec = addElement(
  PluginApplication.ID(
    identifier = anvilPluginID,
    version = version,
    apply = apply,
  ),
)

/**
 * shorthand for `id("$anvilPluginID", version = null, apply = apply)`
 *
 * @param apply if `false`, the rendered statement will include `apply false`
 * @see com.squareup.anvil.plugin.buildProperties.anvilPluginID
 */
@KaseTestEnvironmentDsl
fun PluginsSpec.anvil(apply: Boolean = true): PluginsSpec = addElement(
  PluginApplication.ID(
    identifier = anvilPluginID,
    version = null,
    apply = apply,
  ),
)

package com.squareup.anvil.annotations.internal

/**
 * An internal annotation that is used to propagate module contribution metadata to downstream
 * compilations. Should not be used directly.
 *
 * @property hints Each hint corresponds to a single contributed module.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
@Repeatable
@InternalAnvilApi
public annotation class InternalContributedModuleHints(val hints: Array<String>)

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
@Repeatable
@InternalAnvilApi
public annotation class InternalContributedComponentHints(val hints: Array<String>)

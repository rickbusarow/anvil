package com.squareup.anvil.compiler.testing

import io.kotest.assertions.print.print
import io.kotest.matchers.EqualityMatcherResult
import io.kotest.matchers.Matcher
import io.kotest.matchers.file.shouldExist
import io.kotest.matchers.file.shouldNotExist
import io.kotest.matchers.neverNullMatcher
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import java.io.File

public interface MoreAsserts {

  public fun File.deleteOrFail() {
    delete()
    shouldNotExist()
  }

  public fun File.deleteRecursivelyOrFail() {
    deleteRecursively()
    shouldNotExist()
  }

  public infix fun File.shouldExistWithText(expectedText: String) {
    shouldExist()
    readText() shouldBe expectedText
  }

  public infix fun File.shouldExistWithTextContaining(substring: String) {
    shouldExist()
    readText() shouldContain substring
  }

  public infix fun String?.shouldContain(substring: String): String? {
    this should containWithActualExpected(substring)
    return this
  }

  public infix fun <T> Iterable<T>.shouldContain(expected: T): Iterable<T> {
    this should containWithActualExpected(expected)
    return this
  }
}

/**
 * This replaces Kotest's `contain` so that it can return a different `MatcherResult`. The
 * [EqualityMatcherResult] results in a different exception when it fails, which enables the 'click
 * to see difference' feature in IntelliJ. That diff is much more legible.
 */
private fun containWithActualExpected(substring: String): Matcher<String?> =
  neverNullMatcher { actual ->
    EqualityMatcherResult(
      passed = actual.contains(substring),
      actual = actual,
      expected = substring,
      failureMessageFn = {
        "${actual.print().value} should include substring ${substring.print().value}"
      },
      negatedFailureMessageFn = {
        "${actual.print().value} should not include substring ${substring.print().value}"
      },
    )
  }

/**
 * This replaces Kotest's `contain` so that it can return a different `MatcherResult`. The
 * [EqualityMatcherResult] results in a different exception when it fails, which enables the 'click
 * to see difference' feature in IntelliJ. That diff is much more legible.
 */
private fun <T> containWithActualExpected(expected: T): Matcher<Iterable<T>> =
  neverNullMatcher { actual ->
    EqualityMatcherResult(
      passed = actual.contains(expected),
      actual = actual,
      expected = expected,
      failureMessageFn = {
        "${actual.print().value} should include element: ${expected.print().value}"
      },
      negatedFailureMessageFn = {
        "${actual.print().value} should not include element: ${expected.print().value}"
      },
    )
  }

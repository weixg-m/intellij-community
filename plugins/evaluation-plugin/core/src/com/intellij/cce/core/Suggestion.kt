// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.core

data class Suggestion(
  val text: String,
  val presentationText: String,
  val source: SuggestionSource,
  val details: Map<String, Any?> = emptyMap(),
  val kind: SuggestionKind = SuggestionKind.ANY
) {
  fun withSuggestionKind(kind: SuggestionKind): Suggestion {
    return Suggestion(text, presentationText, source, details, kind)
  }

  companion object {
    const val SCORE_KEY: String = "score"
    const val TOKENS_COUNT_KEY: String = "tokens-count"
  }
}

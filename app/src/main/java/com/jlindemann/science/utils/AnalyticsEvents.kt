package com.jlindemann.science.utils

/**
 * Every custom event name the app sends.
 *
 * Named constants rather than bare strings at the call site: a typo in an event name does not fail
 * the build, it silently produces a second event in the console that nobody notices for a month.
 *
 * GA4 limits these names to 40 characters and reserves the `firebase_`, `google_` and `ga_`
 * prefixes.
 */
object AnalyticsEvent {
    const val FRAGMENT_VIEW = "fragment_view"
    const val INTRO_PAGE_VIEW = "intro_page_view"

    const val AI_PANEL_OPEN = "ai_panel_open"
    const val AI_PANEL_CLOSE = "ai_panel_close"
    const val AI_AGENT_INIT = "ai_agent_init"
    const val AI_MESSAGE_SENT = "ai_message_sent"
    const val AI_RESPONSE = "ai_response"
    const val AI_RATE_LIMITED = "ai_rate_limited"
    const val AI_ENGINE_ERROR = "ai_engine_error"
    const val AI_NEW_CHAT = "ai_new_chat"
    const val AI_HISTORY_OPEN = "ai_history_open"
    const val AI_HISTORY_RESTORE = "ai_history_restore"
    const val AI_ACTION_TAP = "ai_action_tap"
    const val AI_CARD_LOCKED = "ai_card_locked"
    const val AI_UPGRADE_TAP = "ai_upgrade_tap"
}

/**
 * Every custom parameter name.
 *
 * GA4 limits a parameter name to 40 characters, a string value to 100, and an event to 25
 * parameters. [AnalyticsHelper.logEvent] enforces the value limit; the rest are short enough here
 * to be obvious by inspection.
 */
object AnalyticsParam {
    const val FRAGMENT_NAME = "fragment_name"
    const val ENTRY_SOURCE = "entry_source"
    const val PAGE_INDEX = "page_index"

    const val HOST = "host"
    const val SOURCE = "source"
    const val RESULT = "result"
    const val TARGET = "target"

    const val SUCCESS = "success"
    const val ERROR = "error"
    const val DURATION_MS = "duration_ms"
    const val LATENCY_MS = "latency_ms"

    const val PATH = "path"
    const val INTENT = "intent"
    const val CONFIDENCE = "confidence"
    const val LANGUAGE = "language"
    const val LANG_SWITCHED = "language_switched"
    const val ELEMENT_FOUND = "element_resolved"

    const val HAS_CARD = "has_card"
    const val CARD_KIND = "card_kind"
    const val ACTION_COUNT = "action_count"

    const val QUERY_CHARS = "query_chars"
    const val QUERY_WORDS = "query_words"
    const val MESSAGE_COUNT = "message_count"
    const val MESSAGE_INDEX = "message_index"
    const val DAILY_LIMIT = "daily_limit"
    const val HAS_CONTEXT = "has_element_context"
}

/**
 * How the user arrived at a fragment.
 *
 * Every programmatic tab change in `MainActivity` sets one of these, so a spike in a tab can be
 * traced to the thing that caused it — a widget, a deep link, or people actually tapping it.
 */
object AnalyticsSource {
    const val INITIAL = "initial"
    const val BOTTOM_NAV = "bottom_nav"
    const val RETURN = "return"
    const val BACK = "back"
    const val WIDGET = "widget"
    const val DIALOG = "dialog"
    const val PRO_INTENT = "pro_intent"
    const val FLASHCARD_RESULTS = "flashcard_results"
    const val UNKNOWN = "unknown"
}

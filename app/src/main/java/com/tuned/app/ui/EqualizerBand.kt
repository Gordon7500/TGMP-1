package com.tuned.app.ui

/** One band of the equalizer, as reported by AudioEffectsController for the active audio session. */
data class EqBand(
    val index: Int,
    val label: String,
    val levelMb: Int
)

/** Kept as a type alias so any existing code referencing the old name still compiles. */
typealias EqualizerBand = EqBand

enum class SortOrder(val label: String) {
    DATE_ADDED("Recently added"),
    TITLE("Title A-Z"),
    ARTIST("Artist A-Z"),
    DURATION("Duration")
}

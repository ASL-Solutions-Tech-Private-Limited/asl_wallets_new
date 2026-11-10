package com.asl.wallet.feature.home.domain

/** The cards on Home that a retailer can put in their own order (Arrange Home). */
enum class HomeSection(val key: String) {
    Banking("banking"),
    Banners("banners"),
    Recharge("recharge"),
    Bbps("bbps"),
    Reports("reports"),
    Recent("recent"),
}

/** Pure rules for the Home section order, so every case is unit-tested. */
object HomeLayout {
    /** The old app's first dashboard order. */
    val DEFAULT: List<HomeSection> = HomeSection.entries.toList()

    /**
     * A saved order back as sections: unknown or repeated keys are dropped, and any section the saved list lacks
     * (e.g. one added in a later app version) goes at the end in its default place. Never loses a section.
     */
    fun parse(saved: List<String>?): List<HomeSection> {
        val known = saved.orEmpty().mapNotNull { k -> HomeSection.entries.firstOrNull { it.key == k.trim() } }.distinct()
        return known + DEFAULT.filterNot { it in known }
    }

    fun keys(order: List<HomeSection>): List<String> = order.map(HomeSection::key)

    /** Moves the section at [from] to [to] (both clamped). */
    fun move(order: List<HomeSection>, from: Int, to: Int): List<HomeSection> {
        if (from !in order.indices) return order
        val target = to.coerceIn(0, order.lastIndex)
        if (target == from) return order
        return order.toMutableList().apply { add(target, removeAt(from)) }
    }
}

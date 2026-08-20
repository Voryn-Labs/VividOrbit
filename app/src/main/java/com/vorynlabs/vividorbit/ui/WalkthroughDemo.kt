package com.vorynlabs.vividorbit.ui

fun walkthroughPageCount(): Int = 8

fun nextWalkthroughPage(current: Int): Int =
    (current + 1).coerceAtMost(walkthroughPageCount() - 1)

fun prevWalkthroughPage(current: Int): Int =
    (current - 1).coerceAtLeast(0)

fun isLastWalkthroughPage(current: Int): Boolean =
    current >= walkthroughPageCount() - 1

fun shouldShowWalkthrough(seen: Boolean): Boolean = !seen

fun isPhoneWalkthroughPage(page: Int): Boolean = page == 6

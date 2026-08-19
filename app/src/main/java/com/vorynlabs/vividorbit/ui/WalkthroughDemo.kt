package com.vorynlabs.vividorbit.ui

data class WalkthroughDemoChannel(
    val id: Int,
    val name: String,
    val dthNumber: String,
    var customNumber: String,
    var favorite: Boolean = false,
    var hidden: Boolean = false
)

data class DemoAssignResult(
    val assignedNumber: String,
    val swappedWith: String?
)

fun walkthroughPageCount(): Int = 6

fun nextWalkthroughPage(current: Int): Int =
    (current + 1).coerceAtMost(walkthroughPageCount() - 1)

fun prevWalkthroughPage(current: Int): Int =
    (current - 1).coerceAtLeast(0)

fun isLastWalkthroughPage(current: Int): Boolean =
    current >= walkthroughPageCount() - 1

fun shouldShowWalkthrough(seen: Boolean): Boolean = !seen

fun isPlaygroundPage(page: Int): Boolean = page == 2 || page == 3

fun isRemotePage(page: Int): Boolean = page == 1

fun isPhonePage(page: Int): Boolean = page == 4

fun seedDemoChannels(): MutableList<WalkthroughDemoChannel> = mutableListOf(
    WalkthroughDemoChannel(1, "Zee TV HD", "104", "104"),
    WalkthroughDemoChannel(2, "Sony SAB HD", "201", "201"),
    WalkthroughDemoChannel(3, "DD News", "12", "12")
)

/** Give [targetId] number "1". If another row already has "1", swap. */
fun assignDemoNumberOne(
    channels: MutableList<WalkthroughDemoChannel>,
    targetId: Int
): DemoAssignResult? {
    val target = channels.find { it.id == targetId } ?: return null
    val holder = channels.find { it.customNumber == "1" && it.id != targetId }
    if (holder != null) {
        val previous = target.customNumber
        target.customNumber = "1"
        holder.customNumber = previous
        return DemoAssignResult("1", holder.name)
    }
    target.customNumber = "1"
    return DemoAssignResult("1", null)
}

fun toggleDemoFavorite(
    channels: MutableList<WalkthroughDemoChannel>,
    targetId: Int
): Boolean {
    val target = channels.find { it.id == targetId } ?: return false
    target.favorite = !target.favorite
    return target.favorite
}

fun toggleDemoHidden(
    channels: MutableList<WalkthroughDemoChannel>,
    targetId: Int
): Boolean {
    val target = channels.find { it.id == targetId } ?: return false
    target.hidden = !target.hidden
    return target.hidden
}

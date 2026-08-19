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
    val swappedWith: String?,
    val swappedNumber: String? = null
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
    WalkthroughDemoChannel(1, "Discovery Channel", "104", "104"),
    WalkthroughDemoChannel(2, "National Geographic", "201", "201"),
    WalkthroughDemoChannel(3, "CNN International", "12", "12")
)

/** Assign [newNumber] to [targetId]. If another row holds [newNumber], swap atomically. */
fun assignDemoNumber(
    channels: MutableList<WalkthroughDemoChannel>,
    targetId: Int,
    newNumber: String
): DemoAssignResult? {
    val target = channels.find { it.id == targetId } ?: return null
    val holder = channels.find { it.customNumber == newNumber && it.id != targetId }
    val previousTargetNum = target.customNumber
    if (holder != null) {
        target.customNumber = newNumber
        holder.customNumber = previousTargetNum
        return DemoAssignResult(newNumber, holder.name, previousTargetNum)
    }
    target.customNumber = newNumber
    return DemoAssignResult(newNumber, null)
}

/** Legacy helper for single-digit assign test */
fun assignDemoNumberOne(
    channels: MutableList<WalkthroughDemoChannel>,
    targetId: Int
): DemoAssignResult? = assignDemoNumber(channels, targetId, "1")

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

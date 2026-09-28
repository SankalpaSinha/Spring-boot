package io.pointscore.points;

public enum LedgerEntryType {
    /** Points minted by a purchase. Always positive. */
    EARN,
    /** Points spent on a reward. Always negative. */
    REDEEM,
    /** Points killed by the expiry job. Always negative. */
    EXPIRE,
    /** Manual correction by an administrator. Either sign. */
    ADJUST
}

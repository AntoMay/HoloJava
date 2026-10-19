package me.holojava.fullgame;

/**
 * Starting coin balance the game tops a fresh (or spent-down) install up to on boot.
 *
 * <p>This lives in its own file so a build can replace it wholesale. The value checked in here is
 * the development one: enough coins to drive the whole shop without grinding, which is what you
 * want while the coin economy has no real earning rate and every upgrade needs exercising.
 *
 * <p>A public build is the opposite -- it must start at zero, so players earn what they spend.
 * {@code tools/build_public.sh} therefore compiles a copy of the sources with this one file
 * rewritten to {@code 0L}. The value has to be compiled in rather than read from a system
 * property: a real MIDP device cannot be given {@code -D} flags, and FreeJ2ME's own
 * {@code restartApp()} puts them after {@code -jar} where the JVM ignores them.
 *
 * <p>Changing this file alone changes the development and test builds only. See
 * {@link FullGameCanvas#SHOP_STARTING_CAPITAL} for how the balance is applied.
 */
final class ShopStartingCapital {

    /**
     * Balance ceiling. The game credits {@code capital - current} when the stored balance is below
     * it, so coins earned in play are kept and a player who spends down is restored to the same
     * figure rather than losing progress.
     */
    static final long VALUE = 999999999L;

    private ShopStartingCapital() {
    }
}
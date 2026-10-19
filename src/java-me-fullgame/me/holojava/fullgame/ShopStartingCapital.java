package me.holojava.fullgame;

/**
 * Starting coin balance the game tops a fresh (or spent-down) install up to on boot.
 *
 * <p>This lives in its own file so a build can replace it wholesale. The default checked in here
 * is zero, which is what development and the test suite run against: {@code SaveStore}'s tests pin
 * a fresh install to a zero balance, and the coin economy has no real earning rate yet, so handing
 * out free coins by default would mean testing against a balance that never happens in play.
 *
 * <p>A public build wants the opposite -- enough coins to drive the whole shop without grinding --
 * so {@code tools/build_public.sh} compiles a copy of the sources with this one file overwritten.
 * The value therefore has to be compiled in rather than read from a system property: a real MIDP
 * device cannot be given {@code -D} flags, and FreeJ2ME's own {@code restartApp()} puts them after
 * {@code -jar} where the JVM ignores them.
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
    static final long VALUE = 0L;

    private ShopStartingCapital() {
    }
}
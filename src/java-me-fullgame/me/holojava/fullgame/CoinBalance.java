package me.holojava.fullgame;

/**
 * Pure, allocation-free model for the global permanent coin balance.
 *
 * <p>The balance is global and shared by every character, which is why it is modelled apart from
 * the run-local coin counter in {@code RunWorld}: the run counter is reset on every run, this one
 * is meant to survive them. The two are deliberately never combined in this class.
 *
 * <p>Every operation is a pure function of its arguments. Nothing here reads or writes storage,
 * touches MIDP, or keeps state, so the whole model is exercised on a desktop JVM. Persistence and
 * gameplay wiring are later stages; this stage only defines what a balance change *means*.
 *
 * <p>Type choice: {@code long}, not {@code int}. An {@code int} balance would wrap after roughly
 * 2.1 billion coins, and a wrapped negative balance would then be persisted as real progress.
 * {@code long} is available on CLDC 1.1 and is already used for run timing in this project.
 *
 * <p>Overflow and underflow policy, applied consistently by every operation:
 * <ul>
 *   <li>Adding past {@link #MAX} saturates at {@link #MAX}. It never wraps to a negative number.</li>
 *   <li>Spending more than the balance is refused and leaves the balance untouched. The balance
 *       never becomes negative.</li>
 *   <li>A negative balance read from storage is not trusted; {@link #sanitize(long)} clamps it to
 *       zero rather than propagating it.</li>
 *   <li>Zero and negative amounts are rejected by the change operations and leave the balance
 *       exactly as it was. Refusing is preferred over throwing so callers never have to guard.</li>
 * </ul>
 *
 * <p>Refusing quietly follows the style of {@code SaveStore}, which returns early on invalid
 * input, and of {@code MathUtil}, which clamps instead of throwing.
 */
final class CoinBalance {
    /** Largest representable balance. Adding past this saturates here instead of wrapping. */
    static final long MAX = Long.MAX_VALUE;

    private CoinBalance() { }

    /**
     * Normalises a balance read from storage.
     *
     * @param balance the stored value; any negative number is treated as corrupt or absent
     * @return {@code balance} when it is already usable, otherwise {@code 0}
     */
    static long sanitize(long balance) {
        if (balance < 0) return 0;
        return balance;
    }

    /**
     * True when the value is already usable as a balance without sanitising.
     *
     * @param balance value to check
     * @return true when {@code balance >= 0}
     */
    static boolean isValid(long balance) {
        return balance >= 0;
    }

    /**
     * Computes the balance after crediting coins, for example a collected pickup or a stage bonus.
     *
     * <p>Credit is additive and may never reduce the balance. An amount of zero or less is
     * rejected, and so is a negative starting balance, which is sanitised first.
     *
     * @param balance current balance; a negative value is treated as zero
     * @param amount coins to credit; zero or negative is rejected
     * @return the new balance, saturating at {@link #MAX} instead of overflowing
     */
    static long add(long balance, long amount) {
        long current = sanitize(balance);
        if (amount <= 0) return current;
        // Compare before adding so the addition itself can never overflow.
        if (amount > MAX - current) return MAX;
        return current + amount;
    }

    /**
     * Computes the balance after paying coins, for example a future stat purchase.
     *
     * <p>A cost the balance cannot cover is refused outright rather than driving the balance
     * negative, so a caller that ignores the result still cannot create debt.
     *
     * @param balance current balance; a negative value is treated as zero
     * @param cost coins to spend; zero or negative is rejected
     * @return the remaining balance, or {@code balance} itself when the cost is refused
     */
    static long spend(long balance, long cost) {
        long current = sanitize(balance);
        if (cost <= 0) return current;
        if (cost > current) return current;
        return current - cost;
    }

    /**
     * True when {@link #add(long, long)} would credit the full amount.
     *
     * <p>A false result means either the inputs are rejected, or the result would saturate. A
     * caller that needs every coin to land rather than be clamped should check this first.
     *
     * @param balance current balance
     * @param amount coins to credit
     * @return true when the credit is valid and fits without saturating
     */
    static boolean canAdd(long balance, long amount) {
        if (!isValid(balance) || amount <= 0) return false;
        return amount <= MAX - balance;
    }

    /**
     * True when {@link #spend(long, long)} can pay the full cost.
     *
     * @param balance current balance
     * @param cost coins to spend
     * @return true when the cost is positive and covered by the balance
     */
    static boolean canSpend(long balance, long cost) {
        if (!isValid(balance) || cost <= 0) return false;
        return cost <= balance;
    }

    /** Clamps an arbitrary value into the usable balance range {@code [0, MAX]}. */
    static long cap(long value) {
        if (value < 0) return 0;
        if (value > MAX) return MAX;
        return value;
    }
}
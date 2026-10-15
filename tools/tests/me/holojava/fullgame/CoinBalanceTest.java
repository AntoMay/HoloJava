package me.holojava.fullgame;

/**
 * Unit verification for the pure {@link CoinBalance} model.
 *
 * <p>Runs on a desktop JVM. It constructs nothing from the MIDP API and never touches
 * {@code javax.microedition.rms}, so no MIDlet, emulator, or device is required. Every method it
 * exercises is a pure static function of its arguments.
 *
 * <p>Coverage targets, in order: ordinary crediting, a zero starting balance, the boundary values
 * either side of {@code Long.MAX_VALUE}, overflow saturation, refusal to underflow, and every
 * rejected input shape.
 */
public final class CoinBalanceTest {

    private static int checks = 0;
    private static int failures = 0;

    public static void main(String[] args) {
        testOrdinaryCredit();
        testZeroStartingBalance();
        testBoundaryValues();
        testOverflowSaturates();
        testUnderflowIsRefused();
        testInvalidInputs();
        testSanitize();
        testCanAdd();
        testCanSpend();
        testInvariants();
        testCap();

        System.out.println("checks=" + checks + " failures=" + failures);
        if (failures > 0) {
            System.out.println("RESULT: FAIL");
            System.exit(1);
        }
        System.out.println("RESULT: PASS");
    }

    // ---- 1. Ordinary crediting -------------------------------------------------

    private static void testOrdinaryCredit() {
        System.out.println("[1] ordinary credit");
        expectEq(CoinBalance.add(0, 10), 10, "0 + 10");
        expectEq(CoinBalance.add(100, 50), 150, "100 + 50");
        expectEq(CoinBalance.add(1, 1), 2, "1 + 1");
        // A realistic run total: many pickups of the documented value.
        long total = 0;
        for (int i = 0; i < 5000; i++) total = CoinBalance.add(total, 10);
        expectEq(total, 50000, "5000 pickups of 10");
    }

    // ---- 2. Zero starting balance ----------------------------------------------

    private static void testZeroStartingBalance() {
        System.out.println("[2] zero starting balance");
        expectEq(CoinBalance.add(0, 1), 1, "0 + 1");
        expectEq(CoinBalance.add(0, Long.MAX_VALUE), Long.MAX_VALUE, "0 + MAX");
        expectEq(CoinBalance.spend(0, 1), 0, "0 spend 1 is refused, stays 0");
        expectEq(CoinBalance.spend(0, 0), 0, "0 spend 0 stays 0");
    }

    // ---- 3. Boundary values ----------------------------------------------------

    private static void testBoundaryValues() {
        System.out.println("[3] boundary values");
        expectEq(CoinBalance.add(Long.MAX_VALUE - 1, 1), Long.MAX_VALUE, "MAX-1 + 1 == MAX");
        expectEq(CoinBalance.add(Long.MAX_VALUE - 10, 10), Long.MAX_VALUE, "MAX-10 + 10 == MAX");
        expectEq(CoinBalance.add(Long.MAX_VALUE, 0), Long.MAX_VALUE, "MAX + 0 == MAX");
        expectEq(CoinBalance.add(Long.MAX_VALUE, Long.MAX_VALUE), Long.MAX_VALUE, "MAX + MAX saturates");
        expectEq(CoinBalance.add(Long.MAX_VALUE, 5), Long.MAX_VALUE, "MAX + 5 saturates");
        expectEq(CoinBalance.spend(Long.MAX_VALUE, Long.MAX_VALUE), 0, "spend MAX drains to 0");
        expectEq(CoinBalance.spend(Long.MAX_VALUE, Long.MAX_VALUE - 1), 1, "spend MAX-1 leaves 1");
    }

    // ---- 4. Overflow must saturate, never wrap ----------------------------------

    private static void testOverflowSaturates() {
        System.out.println("[4] overflow saturates");
        long overflowed = CoinBalance.add(Long.MAX_VALUE - 5, 1000);
        expectEq(overflowed, Long.MAX_VALUE, "MAX-5 + 1000 saturates");
        expect(overflowed > 0, "overflow must not wrap to a negative balance");
        // The classic wraparound value an int would produce must never appear.
        expect(overflowed != -1005L, "no int-style wraparound to -1005");
        expectEq(CoinBalance.add(Long.MAX_VALUE, Long.MAX_VALUE), Long.MAX_VALUE, "MAX + MAX stays MAX");
        expect(CoinBalance.add(Long.MAX_VALUE, Long.MAX_VALUE) >= 0, "double-MAX result is non-negative");
    }

    // ---- 5. Underflow must be refused ------------------------------------------

    private static void testUnderflowIsRefused() {
        System.out.println("[5] underflow refused");
        expectEq(CoinBalance.spend(100, 101), 100, "spend more than held is refused");
        expectEq(CoinBalance.spend(100, Long.MAX_VALUE), 100, "spend MAX from 100 refused");
        expectEq(CoinBalance.spend(100, 100), 0, "spend exactly held drains to 0");
        long after = CoinBalance.spend(50, 60);
        expect(after >= 0, "refused spend never yields a negative balance");
        // Two refused spends must not accumulate any hidden debt.
        expectEq(CoinBalance.spend(CoinBalance.spend(50, 60), 60), 50, "repeated refusal is stable");
    }

    // ---- 6. Invalid inputs -----------------------------------------------------

    private static void testInvalidInputs() {
        System.out.println("[6] invalid inputs");
        expectEq(CoinBalance.add(100, 0), 100, "zero credit is a no-op");
        expectEq(CoinBalance.add(100, -50), 100, "negative credit is refused");
        expectEq(CoinBalance.add(100, Long.MIN_VALUE), 100, "MIN_VALUE credit is refused");
        expectEq(CoinBalance.spend(100, 0), 100, "zero cost is a no-op");
        expectEq(CoinBalance.spend(100, -50), 100, "negative cost is refused");
        expectEq(CoinBalance.spend(100, Long.MIN_VALUE), 100, "MIN_VALUE cost is refused");
        // A negative stored balance must not leak out through any operation.
        expectEq(CoinBalance.add(-1, 10), 10, "negative balance sanitises to 0 before credit");
        expectEq(CoinBalance.add(-500, 10), 10, "large negative balance sanitises to 0");
        expectEq(CoinBalance.spend(-1, 10), 0, "negative balance sanitises to 0 before spend");
        expectEq(CoinBalance.add(Long.MIN_VALUE, Long.MIN_VALUE), 0, "MIN + MIN is 0, not a wrap");
        expectEq(CoinBalance.spend(Long.MIN_VALUE, 1), 0, "MIN balance cannot go negative");
    }

    // ---- 7. sanitize / isValid -------------------------------------------------

    private static void testSanitize() {
        System.out.println("[7] sanitize and isValid");
        expectEq(CoinBalance.sanitize(0), 0, "sanitize 0");
        expectEq(CoinBalance.sanitize(12345), 12345, "sanitize positive is identity");
        expectEq(CoinBalance.sanitize(Long.MAX_VALUE), Long.MAX_VALUE, "sanitize MAX is identity");
        expectEq(CoinBalance.sanitize(-1), 0, "sanitize -1");
        expectEq(CoinBalance.sanitize(Long.MIN_VALUE), 0, "sanitize MIN_VALUE");
        expect(CoinBalance.isValid(0), "0 is valid");
        expect(CoinBalance.isValid(Long.MAX_VALUE), "MAX is valid");
        expect(!CoinBalance.isValid(-1), "-1 is invalid");
        expect(!CoinBalance.isValid(Long.MIN_VALUE), "MIN_VALUE is invalid");
        expect(CoinBalance.sanitize(CoinBalance.sanitize(-99)) == 0, "sanitize is idempotent");
    }

    // ---- 8. canAdd -------------------------------------------------------------

    private static void testCanAdd() {
        System.out.println("[8] canAdd");
        expect(CoinBalance.canAdd(0, 10), "0 can take 10");
        expect(CoinBalance.canAdd(100, 50), "100 can take 50");
        expect(CoinBalance.canAdd(Long.MAX_VALUE - 10, 10), "MAX-10 can take exactly 10");
        expect(!CoinBalance.canAdd(Long.MAX_VALUE - 10, 11), "MAX-10 cannot take 11");
        expect(!CoinBalance.canAdd(Long.MAX_VALUE, 1), "MAX cannot take 1");
        expect(!CoinBalance.canAdd(Long.MAX_VALUE, Long.MAX_VALUE), "MAX cannot take MAX");
        expect(!CoinBalance.canAdd(100, 0), "zero credit is not creditable");
        expect(!CoinBalance.canAdd(100, -1), "negative credit is not creditable");
        expect(!CoinBalance.canAdd(-1, 10), "negative balance is not creditable");
        // Where canAdd is true, add must deliver the full amount.
        expectEq(CoinBalance.add(37, 11), 48, "canAdd true -> full amount lands");
    }

    // ---- 9. canSpend -----------------------------------------------------------

    private static void testCanSpend() {
        System.out.println("[9] canSpend");
        expect(CoinBalance.canSpend(100, 50), "100 can pay 50");
        expect(CoinBalance.canSpend(100, 100), "100 can pay exactly 100");
        expect(!CoinBalance.canSpend(100, 101), "100 cannot pay 101");
        expect(!CoinBalance.canSpend(0, 1), "0 cannot pay 1");
        expect(!CoinBalance.canSpend(100, 0), "zero cost is not payable");
        expect(!CoinBalance.canSpend(100, -1), "negative cost is not payable");
        expect(!CoinBalance.canSpend(-1, 1), "negative balance cannot pay");
        expectEq(CoinBalance.spend(100, 50), 50, "canSpend true -> spend lands");
        // Paying 50 twice out of 100 is legitimately affordable, so the double-purchase guard is
        // exercised with a cost that the remainder no longer covers.
        long afterFirst = CoinBalance.spend(100, 60);
        expectEq(afterFirst, 40, "first purchase of 60 leaves 40");
        expect(!CoinBalance.canSpend(afterFirst, 60), "a second identical purchase is refused");
        expectEq(CoinBalance.spend(afterFirst, 60), 40, "refused second spend leaves balance intact");
    }

    // ---- 10. Invariants across the whole model ----------------------------------

    private static void testInvariants() {
        System.out.println("[10] invariants");
        long[] balances = { 0, 1, 99, 1000, 1000000, Long.MAX_VALUE / 2, Long.MAX_VALUE - 1, Long.MAX_VALUE };
        long[] amounts = { 1, 7, 10, 1000, Long.MAX_VALUE / 3, Long.MAX_VALUE - 1, Long.MAX_VALUE };
        for (int i = 0; i < balances.length; i++) {
            for (int k = 0; k < amounts.length; k++) {
                long b = balances[i];
                long a = amounts[k];
                long got = CoinBalance.add(b, a);
                expect(got >= 0, "add never yields negative: " + b + " + " + a);
                expect(got <= Long.MAX_VALUE, "add never exceeds MAX: " + b + " + " + a);
                expect(got >= CoinBalance.sanitize(b), "credit never decreases the balance");
                expectEq(got, CoinBalance.sanitize(CoinBalance.add(got, 0)), "add is a fixed point at zero");
                long spent = CoinBalance.spend(got, a);
                expect(spent >= 0, "spend never yields negative");
                expect(spent <= got, "spend never increases the balance");
            }
        }
    }

    // ---- 11. cap ---------------------------------------------------------------

    private static void testCap() {
        System.out.println("[11] cap");
        expectEq(CoinBalance.cap(-1), 0, "cap -1");
        expectEq(CoinBalance.cap(Long.MIN_VALUE), 0, "cap MIN_VALUE");
        expectEq(CoinBalance.cap(0), 0, "cap 0");
        expectEq(CoinBalance.cap(77), 77, "cap positive is identity");
        expectEq(CoinBalance.cap(Long.MAX_VALUE), Long.MAX_VALUE, "cap MAX");
    }

    // ---- helpers ----------------------------------------------------------------

    private static void expectEq(long got, long want, String label) {
        expect(got == want, label + ": expected " + want + " got " + got);
    }

    private static void expect(boolean ok, String label) {
        checks++;
        if (!ok) {
            failures++;
            System.out.println("  FAIL: " + label);
        }
    }
}
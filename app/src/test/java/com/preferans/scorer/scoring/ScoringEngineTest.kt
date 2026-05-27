package com.preferans.scorer.scoring

import com.preferans.scorer.domain.Bid
import com.preferans.scorer.domain.GameConfig
import com.preferans.scorer.domain.Hand
import com.preferans.scorer.domain.Player
import com.preferans.scorer.domain.ScoreSheet
import com.preferans.scorer.domain.SeatId
import com.preferans.scorer.domain.Suit
import com.preferans.scorer.domain.Variant
import com.preferans.scorer.domain.WhisterRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoringEngineTest {

    private fun threePlayerConfig(variant: Variant = Variant.SOCHINKA): GameConfig =
        GameConfig(
            variant = variant,
            players = listOf(
                Player(0, "A"),
                Player(1, "B"),
                Player(2, "C"),
            ),
        )

    private fun fourPlayerConfig(variant: Variant = Variant.SOCHINKA): GameConfig =
        GameConfig(
            variant = variant,
            players = listOf(
                Player(0, "A"),
                Player(1, "B"),
                Player(2, "C"),
                Player(3, "D"),
            ),
        )

    private fun emptySheet(config: GameConfig) = ScoreSheet.empty(config.seats)

    // ── Threshold splitting ─────────────────────────────────────────────────

    @Test fun splitThreshold_4_over_2() {
        assertEquals(listOf(2, 2), ScoringEngine.splitThreshold(4, 2))
    }

    @Test fun splitThreshold_2_over_2() {
        assertEquals(listOf(1, 1), ScoringEngine.splitThreshold(2, 2))
    }

    @Test fun splitThreshold_1_over_2() {
        assertEquals(listOf(1, 0), ScoringEngine.splitThreshold(1, 2))
    }

    @Test fun splitThreshold_4_over_1() {
        assertEquals(listOf(4), ScoringEngine.splitThreshold(4, 1))
    }

    // ── Sochinka: declarer makes contract ───────────────────────────────────

    @Test fun sixSpades_made_bothWhist_thresholdExact_noPenalty() {
        // 6♠: declarer needs 6, whisters together need 4. Declarer makes 6, whisters made 4.
        val cfg = threePlayerConfig()
        val hand = Hand.Played(
            handNumber = 1,
            dealerSeat = 0,
            declarerSeat = 0,
            bid = Bid.SuitBid(6, Suit.SPADES),
            declarerTricks = 6,
            opponents = listOf(
                WhisterRecord(seat = 1, whisted = true, tricks = 2),
                WhisterRecord(seat = 2, whisted = true, tricks = 2),
            ),
        )
        val result = ScoringEngine.applyHand(emptySheet(cfg), hand, cfg)

        assertEquals(2, result.scores[0]?.bullet) // V[6]=2 bullet
        assertEquals(0, result.scores[0]?.mountain)
        assertEquals(0, result.scores[1]?.mountain)
        assertEquals(0, result.scores[2]?.mountain)
    }

    @Test fun sixSpades_made_bothWhist_oneFailedTheirShare_takesMountain() {
        // 6♠: declarer takes 7, whisters take 3 combined (2 + 1). Whister[1] failed their share of 2.
        val cfg = threePlayerConfig()
        val hand = Hand.Played(
            handNumber = 1,
            dealerSeat = 0,
            declarerSeat = 0,
            bid = Bid.SuitBid(6, Suit.SPADES),
            declarerTricks = 7,
            opponents = listOf(
                WhisterRecord(seat = 1, whisted = true, tricks = 2),
                WhisterRecord(seat = 2, whisted = true, tricks = 1),
            ),
        )
        val result = ScoringEngine.applyHand(emptySheet(cfg), hand, cfg)

        assertEquals(2, result.scores[0]?.bullet)
        // Whister[1] hit share (2). Whister[2] got 1, share 2 → 1 short → +V[6]×1 = +2 mountain
        assertEquals(0, result.scores[1]?.mountain)
        assertEquals(2, result.scores[2]?.mountain)
    }

    @Test fun sevenHearts_made_neitherWhists_autoWin() {
        // 7♥: neither opponent whisted, declarer auto-wins.
        val cfg = threePlayerConfig()
        val hand = Hand.Played(
            handNumber = 1,
            dealerSeat = 0,
            declarerSeat = 1,
            bid = Bid.SuitBid(7, Suit.HEARTS),
            declarerTricks = 10, // auto-win counted as full 10
            opponents = listOf(
                WhisterRecord(seat = 0, whisted = false, tricks = 0),
                WhisterRecord(seat = 2, whisted = false, tricks = 0),
            ),
        )
        val result = ScoringEngine.applyHand(emptySheet(cfg), hand, cfg)

        assertEquals(4, result.scores[1]?.bullet) // V[7]=4
        assertEquals(0, result.scores[1]?.mountain)
        // No mountain or whist for anyone else.
        assertEquals(0, result.scores[0]?.mountain)
        assertEquals(0, result.scores[2]?.mountain)
        assertEquals(0, result.scores[0]?.totalWhist())
        assertEquals(0, result.scores[2]?.totalWhist())
    }

    // ── Sochinka: declarer fails ────────────────────────────────────────────

    @Test fun sixSpades_failedBy1_bothWhisted_mirrorAndOvertricks() {
        // 6♠: declarer takes 5, whisters take 5 combined. Threshold 4, so 1 overtrick.
        val cfg = threePlayerConfig()
        val hand = Hand.Played(
            handNumber = 1,
            dealerSeat = 0,
            declarerSeat = 0,
            bid = Bid.SuitBid(6, Suit.SPADES),
            declarerTricks = 5,
            opponents = listOf(
                WhisterRecord(seat = 1, whisted = true, tricks = 3), // share 2, +1 over
                WhisterRecord(seat = 2, whisted = true, tricks = 2), // share 2, +0 over
            ),
        )
        val result = ScoringEngine.applyHand(emptySheet(cfg), hand, cfg)

        // V[6] = 2, declarer short by 1 → +2 mountain
        assertEquals(0, result.scores[0]?.bullet)
        assertEquals(2, result.scores[0]?.mountain)
        // Mirror whist: each opponent +V*1 = 2 whist against declarer
        // Plus overtricks for whister[1] = 1 × V = 2 more whist
        assertEquals(2 + 2, result.scores[1]?.whistAgainst?.get(0))
        assertEquals(2, result.scores[2]?.whistAgainst?.get(0))
    }

    @Test fun sevenNT_failedBy2_oneWhister_overtricks() {
        // 7NT: declarer takes 5, opponent[1] whisted alone and took 4, opponent[2] passed and took 1.
        // Threshold = 2 for whister alone. Whister took 4 → 2 overtricks.
        val cfg = threePlayerConfig()
        val hand = Hand.Played(
            handNumber = 1,
            dealerSeat = 0,
            declarerSeat = 0,
            bid = Bid.NoTrumpBid(7),
            declarerTricks = 5,
            opponents = listOf(
                WhisterRecord(seat = 1, whisted = true, tricks = 4),
                WhisterRecord(seat = 2, whisted = false, tricks = 1),
            ),
        )
        val result = ScoringEngine.applyHand(emptySheet(cfg), hand, cfg)

        // V[7]=4, declarer short by 2 → +4×2 = 8 mountain
        assertEquals(8, result.scores[0]?.mountain)
        // Mirror whist: each opponent +V×2 = 8 whist against declarer (yes, even the passer)
        // Whister: additional V × overtricks (4-2=2) = 8 more whist → total 16
        assertEquals(16, result.scores[1]?.whistAgainst?.get(0))
        // Passer just gets mirror
        assertEquals(8, result.scores[2]?.whistAgainst?.get(0))
    }

    @Test fun eightNT_failed_oneWhister_undertricks() {
        // 8NT: declarer takes 7 (fails by 1), opponent[1] whisted but took 0, opponent[2] passed and took 3.
        // Whister[1] alone needed threshold = 1. Took 0 → failed whist by 1.
        val cfg = threePlayerConfig()
        val hand = Hand.Played(
            handNumber = 1,
            dealerSeat = 0,
            declarerSeat = 0,
            bid = Bid.NoTrumpBid(8),
            declarerTricks = 7,
            opponents = listOf(
                WhisterRecord(seat = 1, whisted = true, tricks = 0),
                WhisterRecord(seat = 2, whisted = false, tricks = 3),
            ),
        )
        val result = ScoringEngine.applyHand(emptySheet(cfg), hand, cfg)

        // V[8]=6, declarer short by 1 → +6 mountain
        assertEquals(6, result.scores[0]?.mountain)
        // Mirror whist for both opponents
        assertEquals(6, result.scores[1]?.whistAgainst?.get(0))
        assertEquals(6, result.scores[2]?.whistAgainst?.get(0))
        // Whister failed their share of 1 → +V×1 = +6 mountain to themselves
        assertEquals(6, result.scores[1]?.mountain)
    }

    // ── Misère ──────────────────────────────────────────────────────────────

    @Test fun misere_success_zeroTricks_10Bullet() {
        val cfg = threePlayerConfig()
        val hand = Hand.Played(
            handNumber = 1,
            dealerSeat = 0,
            declarerSeat = 0,
            bid = Bid.Misere,
            declarerTricks = 0,
            opponents = listOf(
                WhisterRecord(seat = 1, whisted = false, tricks = 5),
                WhisterRecord(seat = 2, whisted = false, tricks = 5),
            ),
        )
        val result = ScoringEngine.applyHand(emptySheet(cfg), hand, cfg)

        assertEquals(10, result.scores[0]?.bullet)
        assertEquals(0, result.scores[0]?.mountain)
    }

    @Test fun misere_failed_oneTrick_10Mountain() {
        val cfg = threePlayerConfig()
        val hand = Hand.Played(
            handNumber = 1,
            dealerSeat = 0,
            declarerSeat = 1,
            bid = Bid.Misere,
            declarerTricks = 1,
            opponents = listOf(
                WhisterRecord(seat = 0, whisted = false, tricks = 5),
                WhisterRecord(seat = 2, whisted = false, tricks = 4),
            ),
        )
        val result = ScoringEngine.applyHand(emptySheet(cfg), hand, cfg)

        assertEquals(0, result.scores[1]?.bullet)
        assertEquals(10, result.scores[1]?.mountain)
    }

    // ── Raspasovka ──────────────────────────────────────────────────────────

    @Test fun raspasovka_threePlayer_tricksAndBulletBonus() {
        // Player 0: 0 tricks → +1 bullet. Players 1,2: 5,5 tricks → +5 mountain each.
        val cfg = threePlayerConfig()
        val hand = Hand.Raspasovka(
            handNumber = 1,
            dealerSeat = 0,
            tricksBySeat = mapOf(0 to 0, 1 to 5, 2 to 5),
        )
        val result = ScoringEngine.applyHand(emptySheet(cfg), hand, cfg)

        assertEquals(1, result.scores[0]?.bullet)
        assertEquals(0, result.scores[0]?.mountain)
        assertEquals(5, result.scores[1]?.mountain)
        assertEquals(5, result.scores[2]?.mountain)
    }

    // ── Leningradka ─────────────────────────────────────────────────────────

    @Test fun leningradka_doublesMountainAndWhist_butNotBulletDuringPlay() {
        // 6♠ failed by 1, both whist.
        val cfg = threePlayerConfig(Variant.LENINGRADKA)
        val hand = Hand.Played(
            handNumber = 1,
            dealerSeat = 0,
            declarerSeat = 0,
            bid = Bid.SuitBid(6, Suit.SPADES),
            declarerTricks = 5,
            opponents = listOf(
                WhisterRecord(seat = 1, whisted = true, tricks = 3),
                WhisterRecord(seat = 2, whisted = true, tricks = 2),
            ),
        )
        val result = ScoringEngine.applyHand(emptySheet(cfg), hand, cfg)

        // Sochinka: declarer +2 mountain; whister1 +4 whist (mirror 2 + overtricks 2); whister2 +2 whist
        // Leningradka: doubled → declarer +4 mountain; whister1 +8 whist; whister2 +4 whist
        assertEquals(4, result.scores[0]?.mountain)
        assertEquals(8, result.scores[1]?.whistAgainst?.get(0))
        assertEquals(4, result.scores[2]?.whistAgainst?.get(0))
    }

    @Test fun leningradka_bulletScoredAtBaseValueDuringPlay() {
        // 6♠ made, neither whists.
        val cfg = threePlayerConfig(Variant.LENINGRADKA)
        val hand = Hand.Played(
            handNumber = 1,
            dealerSeat = 0,
            declarerSeat = 0,
            bid = Bid.SuitBid(6, Suit.SPADES),
            declarerTricks = 10,
            opponents = listOf(
                WhisterRecord(seat = 1, whisted = false, tricks = 0),
                WhisterRecord(seat = 2, whisted = false, tricks = 0),
            ),
        )
        val result = ScoringEngine.applyHand(emptySheet(cfg), hand, cfg)

        assertEquals(2, result.scores[0]?.bullet) // bullets stay at base during play
    }

    // ── Rostov ──────────────────────────────────────────────────────────────

    @Test fun rostov_noMountain_substitutesWhistFiveToEachOpponent() {
        // 6♠ failed by 1 in Sochinka terms: declarer would owe 2 mountain.
        // In Rostov: each opponent gets 5×2 = 10 whist against declarer instead.
        val cfg = threePlayerConfig(Variant.ROSTOV)
        val hand = Hand.Played(
            handNumber = 1,
            dealerSeat = 0,
            declarerSeat = 0,
            bid = Bid.SuitBid(6, Suit.SPADES),
            declarerTricks = 5,
            opponents = listOf(
                WhisterRecord(seat = 1, whisted = true, tricks = 3),
                WhisterRecord(seat = 2, whisted = true, tricks = 2),
            ),
        )
        val result = ScoringEngine.applyHand(emptySheet(cfg), hand, cfg)

        // Declarer's mountain (Sochinka 2) → each opponent gets 10 whist
        // Plus halved normal whist:
        //   - mirror (Sochinka 2) halved → 1 each opponent
        //   - whister1 overtricks (Sochinka 2 over base mirror) halved → 1
        // So whister[1] gets: 10 (from mountain subst) + 1 (mirror halved) + 1 (overtrick halved) = 12
        // whister[2] gets: 10 (from mountain subst) + 1 (mirror halved) = 11
        assertEquals(0, result.scores[0]?.mountain) // no mountain in Rostov
        assertEquals(12, result.scores[1]?.whistAgainst?.get(0))
        assertEquals(11, result.scores[2]?.whistAgainst?.get(0))
    }

    @Test fun rostov_misereFailure_mountainBecomesWhist() {
        // Misère failed by 1 trick: Sochinka 10 mountain → Rostov 50 whist per opponent.
        val cfg = threePlayerConfig(Variant.ROSTOV)
        val hand = Hand.Played(
            handNumber = 1,
            dealerSeat = 0,
            declarerSeat = 0,
            bid = Bid.Misere,
            declarerTricks = 1,
            opponents = listOf(
                WhisterRecord(seat = 1, whisted = false, tricks = 5),
                WhisterRecord(seat = 2, whisted = false, tricks = 4),
            ),
        )
        val result = ScoringEngine.applyHand(emptySheet(cfg), hand, cfg)

        assertEquals(0, result.scores[0]?.mountain)
        assertEquals(50, result.scores[1]?.whistAgainst?.get(0))
        assertEquals(50, result.scores[2]?.whistAgainst?.get(0))
    }

    // ── Settlement ──────────────────────────────────────────────────────────

    @Test fun settle_normalizesMountainAndComputesNetWhist() {
        val cfg = threePlayerConfig()
        val sheet = ScoreSheet(
            scores = mapOf(
                0 to com.preferans.scorer.domain.PlayerScore(
                    seat = 0,
                    bullet = 10,
                    mountain = 6,
                    whistAgainst = mapOf(1 to 10, 2 to 6),
                ),
                1 to com.preferans.scorer.domain.PlayerScore(
                    seat = 1,
                    bullet = 10,
                    mountain = 12,
                    whistAgainst = mapOf(0 to 4, 2 to 8),
                ),
                2 to com.preferans.scorer.domain.PlayerScore(
                    seat = 2,
                    bullet = 10,
                    mountain = 8,
                    whistAgainst = mapOf(0 to 6, 1 to 2),
                ),
            )
        )
        val r = ScoringEngine.settle(sheet, cfg)

        // Normalize: min mountain = 6 → subtract 6: [0, 6, 2]
        assertEquals(0, r.normalizedMountain[0])
        assertEquals(6, r.normalizedMountain[1])
        assertEquals(2, r.normalizedMountain[2])

        // Each normalized * 10 / 3 to opponents.
        // Mountain[0]=0 → 0 each
        // Mountain[1]=6 → 6*10/3=20 each to opponents (0 and 2)
        // Mountain[2]=2 → 2*10/3=6 each (integer div)
        assertEquals(20, r.mountainConvertedWhist[1]?.get(0))
        assertEquals(20, r.mountainConvertedWhist[1]?.get(2))
        assertEquals(6, r.mountainConvertedWhist[2]?.get(0))
        assertEquals(6, r.mountainConvertedWhist[2]?.get(1))

        // Net whist:
        // seat 0: my whist = 10+6 + 0+0 (mountain conv from seat 0) = 16. Others against me: 4 (from 1) + 6 (from 2) + 20 (mountain from 1) + 6 (from 2) = 36. Net = 16 - 36 = -20.
        // seat 1: my whist = 4+8 + 20+20 = 52. Others against me: 10 (from 0) + 2 (from 2) + 0 (mountain from 0) + 6 (mountain from 2) = 18. Net = 52 - 18 = 34.
        // seat 2: my whist = 6+2 + 6+6 = 20. Others against me: 6 (from 0) + 8 (from 1) + 0 + 20 = 34. Net = 20 - 34 = -14.
        // Sanity: net sums to 0: -20 + 34 + -14 = 0. ✓
        assertEquals(-20, r.netWhist[0])
        assertEquals(34, r.netWhist[1])
        assertEquals(-14, r.netWhist[2])
        assertEquals(1, r.winner)
    }

    @Test fun settle_leningradkaDoublesBullets() {
        val cfg = threePlayerConfig(Variant.LENINGRADKA)
        val sheet = ScoreSheet(
            scores = mapOf(
                0 to com.preferans.scorer.domain.PlayerScore(seat = 0, bullet = 10),
                1 to com.preferans.scorer.domain.PlayerScore(seat = 1, bullet = 10),
                2 to com.preferans.scorer.domain.PlayerScore(seat = 2, bullet = 10),
            )
        )
        val r = ScoringEngine.settle(sheet, cfg)
        assertEquals(20, r.bullets[0])
        assertEquals(20, r.bullets[1])
        assertEquals(20, r.bullets[2])
    }

    // ── Edge: lone whister fails while declarer also fails ──────────────────

    @Test fun sixSpades_failed_oneWhister_whisterUndershot_bothPenalized() {
        // 6♠: declarer takes 5 (fail by 1). Opp[1] whisted alone, took 2.
        // Opp[2] passed and took 3. Whister[1] alone needed share=4, took 2 → gap=2.
        val cfg = threePlayerConfig()
        val hand = Hand.Played(
            handNumber = 1,
            dealerSeat = 0,
            declarerSeat = 0,
            bid = Bid.SuitBid(6, Suit.SPADES),
            declarerTricks = 5,
            opponents = listOf(
                WhisterRecord(seat = 1, whisted = true, tricks = 2),
                WhisterRecord(seat = 2, whisted = false, tricks = 3),
            ),
        )
        val result = ScoringEngine.applyHand(emptySheet(cfg), hand, cfg)

        // V[6]=2, short=1 → declarer +2 mountain
        assertEquals(2, result.scores[0]?.mountain)
        // Mirror: each opponent +V*1 = 2 whist against declarer (literal "each opponent")
        assertEquals(2, result.scores[1]?.whistAgainst?.get(0))
        assertEquals(2, result.scores[2]?.whistAgainst?.get(0))
        // Lone whister took 2 vs share 4 → gap 2 → +V*2 = +4 mountain
        assertEquals(4, result.scores[1]?.mountain)
    }

    // ── 4-player Rostov: dealer must not leak ──────────────────────────────

    @Test fun rostov_fourPlayer_dealerExcludedFromMountainSubstitute() {
        // 4-player, dealer=0 sits out. Declarer=1 fails 6♠ by 1.
        // In Sochinka: declarer +2 mountain. In Rostov: only the two active
        // opponents (seats 2 and 3) should receive 5*2=10 whist; dealer (seat 0)
        // should NOT receive any whist from the mountain substitute.
        val cfg = fourPlayerConfig(Variant.ROSTOV)
        val hand = Hand.Played(
            handNumber = 1,
            dealerSeat = 0,
            declarerSeat = 1,
            bid = Bid.SuitBid(6, Suit.SPADES),
            declarerTricks = 5,
            opponents = listOf(
                WhisterRecord(seat = 2, whisted = true, tricks = 3),
                WhisterRecord(seat = 3, whisted = true, tricks = 2),
            ),
        )
        val result = ScoringEngine.applyHand(emptySheet(cfg), hand, cfg)

        assertEquals(0, result.scores[1]?.mountain) // mountain stays 0 in Rostov
        // Dealer (seat 0) should have no whist against declarer (seat 1)
        assertEquals(null, result.scores[0]?.whistAgainst?.get(1))
        // Active opponents both got mountain-substitute whist
        val o2 = result.scores[2]?.whistAgainst?.get(1) ?: 0
        val o3 = result.scores[3]?.whistAgainst?.get(1) ?: 0
        assertTrue("seat 2 should have mountain-substitute whist", o2 >= 10)
        assertTrue("seat 3 should have mountain-substitute whist", o3 >= 10)
    }

    // ── 4-player smoke test ─────────────────────────────────────────────────

    @Test fun fourPlayer_dealerSitsOut_scoringDoesntTouchDealer() {
        val cfg = fourPlayerConfig()
        // Dealer is seat 0; declarer + 2 opponents are seats 1,2,3
        val hand = Hand.Played(
            handNumber = 1,
            dealerSeat = 0,
            declarerSeat = 1,
            bid = Bid.SuitBid(6, Suit.SPADES),
            declarerTricks = 6,
            opponents = listOf(
                WhisterRecord(seat = 2, whisted = true, tricks = 2),
                WhisterRecord(seat = 3, whisted = true, tricks = 2),
            ),
        )
        val result = ScoringEngine.applyHand(emptySheet(cfg), hand, cfg)

        // Declarer gets bullets; dealer (seat 0) untouched.
        assertEquals(2, result.scores[1]?.bullet)
        assertEquals(0, result.scores[0]?.bullet)
        assertEquals(0, result.scores[0]?.mountain)
        assertEquals(0, result.scores[0]?.totalWhist())
    }

    @Test fun fourPlayer_talonBonusGoesToDealer() {
        val cfg = fourPlayerConfig()
        val hand = Hand.Played(
            handNumber = 1,
            dealerSeat = 0,
            declarerSeat = 1,
            bid = Bid.SuitBid(6, Suit.SPADES),
            declarerTricks = 6,
            opponents = listOf(
                WhisterRecord(seat = 2, whisted = true, tricks = 2),
                WhisterRecord(seat = 3, whisted = true, tricks = 2),
            ),
            talonWhistBonus = 3, // e.g., two aces in talon
        )
        val result = ScoringEngine.applyHand(emptySheet(cfg), hand, cfg)

        assertEquals(3, result.scores[0]?.whistAgainst?.get(1))
    }

    // ── Bid comparison ──────────────────────────────────────────────────────

    @Test fun bidOrdering_levelsAndSuitsAndMisere() {
        val sixS = Bid.SuitBid(6, Suit.SPADES)
        val sixNT = Bid.NoTrumpBid(6)
        val sevenS = Bid.SuitBid(7, Suit.SPADES)
        val eightNT = Bid.NoTrumpBid(8)
        val nineS = Bid.SuitBid(9, Suit.SPADES)

        assertTrue(sixS < sixNT)
        assertTrue(sixNT < sevenS)
        assertTrue(eightNT < Bid.Misere)
        assertTrue(Bid.Misere < nineS)
    }
}

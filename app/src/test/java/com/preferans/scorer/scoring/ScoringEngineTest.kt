package com.preferans.scorer.scoring

import com.preferans.scorer.domain.Bid
import com.preferans.scorer.domain.GameConfig
import com.preferans.scorer.domain.Hand
import com.preferans.scorer.domain.Player
import com.preferans.scorer.domain.ScoreSheet
import com.preferans.scorer.domain.SeatId
import com.preferans.scorer.domain.Suit
import com.preferans.scorer.domain.Variant
import com.preferans.scorer.domain.WhistChoice
import com.preferans.scorer.domain.WhistScoringRule
import com.preferans.scorer.domain.WhistSharing
import com.preferans.scorer.domain.WhistTrickPooling
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
                WhisterRecord(seat = 1, choice = WhistChoice.WHIST, tricks = 2),
                WhisterRecord(seat = 2, choice = WhistChoice.WHIST, tricks = 2),
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
                WhisterRecord(seat = 1, choice = WhistChoice.WHIST, tricks = 2),
                WhisterRecord(seat = 2, choice = WhistChoice.WHIST, tricks = 1),
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
                WhisterRecord(seat = 0, choice = WhistChoice.PASS, tricks = 0),
                WhisterRecord(seat = 2, choice = WhistChoice.PASS, tricks = 0),
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

    @Test fun sixSpades_failedBy1_bothWhisted_mirrorAndFlatBonus() {
        // 6♠: declarer takes 5, both whisters take 5 combined (3 + 2).
        val cfg = threePlayerConfig()
        val hand = Hand.Played(
            handNumber = 1,
            dealerSeat = 0,
            declarerSeat = 0,
            bid = Bid.SuitBid(6, Suit.SPADES),
            declarerTricks = 5,
            opponents = listOf(
                WhisterRecord(seat = 1, choice = WhistChoice.WHIST, tricks = 3),
                WhisterRecord(seat = 2, choice = WhistChoice.WHIST, tricks = 2),
            ),
        )
        val result = ScoringEngine.applyHand(emptySheet(cfg), hand, cfg)

        // V[6] = 2, declarer short by 1 → +2 mountain.
        assertEquals(0, result.scores[0]?.bullet)
        assertEquals(2, result.scores[0]?.mountain)
        // Mirror whist V*1 = 2 to each opponent + V (=2) split among 2 whisters = 1 each.
        // Each whister: 2 + 1 = 3.
        assertEquals(3, result.scores[1]?.whistAgainst?.get(0))
        assertEquals(3, result.scores[2]?.whistAgainst?.get(0))
    }

    @Test fun sevenNT_failedBy2_oneWhister_aloneBonus() {
        // 7NT: declarer takes 5, opp[1] whisted alone and took 4, opp[2] passed and took 1.
        val cfg = threePlayerConfig()
        val hand = Hand.Played(
            handNumber = 1,
            dealerSeat = 0,
            declarerSeat = 0,
            bid = Bid.NoTrumpBid(7),
            declarerTricks = 5,
            opponents = listOf(
                WhisterRecord(seat = 1, choice = WhistChoice.WHIST, tricks = 4),
                WhisterRecord(seat = 2, choice = WhistChoice.PASS, tricks = 1),
            ),
        )
        val result = ScoringEngine.applyHand(emptySheet(cfg), hand, cfg)

        // V[7]=4, declarer short by 2 → +4×2 = 8 mountain
        assertEquals(8, result.scores[0]?.mountain)
        // Mirror V*2 = 8 to each opponent. Lone whister gets full V=4 bonus.
        // Whister total: 8 + 4 = 12. Passer just gets mirror = 8.
        assertEquals(12, result.scores[1]?.whistAgainst?.get(0))
        assertEquals(8, result.scores[2]?.whistAgainst?.get(0))
    }

    @Test fun eightNT_failed_oneWhister_undertricks() {
        // 8NT: declarer takes 7 (fails by 1), opp[1] whisted but took 0, opp[2] passed took 3.
        val cfg = threePlayerConfig()
        val hand = Hand.Played(
            handNumber = 1,
            dealerSeat = 0,
            declarerSeat = 0,
            bid = Bid.NoTrumpBid(8),
            declarerTricks = 7,
            opponents = listOf(
                WhisterRecord(seat = 1, choice = WhistChoice.WHIST, tricks = 0),
                WhisterRecord(seat = 2, choice = WhistChoice.PASS, tricks = 3),
            ),
        )
        val result = ScoringEngine.applyHand(emptySheet(cfg), hand, cfg)

        // V[8]=6, declarer short by 1 → +6 mountain
        assertEquals(6, result.scores[0]?.mountain)
        // Mirror = V*1=6 to each opp. Lone whister bonus = V=6.
        // Whister total whist = 6 + 6 = 12. Passer = 6.
        assertEquals(12, result.scores[1]?.whistAgainst?.get(0))
        assertEquals(6, result.scores[2]?.whistAgainst?.get(0))
        // Whister failed their share of 1 → +V*1 = +6 mountain to themselves
        assertEquals(6, result.scores[1]?.mountain)
    }

    // ── Half-whist ──────────────────────────────────────────────────────────

    @Test fun halfWhist_sixSpades_endsWithoutPlay_declarerMakes_halfWhisterScoresFour() {
        // 6♠: opp[1] half-whisted, opp[2] passed. Hand ends; declarer credited as
        // having made the contract. Half-whister gets V × (T/2) = 2 × 2 = 4 whist.
        // We supply synthesized tricks (8/2/0) so Hand.Played.init is satisfied.
        val cfg = threePlayerConfig()
        val hand = Hand.Played(
            handNumber = 1,
            dealerSeat = 0,
            declarerSeat = 0,
            bid = Bid.SuitBid(6, Suit.SPADES),
            declarerTricks = 8, // L + T/2 = 6 + 2 = 8
            opponents = listOf(
                WhisterRecord(seat = 1, choice = WhistChoice.HALF_WHIST, tricks = 2),
                WhisterRecord(seat = 2, choice = WhistChoice.PASS, tricks = 0),
            ),
        )
        val result = ScoringEngine.applyHand(emptySheet(cfg), hand, cfg)

        assertEquals(2, result.scores[0]?.bullet) // V[6] bullet for declarer
        assertEquals(0, result.scores[0]?.mountain)
        assertEquals(4, result.scores[1]?.whistAgainst?.get(0))
        // Passer: no whist credit
        assertEquals(null, result.scores[2]?.whistAgainst?.get(0))
        // Half-whister: no mountain (no failed-share path taken in half-whist branch)
        assertEquals(0, result.scores[1]?.mountain)
    }

    @Test fun halfWhist_sevenHearts_halfWhisterScoresFour() {
        // 7♥: V=4, threshold=2 → half-whister gets V × (T/2) = 4 × 1 = 4 whist.
        // Synthesised tricks: declarer=10-1=9, half-whister=1, passer=0 (sum=10).
        val cfg = threePlayerConfig()
        val hand = Hand.Played(
            handNumber = 1,
            dealerSeat = 0,
            declarerSeat = 0,
            bid = Bid.SuitBid(7, Suit.HEARTS),
            declarerTricks = 9,
            opponents = listOf(
                WhisterRecord(seat = 1, choice = WhistChoice.HALF_WHIST, tricks = 1),
                WhisterRecord(seat = 2, choice = WhistChoice.PASS, tricks = 0),
            ),
        )
        val result = ScoringEngine.applyHand(emptySheet(cfg), hand, cfg)

        assertEquals(4, result.scores[0]?.bullet) // V[7]
        assertEquals(4, result.scores[1]?.whistAgainst?.get(0))
    }

    @Test fun halfWhist_rostov_appliesHalving() {
        // 6♠ half-whist in Rostov. Sochinka: half-whister gets 4 whist.
        // Rostov halves → 4 / 2 = 2 whist.
        val cfg = threePlayerConfig(Variant.ROSTOV)
        val hand = Hand.Played(
            handNumber = 1,
            dealerSeat = 0,
            declarerSeat = 0,
            bid = Bid.SuitBid(6, Suit.SPADES),
            declarerTricks = 8,
            opponents = listOf(
                WhisterRecord(seat = 1, choice = WhistChoice.HALF_WHIST, tricks = 2),
                WhisterRecord(seat = 2, choice = WhistChoice.PASS, tricks = 0),
            ),
        )
        val result = ScoringEngine.applyHand(emptySheet(cfg), hand, cfg)
        assertEquals(2, result.scores[0]?.bullet)
        assertEquals(2, result.scores[1]?.whistAgainst?.get(0))
    }

    // ── Whist scoring rule: TRICKS_TAKEN on a made contract ─────────────────

    @Test fun madeContract_failureOnlyRule_whisterScoresNothing() {
        // 8♣ made: declarer 8, whister took 2 (the user's scenario). Under the
        // default FAILURE_ONLY (catsatcards) rule, the whister scores nothing.
        val cfg = threePlayerConfig() // default FAILURE_ONLY
        val hand = Hand.Played(
            handNumber = 1,
            dealerSeat = 0,
            declarerSeat = 0,
            bid = Bid.SuitBid(8, Suit.CLUBS),
            declarerTricks = 8,
            opponents = listOf(
                WhisterRecord(seat = 1, choice = WhistChoice.WHIST, tricks = 2),
                WhisterRecord(seat = 2, choice = WhistChoice.PASS, tricks = 0),
            ),
        )
        val result = ScoringEngine.applyHand(emptySheet(cfg), hand, cfg)

        assertEquals(6, result.scores[0]?.bullet) // V[8] bullet for declarer
        assertEquals(null, result.scores[1]?.whistAgainst?.get(0)) // whister: nothing
        assertEquals(0, result.scores[1]?.mountain) // took 2 ≥ threshold 1 → no penalty
    }

    @Test fun madeContract_tricksTakenRule_whisterScoresPerTrick() {
        // Same 8♣ made, whister took 2 — but with TRICKS_TAKEN the whister
        // records V[8] × 2 = 12 whist against the declarer.
        val cfg = threePlayerConfig().copy(whistScoringRule = WhistScoringRule.TRICKS_TAKEN)
        val hand = Hand.Played(
            handNumber = 1,
            dealerSeat = 0,
            declarerSeat = 0,
            bid = Bid.SuitBid(8, Suit.CLUBS),
            declarerTricks = 8,
            opponents = listOf(
                WhisterRecord(seat = 1, choice = WhistChoice.WHIST, tricks = 2),
                WhisterRecord(seat = 2, choice = WhistChoice.PASS, tricks = 0),
            ),
        )
        val result = ScoringEngine.applyHand(emptySheet(cfg), hand, cfg)

        assertEquals(6, result.scores[0]?.bullet)
        assertEquals(12, result.scores[1]?.whistAgainst?.get(0)) // V*2
        // Passer (didn't whist) scores nothing even under TRICKS_TAKEN.
        assertEquals(null, result.scores[2]?.whistAgainst?.get(0))
    }

    @Test fun madeContract_tricksTakenRule_failureCaseUnchanged() {
        // TRICKS_TAKEN must NOT change the failure case — still mirror + bonus.
        val cfg = threePlayerConfig().copy(whistScoringRule = WhistScoringRule.TRICKS_TAKEN)
        val hand = Hand.Played(
            handNumber = 1,
            dealerSeat = 0,
            declarerSeat = 0,
            bid = Bid.SuitBid(6, Suit.SPADES),
            declarerTricks = 5,
            opponents = listOf(
                WhisterRecord(seat = 1, choice = WhistChoice.WHIST, tricks = 3),
                WhisterRecord(seat = 2, choice = WhistChoice.WHIST, tricks = 2),
            ),
        )
        val result = ScoringEngine.applyHand(emptySheet(cfg), hand, cfg)

        // Identical to the FAILURE_ONLY failure result: declarer +2 mountain,
        // each whister +3 (mirror 2 + bonus 1).
        assertEquals(2, result.scores[0]?.mountain)
        assertEquals(3, result.scores[1]?.whistAgainst?.get(0))
        assertEquals(3, result.scores[2]?.whistAgainst?.get(0))
    }

    // ── Whist trick pooling (lone whister + passer) ─────────────────────────

    @Test fun pooling_loneWhister_made_creditsPasserTricks() {
        // 6♠ made (declarer 6). Seat 1 whists and took 1, seat 2 passes and took 3.
        // POOLED + TRICKS_TAKEN: whister credited for all 4 defensive tricks
        // → V[6] × 4 = 8 whist, and no failed-whist penalty (4 ≥ threshold 4).
        val cfg = threePlayerConfig().copy(
            whistScoringRule = WhistScoringRule.TRICKS_TAKEN,
            whistTrickPooling = WhistTrickPooling.POOLED,
        )
        val hand = Hand.Played(
            handNumber = 1,
            dealerSeat = 0,
            declarerSeat = 0,
            bid = Bid.SuitBid(6, Suit.SPADES),
            declarerTricks = 6,
            opponents = listOf(
                WhisterRecord(seat = 1, choice = WhistChoice.WHIST, tricks = 1),
                WhisterRecord(seat = 2, choice = WhistChoice.PASS, tricks = 3),
            ),
        )
        val result = ScoringEngine.applyHand(emptySheet(cfg), hand, cfg)

        assertEquals(2, result.scores[0]?.bullet)
        assertEquals(8, result.scores[1]?.whistAgainst?.get(0)) // V*4
        assertEquals(0, result.scores[1]?.mountain) // pooled total met threshold
        assertEquals(null, result.scores[2]?.whistAgainst?.get(0)) // passer scores nothing
    }

    @Test fun pooling_off_loneWhister_made_individualTricksAndPenalty() {
        // Same hand, INDIVIDUAL: whister credited only for their own 1 trick
        // → V*1 = 2 whist, AND penalised for falling short of threshold 4
        // → V × (4-1) = 6 mountain.
        val cfg = threePlayerConfig().copy(
            whistScoringRule = WhistScoringRule.TRICKS_TAKEN,
            whistTrickPooling = WhistTrickPooling.INDIVIDUAL,
        )
        val hand = Hand.Played(
            handNumber = 1,
            dealerSeat = 0,
            declarerSeat = 0,
            bid = Bid.SuitBid(6, Suit.SPADES),
            declarerTricks = 6,
            opponents = listOf(
                WhisterRecord(seat = 1, choice = WhistChoice.WHIST, tricks = 1),
                WhisterRecord(seat = 2, choice = WhistChoice.PASS, tricks = 3),
            ),
        )
        val result = ScoringEngine.applyHand(emptySheet(cfg), hand, cfg)

        assertEquals(2, result.scores[1]?.whistAgainst?.get(0)) // V*1
        assertEquals(6, result.scores[1]?.mountain) // V*(4-1)
    }

    @Test fun pooling_noEffect_whenBothWhist() {
        // 6♠ made +1 (declarer 7). Both whist: shares [2,2]. Seat 1 took 2 (ok),
        // seat 2 took 1 (gap 1). With two whisters there's no passer, so POOLED
        // changes nothing: seat 2 still penalised V × 1 = 2; seat 1 not.
        val cfg = threePlayerConfig().copy(whistTrickPooling = WhistTrickPooling.POOLED)
        val hand = Hand.Played(
            handNumber = 1,
            dealerSeat = 0,
            declarerSeat = 0,
            bid = Bid.SuitBid(6, Suit.SPADES),
            declarerTricks = 7,
            opponents = listOf(
                WhisterRecord(seat = 1, choice = WhistChoice.WHIST, tricks = 2),
                WhisterRecord(seat = 2, choice = WhistChoice.WHIST, tricks = 1),
            ),
        )
        val result = ScoringEngine.applyHand(emptySheet(cfg), hand, cfg)

        assertEquals(0, result.scores[1]?.mountain)
        assertEquals(2, result.scores[2]?.mountain)
    }

    // ── Whist reward sharing (greedy vs gentleman's) ────────────────────────

    @Test fun gentlemans_made_sharesRewardEvenly() {
        // 8♣ made, lone whister took 2 (individual pooling). Reward = V[8]×2 = 12.
        // Gentleman's splits it: whister 6, passer 6.
        val cfg = threePlayerConfig().copy(
            whistScoringRule = WhistScoringRule.TRICKS_TAKEN,
            whistTrickPooling = WhistTrickPooling.INDIVIDUAL,
            whistSharing = WhistSharing.GENTLEMANS,
        )
        val hand = Hand.Played(
            handNumber = 1,
            dealerSeat = 0,
            declarerSeat = 0,
            bid = Bid.SuitBid(8, Suit.CLUBS),
            declarerTricks = 8,
            opponents = listOf(
                WhisterRecord(seat = 1, choice = WhistChoice.WHIST, tricks = 2),
                WhisterRecord(seat = 2, choice = WhistChoice.PASS, tricks = 0),
            ),
        )
        val result = ScoringEngine.applyHand(emptySheet(cfg), hand, cfg)

        assertEquals(6, result.scores[1]?.whistAgainst?.get(0)) // whister half
        assertEquals(6, result.scores[2]?.whistAgainst?.get(0)) // passer half
        assertEquals(0, result.scores[1]?.mountain)
    }

    @Test fun gentlemans_made_pooledRewardShared() {
        // 6♠ made, whister took 1, passer took 3, POOLED + gentleman's.
        // Pooled reward = V[6] × 4 = 8 → split 4/4.
        val cfg = threePlayerConfig().copy(
            whistScoringRule = WhistScoringRule.TRICKS_TAKEN,
            whistTrickPooling = WhistTrickPooling.POOLED,
            whistSharing = WhistSharing.GENTLEMANS,
        )
        val hand = Hand.Played(
            handNumber = 1,
            dealerSeat = 0,
            declarerSeat = 0,
            bid = Bid.SuitBid(6, Suit.SPADES),
            declarerTricks = 6,
            opponents = listOf(
                WhisterRecord(seat = 1, choice = WhistChoice.WHIST, tricks = 1),
                WhisterRecord(seat = 2, choice = WhistChoice.PASS, tricks = 3),
            ),
        )
        val result = ScoringEngine.applyHand(emptySheet(cfg), hand, cfg)

        assertEquals(4, result.scores[1]?.whistAgainst?.get(0))
        assertEquals(4, result.scores[2]?.whistAgainst?.get(0))
    }

    @Test fun gentlemans_failed_sharesBonusButNotMirror() {
        // 6♠ failed by 1, lone whister took 3, passer took 2. Gentleman's.
        // Mirror (V×1=2) goes to EACH opponent individually (not shared).
        // Bonus (V=2) is the whister's reward → split 1/1.
        val cfg = threePlayerConfig().copy(whistSharing = WhistSharing.GENTLEMANS)
        val hand = Hand.Played(
            handNumber = 1,
            dealerSeat = 0,
            declarerSeat = 0,
            bid = Bid.SuitBid(6, Suit.SPADES),
            declarerTricks = 5,
            opponents = listOf(
                WhisterRecord(seat = 1, choice = WhistChoice.WHIST, tricks = 3),
                WhisterRecord(seat = 2, choice = WhistChoice.PASS, tricks = 2),
            ),
        )
        val result = ScoringEngine.applyHand(emptySheet(cfg), hand, cfg)

        assertEquals(2, result.scores[0]?.mountain) // declarer V×short
        // Whister: mirror 2 + bonus-share 1 = 3. Passer: mirror 2 + bonus-share 1 = 3.
        assertEquals(3, result.scores[1]?.whistAgainst?.get(0))
        assertEquals(3, result.scores[2]?.whistAgainst?.get(0))
        // Whister still personally penalised for the threshold gap (share 4, took 3).
        assertEquals(2, result.scores[1]?.mountain)
        assertEquals(0, result.scores[2]?.mountain) // passer not a whister → no penalty
    }

    @Test fun greedy_made_whisterKeepsWholeReward() {
        // Same as gentlemans_made_sharesRewardEvenly but GREEDY: whister keeps 12.
        val cfg = threePlayerConfig().copy(
            whistScoringRule = WhistScoringRule.TRICKS_TAKEN,
            whistTrickPooling = WhistTrickPooling.INDIVIDUAL,
            whistSharing = WhistSharing.GREEDY,
        )
        val hand = Hand.Played(
            handNumber = 1,
            dealerSeat = 0,
            declarerSeat = 0,
            bid = Bid.SuitBid(8, Suit.CLUBS),
            declarerTricks = 8,
            opponents = listOf(
                WhisterRecord(seat = 1, choice = WhistChoice.WHIST, tricks = 2),
                WhisterRecord(seat = 2, choice = WhistChoice.PASS, tricks = 0),
            ),
        )
        val result = ScoringEngine.applyHand(emptySheet(cfg), hand, cfg)

        assertEquals(12, result.scores[1]?.whistAgainst?.get(0))
        assertEquals(null, result.scores[2]?.whistAgainst?.get(0))
    }

    @Test fun gentlemans_noEffect_whenBothWhist() {
        // Both whist → no passer → sharing is a no-op. 6♠ failed by 1,
        // each whister gets mirror 2 + bonus (V/2=1) = 3, same as greedy.
        val cfg = threePlayerConfig().copy(whistSharing = WhistSharing.GENTLEMANS)
        val hand = Hand.Played(
            handNumber = 1,
            dealerSeat = 0,
            declarerSeat = 0,
            bid = Bid.SuitBid(6, Suit.SPADES),
            declarerTricks = 5,
            opponents = listOf(
                WhisterRecord(seat = 1, choice = WhistChoice.WHIST, tricks = 3),
                WhisterRecord(seat = 2, choice = WhistChoice.WHIST, tricks = 2),
            ),
        )
        val result = ScoringEngine.applyHand(emptySheet(cfg), hand, cfg)

        assertEquals(3, result.scores[1]?.whistAgainst?.get(0))
        assertEquals(3, result.scores[2]?.whistAgainst?.get(0))
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
                WhisterRecord(seat = 1, choice = WhistChoice.PASS, tricks = 5),
                WhisterRecord(seat = 2, choice = WhistChoice.PASS, tricks = 5),
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
                WhisterRecord(seat = 0, choice = WhistChoice.PASS, tricks = 5),
                WhisterRecord(seat = 2, choice = WhistChoice.PASS, tricks = 4),
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
                WhisterRecord(seat = 1, choice = WhistChoice.WHIST, tricks = 3),
                WhisterRecord(seat = 2, choice = WhistChoice.WHIST, tricks = 2),
            ),
        )
        val result = ScoringEngine.applyHand(emptySheet(cfg), hand, cfg)

        // Sochinka base: declarer +2 mountain; each whister +3 whist (2 mirror + 1 bonus).
        // Leningradka doubles: declarer +4 mountain; each whister +6 whist.
        assertEquals(4, result.scores[0]?.mountain)
        assertEquals(6, result.scores[1]?.whistAgainst?.get(0))
        assertEquals(6, result.scores[2]?.whistAgainst?.get(0))
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
                WhisterRecord(seat = 1, choice = WhistChoice.PASS, tricks = 0),
                WhisterRecord(seat = 2, choice = WhistChoice.PASS, tricks = 0),
            ),
        )
        val result = ScoringEngine.applyHand(emptySheet(cfg), hand, cfg)

        assertEquals(2, result.scores[0]?.bullet) // bullets stay at base during play
    }

    // ── Rostov ──────────────────────────────────────────────────────────────

    @Test fun rostov_noMountain_substitutesWhistFiveToEachOpponent() {
        // 6♠ failed by 1. Sochinka: declarer +2 mountain; each whister +3 whist.
        val cfg = threePlayerConfig(Variant.ROSTOV)
        val hand = Hand.Played(
            handNumber = 1,
            dealerSeat = 0,
            declarerSeat = 0,
            bid = Bid.SuitBid(6, Suit.SPADES),
            declarerTricks = 5,
            opponents = listOf(
                WhisterRecord(seat = 1, choice = WhistChoice.WHIST, tricks = 3),
                WhisterRecord(seat = 2, choice = WhistChoice.WHIST, tricks = 2),
            ),
        )
        val result = ScoringEngine.applyHand(emptySheet(cfg), hand, cfg)

        // Declarer mountain (2) → 5*2=10 whist for each opponent against declarer.
        // Other whist (3 each) halved with integer division → 1 each.
        // Per opponent: 10 (mountain subst) + 1 (halved normal) = 11.
        assertEquals(0, result.scores[0]?.mountain) // mountain stays 0 in Rostov
        assertEquals(11, result.scores[1]?.whistAgainst?.get(0))
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
                WhisterRecord(seat = 1, choice = WhistChoice.PASS, tricks = 5),
                WhisterRecord(seat = 2, choice = WhistChoice.PASS, tricks = 4),
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

        // mountainConvertedWhist[earner][target] = normMountain[target] * 10 / N.
        // i.e., target's mountain becomes whist credited to each earner *against* target.
        // Seat 1 has normalized mountain 6 → 60/3=20 to each opponent against seat 1.
        assertEquals(20, r.mountainConvertedWhist[0]?.get(1))
        assertEquals(20, r.mountainConvertedWhist[2]?.get(1))
        // Seat 2 has normalized mountain 2 → 20/3=6 (int div) against seat 2.
        assertEquals(6, r.mountainConvertedWhist[0]?.get(2))
        assertEquals(6, r.mountainConvertedWhist[1]?.get(2))
        // Seat 0 has 0 → 0 against seat 0.
        assertEquals(0, r.mountainConvertedWhist[1]?.get(0))
        assertEquals(0, r.mountainConvertedWhist[2]?.get(0))

        // Net whist = (my normal whist + my mountain-conv whist) − (others against me, both kinds).
        // seat 0: (10+6 + 20+6) − (4+6 + 0+0) = 42 − 10 = 32
        // seat 1: (4+8  + 0+6)  − (10+2 + 20+20) = 18 − 52 = −34
        // seat 2: (6+2  + 0+20) − (6+8  + 6+6)  = 28 − 26 = 2
        // Sanity: 32 + (−34) + 2 = 0 ✓
        assertEquals(32, r.netWhist[0])
        assertEquals(-34, r.netWhist[1])
        assertEquals(2, r.netWhist[2])
        assertEquals(0, r.winner)
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
        // 6♠: declarer 5 (fail by 1). Opp[1] whisted alone, took 2. Opp[2] passed, took 3.
        val cfg = threePlayerConfig()
        val hand = Hand.Played(
            handNumber = 1,
            dealerSeat = 0,
            declarerSeat = 0,
            bid = Bid.SuitBid(6, Suit.SPADES),
            declarerTricks = 5,
            opponents = listOf(
                WhisterRecord(seat = 1, choice = WhistChoice.WHIST, tricks = 2),
                WhisterRecord(seat = 2, choice = WhistChoice.PASS, tricks = 3),
            ),
        )
        val result = ScoringEngine.applyHand(emptySheet(cfg), hand, cfg)

        // V[6]=2, short=1 → declarer +2 mountain.
        assertEquals(2, result.scores[0]?.mountain)
        // Mirror V*1=2 to each opp. Lone whister gets full V=2 bonus.
        // Whister: 2 + 2 = 4 whist. Passer: just mirror = 2.
        assertEquals(4, result.scores[1]?.whistAgainst?.get(0))
        assertEquals(2, result.scores[2]?.whistAgainst?.get(0))
        // Lone whister took 2 vs share 4 → gap 2 → +V*2 = +4 mountain.
        assertEquals(4, result.scores[1]?.mountain)
    }

    // ── Level 10 is always played (no auto-win) ─────────────────────────────

    @Test fun level10_noWhisters_isStillPlayed_notAutoWon() {
        // 10♠: declarer takes 8 — fails by 2. With no whisters, prior code would have
        // treated this as auto-win (+10 bullet). Now it must be played as failure.
        val cfg = threePlayerConfig()
        val hand = Hand.Played(
            handNumber = 1,
            dealerSeat = 0,
            declarerSeat = 0,
            bid = Bid.SuitBid(10, Suit.SPADES),
            declarerTricks = 8,
            opponents = listOf(
                WhisterRecord(seat = 1, choice = WhistChoice.PASS, tricks = 1),
                WhisterRecord(seat = 2, choice = WhistChoice.PASS, tricks = 1),
            ),
        )
        val result = ScoringEngine.applyHand(emptySheet(cfg), hand, cfg)

        // V[10]=10, short=2 → declarer +20 mountain.
        assertEquals(0, result.scores[0]?.bullet)
        assertEquals(20, result.scores[0]?.mountain)
        // Mirror whist V*2=20 to each opp; no whisters → no bonus.
        assertEquals(20, result.scores[1]?.whistAgainst?.get(0))
        assertEquals(20, result.scores[2]?.whistAgainst?.get(0))
    }

    @Test fun level10_madeWithFullTricks_bulletEarned() {
        val cfg = threePlayerConfig()
        val hand = Hand.Played(
            handNumber = 1,
            dealerSeat = 0,
            declarerSeat = 0,
            bid = Bid.SuitBid(10, Suit.SPADES),
            declarerTricks = 10,
            opponents = listOf(
                WhisterRecord(seat = 1, choice = WhistChoice.PASS, tricks = 0),
                WhisterRecord(seat = 2, choice = WhistChoice.PASS, tricks = 0),
            ),
        )
        val result = ScoringEngine.applyHand(emptySheet(cfg), hand, cfg)
        assertEquals(10, result.scores[0]?.bullet)
        assertEquals(0, result.scores[0]?.mountain)
    }

    // ── American Aid ────────────────────────────────────────────────────────

    @Test fun americanAid_distributesOvershootAndAppliesWhistMountain() {
        // Construct a sheet where seat 0 overshoots by 2; seat 1 and seat 2 are 2 below target.
        val cfg = threePlayerConfig().copy(bulletTarget = 10)
        val initial = ScoreSheet(
            scores = mapOf(
                0 to com.preferans.scorer.domain.PlayerScore(seat = 0, bullet = 12),
                1 to com.preferans.scorer.domain.PlayerScore(seat = 1, bullet = 8),
                2 to com.preferans.scorer.domain.PlayerScore(seat = 2, bullet = 8),
            )
        )
        val out = ScoringEngine.applyAmericanAid(initial, cfg)

        // Excess of 2 → one bullet to each of seats 1 and 2 (spread to neediest first;
        // ties break by seat order, then seat 2 has lower bullets after seat 1 receives).
        assertEquals(10, out.scores[0]?.bullet)
        assertEquals(9, out.scores[1]?.bullet)
        assertEquals(9, out.scores[2]?.bullet)
        // Giver (seat 0): +10 whist against each recipient (10 per bullet transferred).
        assertEquals(10, out.scores[0]?.whistAgainst?.get(1))
        assertEquals(10, out.scores[0]?.whistAgainst?.get(2))
        // Recipients: +10 mountain each.
        assertEquals(10, out.scores[1]?.mountain)
        assertEquals(10, out.scores[2]?.mountain)
    }

    @Test fun americanAid_noOp_whenNoOneOvershoots() {
        val cfg = threePlayerConfig()
        val initial = ScoreSheet(
            scores = mapOf(
                0 to com.preferans.scorer.domain.PlayerScore(seat = 0, bullet = 5),
                1 to com.preferans.scorer.domain.PlayerScore(seat = 1, bullet = 7),
                2 to com.preferans.scorer.domain.PlayerScore(seat = 2, bullet = 6),
            )
        )
        val out = ScoringEngine.applyAmericanAid(initial, cfg)
        assertEquals(5, out.scores[0]?.bullet)
        assertEquals(7, out.scores[1]?.bullet)
        assertEquals(6, out.scores[2]?.bullet)
        assertEquals(0, out.scores[0]?.mountain)
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
                WhisterRecord(seat = 2, choice = WhistChoice.WHIST, tricks = 3),
                WhisterRecord(seat = 3, choice = WhistChoice.WHIST, tricks = 2),
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

    // ── 4-player raspasovka dealer bonus ────────────────────────────────────

    @Test fun raspasovka_fourPlayer_dealerZeroTricks_gets10Bullets() {
        // Dealer (seat 0) plays widow, wins 0 tricks → +10 bullets.
        // Other 3 share the 10 tricks. Any of them with 0 also gets +1.
        val cfg = fourPlayerConfig()
        val hand = Hand.Raspasovka(
            handNumber = 1,
            dealerSeat = 0,
            tricksBySeat = mapOf(0 to 0, 1 to 4, 2 to 3, 3 to 3),
        )
        val result = ScoringEngine.applyHand(emptySheet(cfg), hand, cfg)

        assertEquals(10, result.scores[0]?.bullet) // dealer +10
        assertEquals(0, result.scores[0]?.mountain)
        assertEquals(4, result.scores[1]?.mountain)
        assertEquals(3, result.scores[2]?.mountain)
        assertEquals(3, result.scores[3]?.mountain)
    }

    @Test fun raspasovka_fourPlayer_nonDealerZeroTricks_gets1Bullet() {
        val cfg = fourPlayerConfig()
        // Dealer takes 2, one non-dealer takes 0.
        val hand = Hand.Raspasovka(
            handNumber = 1,
            dealerSeat = 0,
            tricksBySeat = mapOf(0 to 2, 1 to 0, 2 to 4, 3 to 4),
        )
        val result = ScoringEngine.applyHand(emptySheet(cfg), hand, cfg)

        assertEquals(0, result.scores[0]?.bullet) // dealer took 2 → no bullet
        assertEquals(2, result.scores[0]?.mountain) // dealer mountain
        assertEquals(1, result.scores[1]?.bullet) // non-dealer 0 → +1
        assertEquals(0, result.scores[1]?.mountain)
    }

    // ── Dealer stands in for an opponent (4-player) ─────────────────────────

    @Test fun fourPlayer_dealerStandsInFor_redirectsOppScoreToDealer() {
        // 4P, declarer = seat 1, dealer = seat 0. Both opps would have passed,
        // but dealer steps in for seat 2. Declarer bids 6♠ and fails by 1.
        // Without redirect: seat 2 (the original opp) gets the whist credit.
        // With dealerStandsInFor = 2: that credit must instead go to seat 0.
        val cfg = fourPlayerConfig()
        val hand = Hand.Played(
            handNumber = 1,
            dealerSeat = 0,
            declarerSeat = 1,
            bid = Bid.SuitBid(6, Suit.SPADES),
            declarerTricks = 5,
            opponents = listOf(
                // Seat 2 is "the opp" but the dealer is playing as them.
                WhisterRecord(seat = 2, choice = WhistChoice.WHIST, tricks = 3),
                WhisterRecord(seat = 3, choice = WhistChoice.PASS, tricks = 2),
            ),
            dealerStandsInFor = 2,
        )
        val result = ScoringEngine.applyHand(emptySheet(cfg), hand, cfg)

        // V[6]=2, short=1. Declarer +2 mountain.
        assertEquals(2, result.scores[1]?.mountain)
        // Mirror whist V*1=2 to each opp. Whist bonus V (split among 1 whister) = 2.
        // Seat 2's credits redirect to dealer (seat 0): mirror 2 + bonus 2 = 4.
        assertEquals(4, result.scores[0]?.whistAgainst?.get(1))
        // Seat 2 itself gets nothing.
        assertEquals(null, result.scores[2]?.whistAgainst?.get(1))
        // Seat 3 (the actual passer) still gets the mirror whist.
        assertEquals(2, result.scores[3]?.whistAgainst?.get(1))
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
                WhisterRecord(seat = 2, choice = WhistChoice.WHIST, tricks = 2),
                WhisterRecord(seat = 3, choice = WhistChoice.WHIST, tricks = 2),
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
                WhisterRecord(seat = 2, choice = WhistChoice.WHIST, tricks = 2),
                WhisterRecord(seat = 3, choice = WhistChoice.WHIST, tricks = 2),
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

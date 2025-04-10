package com.carolinarollergirls.scoreboard.core.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.LinkedList;
import java.util.Queue;

import org.junit.Before;
import org.junit.Test;

import com.carolinarollergirls.scoreboard.core.ScoreBoardImpl;
import com.carolinarollergirls.scoreboard.core.interfaces.CurrentGame;
import com.carolinarollergirls.scoreboard.core.interfaces.Fielding;
import com.carolinarollergirls.scoreboard.core.interfaces.FloorPosition;
import com.carolinarollergirls.scoreboard.core.interfaces.Game;
import com.carolinarollergirls.scoreboard.core.interfaces.Role;
import com.carolinarollergirls.scoreboard.core.interfaces.Rulesets;
import com.carolinarollergirls.scoreboard.core.interfaces.Rulesets.Ruleset;
import com.carolinarollergirls.scoreboard.core.interfaces.ScoreBoard;
import com.carolinarollergirls.scoreboard.core.interfaces.Skater;
import com.carolinarollergirls.scoreboard.core.interfaces.Team;
import com.carolinarollergirls.scoreboard.core.interfaces.TeamJam;
import com.carolinarollergirls.scoreboard.core.prepared.RulesetsImpl;
import com.carolinarollergirls.scoreboard.event.ConditionalScoreBoardListener;
import com.carolinarollergirls.scoreboard.event.ScoreBoardEvent;
import com.carolinarollergirls.scoreboard.event.ScoreBoardEventProvider.Flag;
import com.carolinarollergirls.scoreboard.event.ScoreBoardListener;
import com.carolinarollergirls.scoreboard.rules.Rule;
import com.carolinarollergirls.scoreboard.utils.ScoreBoardClock;
import com.carolinarollergirls.scoreboard.utils.ValWithId;

public class TeamImplTests {

    private ScoreBoard sb;
    private Game g;

    private Queue<ScoreBoardEvent<?>> collectedEvents;
    public ScoreBoardListener listener = new ScoreBoardListener() {
        @Override
        public void scoreBoardChange(ScoreBoardEvent<?> event) {
            synchronized (collectedEvents) { collectedEvents.add(event); }
        }
    };

    private TeamImpl team;
    private static String ID = Team.ID_1;

    @Before
    public void setUp() throws Exception {
        collectedEvents = new LinkedList<>();

        sb = new ScoreBoardImpl(false);
        sb.postAutosaveUpdate();
        g = sb.getCurrentGame().get(CurrentGame.GAME);
        team = (TeamImpl) g.getTeam(ID);
        ScoreBoardClock.getInstance().stop();
        GameImpl.setQuickClockThreshold(0L);

        // run a jam so timeouts and ORs are credited to period 1.
        g.startJam();
        g.stopJamTO();
    }

    private void advance(long time_ms) { ScoreBoardClock.getInstance().advance(time_ms); }

    @Test
    public void testStartJam() {
        team.set(Team.TRIP_SCORE, 34);
        team.set(Team.LEAD, false);
        sb.addScoreBoardListener(new ConditionalScoreBoardListener<>(team, Team.LAST_SCORE, listener));
        sb.addScoreBoardListener(new ConditionalScoreBoardListener<>(team, Team.LEAD, listener));

        g.startJam();

        assertEquals(0, (int) team.get(Team.TRIP_SCORE));
        assertFalse(team.isLead());
        assertEquals(1, collectedEvents.size());
    }

    @Test
    public void testStopJam() {
        g.startJam();
        team.setStarPass(true);

        g.stopJamTO();

        assertTrue(team.isStarPass());
        assertTrue(team.isFieldingStarPass());

        team.execute(Team.ADVANCE_FIELDINGS);

        assertTrue(team.isStarPass());
        assertFalse(team.isFieldingStarPass());
    }

    @Test
    public void testTimeout() {
        assertEquals(3, team.getTimeouts());

        team.timeout();
        assertEquals(2, team.getTimeouts());
        assertEquals(team, g.getTimeoutOwner());
        assertFalse(g.isOfficialReview());

        team.timeout();
        assertEquals(2, team.getTimeouts());
    }

    @Test
    public void testOfficialReview() {
        team.officialReview();
        assertEquals(0, team.getOfficialReviews());
        assertEquals(team, g.getTimeoutOwner());
        assertTrue(g.isOfficialReview());

        team.officialReview();
        assertEquals(0, team.getOfficialReviews());
    }

    @Test
    public void testSetRetainedOfficialReview() {
        assertFalse(team.retainedOfficialReview());
        assertEquals(1, team.getOfficialReviews());
        sb.addScoreBoardListener(new ConditionalScoreBoardListener<>(team, Team.RETAINED_OFFICIAL_REVIEW, listener));

        // can't set retained when there was no OR
        team.setRetainedOfficialReview(true);
        assertFalse(team.retainedOfficialReview());
        assertEquals(1, team.getOfficialReviews());
        assertEquals(0, collectedEvents.size());

        team.officialReview();
        g.stopJamTO();

        team.setRetainedOfficialReview(true);
        assertTrue(team.retainedOfficialReview());
        assertEquals(1, team.getOfficialReviews());
        assertEquals(1, collectedEvents.size());
        ScoreBoardEvent<?> event = collectedEvents.poll();
        assertTrue((Boolean) event.getValue());
        assertFalse((Boolean) event.getPreviousValue());

        // check idempotency
        team.setRetainedOfficialReview(true);
        assertTrue(team.retainedOfficialReview());
        assertEquals(0, collectedEvents.size());

        team.setRetainedOfficialReview(false);
        assertFalse(team.retainedOfficialReview());
        assertEquals(0, team.getOfficialReviews());

        collectedEvents.clear();
        team.setRetainedOfficialReview(true);
        assertEquals(1, team.getOfficialReviews());
        assertEquals(1, collectedEvents.size());
    }

    @Test
    public void testTimeoutsResetAtHalf() {
        team.timeout();
        advance(60000);
        g.timeout();
        team.officialReview();
        team.setRetainedOfficialReview(true);
        advance(60000);
        g.timeout();
        team.officialReview();

        assertFalse(team.inTimeout());
        assertTrue(team.inOfficialReview());
        assertEquals(2, team.getTimeouts());
        assertEquals(0, team.getOfficialReviews());
        assertFalse(team.retainedOfficialReview());

        g.stopJamTO();
        g.startJam();
        g.stopJamTO();
        advance(g.getLong(Rule.PERIOD_DURATION));
        advance(15 * 60 * 1000);
        g.startJam();
        team.setRetainedOfficialReview(true);

        assertFalse(team.inTimeout());
        assertFalse(team.inOfficialReview());
        assertEquals(2, team.getTimeouts());
        assertEquals(1, team.getOfficialReviews());
        assertFalse(team.retainedOfficialReview());

        g.set(Rule.NUMBER_REVIEWS, String.valueOf(2));
        team.recountTimeouts();
        assertFalse(team.inTimeout());
        assertFalse(team.inOfficialReview());
        assertEquals(2, team.getTimeouts());
        assertEquals(2, team.getOfficialReviews());
        assertFalse(team.retainedOfficialReview());

        g.set(Rule.NUMBER_TIMEOUTS, String.valueOf(4));
        g.set(Rule.TIMEOUTS_PER_PERIOD, String.valueOf(true));
        g.set(Rule.REVIEWS_PER_PERIOD, String.valueOf(false));
        team.recountTimeouts();
        assertEquals(4, team.getTimeouts());
        assertEquals(1, team.getOfficialReviews());
        assertFalse(team.retainedOfficialReview());
    }

    @Test
    public void testChangeScore() {
        g.startJam();
        g.stopJamTO();
        team.set(Team.TRIP_SCORE, 3);
        assertEquals(3, team.getScore());

        team.set(Team.TRIP_SCORE, 1, Flag.CHANGE);
        assertEquals(4, team.getScore());

        team.set(Team.TRIP_SCORE, -1, Flag.CHANGE);
        assertEquals(3, team.getScore());

        assertFalse(team.cancelTripAdvancement());

        g.startJam();
        team.set(Team.TRIP_SCORE, 3);

        assertTrue(team.cancelTripAdvancement());
    }

    @Test
    public void testCancelTripAdvancement() {
        g.startJam();
        team.execute(Team.ADD_TRIP);

        assertEquals(2, team.getCurrentTrip().getNumber());

        team.set(Team.TRIP_SCORE, 1);
        team.set(Team.TRIP_SCORE, -1, Flag.CHANGE);

        assertFalse(team.cancelTripAdvancement());

        team.execute(Team.ADD_TRIP);
        team.set(Team.TRIP_SCORE, 1);

        assertEquals(3, team.getCurrentTrip().getNumber());

        team.execute(Team.REMOVE_TRIP);

        assertFalse(team.cancelTripAdvancement());
        assertEquals(3, team.getCurrentTrip().getNumber());
    }

    @Test
    public void testDisplayLead() {
        assertFalse(team.isLost());
        assertFalse(team.isLead());
        sb.addScoreBoardListener(new ConditionalScoreBoardListener<>(team, Team.DISPLAY_LEAD, listener));

        team.set(Team.LEAD, true);
        assertTrue(team.isDisplayLead());
        assertEquals(1, collectedEvents.size());
        ScoreBoardEvent<?> event = collectedEvents.poll();
        assertEquals(true, event.getValue());
        assertEquals(false, event.getPreviousValue());

        // check idempotency
        team.set(Team.LEAD, true);
        assertTrue(team.isDisplayLead());
        assertEquals(0, collectedEvents.size());

        team.set(Team.LOST, true);
        assertFalse(team.isDisplayLead());

        team.set(Team.LOST, false);
        assertTrue(team.isDisplayLead());
    }

    @Test
    public void testSetStarPass() {
        assertFalse(team.isStarPass());
        sb.addScoreBoardListener(new ConditionalScoreBoardListener<>(team, Team.STAR_PASS, listener));

        team.setStarPass(true);
        assertTrue(team.isStarPass());
        assertEquals(1, collectedEvents.size());
        ScoreBoardEvent<?> event = collectedEvents.poll();
        assertTrue((Boolean) event.getValue());
        assertFalse((Boolean) event.getPreviousValue());

        // check idempotency
        team.setStarPass(true);
        assertTrue(team.isStarPass());
        assertEquals(0, collectedEvents.size());

        team.setStarPass(false);
        assertFalse(team.isStarPass());
    }

    @Test
    public void testField() {
        team.execute(Team.ADVANCE_FIELDINGS);
        Skater skater1 = new SkaterImpl(team, "S1");
        Skater skater2 = new SkaterImpl(team, "S2");
        Skater skater3 = new SkaterImpl(team, "S3");
        Skater skater4 = new SkaterImpl(team, "S4");
        Skater skater5 = new SkaterImpl(team, "S5");
        Skater skater6 = new SkaterImpl(team, "S6");
        team.addSkater(skater1);
        team.addSkater(skater2);
        team.addSkater(skater3);
        team.addSkater(skater4);
        team.addSkater(skater5);
        team.addSkater(skater6);

        team.field(skater1, Role.JAMMER);
        team.field(skater2, Role.PIVOT);
        team.field(skater3, Role.BLOCKER);
        team.field(skater4, Role.BLOCKER);
        team.field(skater5, Role.BLOCKER);

        assertFalse(team.hasNoPivot());
        assertEquals(skater1, team.getPosition(FloorPosition.JAMMER).getSkater());
        assertEquals(team.getPosition(FloorPosition.JAMMER), skater1.getPosition());
        assertEquals(Role.JAMMER, skater1.getRole());
        assertEquals(skater2, team.getPosition(FloorPosition.PIVOT).getSkater());
        assertEquals(team.getPosition(FloorPosition.PIVOT), skater2.getPosition());
        assertEquals(Role.PIVOT, skater2.getRole());
        assertEquals(skater3, team.getPosition(FloorPosition.BLOCKER1).getSkater());
        assertEquals(team.getPosition(FloorPosition.BLOCKER1), skater3.getPosition());
        assertEquals(Role.BLOCKER, skater3.getRole());
        assertEquals(skater4, team.getPosition(FloorPosition.BLOCKER2).getSkater());
        assertEquals(team.getPosition(FloorPosition.BLOCKER2), skater4.getPosition());
        assertEquals(Role.BLOCKER, skater4.getRole());
        assertEquals(skater5, team.getPosition(FloorPosition.BLOCKER3).getSkater());
        assertEquals(team.getPosition(FloorPosition.BLOCKER3), skater5.getPosition());
        assertEquals(Role.BLOCKER, skater5.getRole());

        team.field(skater6, Role.JAMMER);

        assertEquals(skater6, team.getPosition(FloorPosition.JAMMER).getSkater());
        assertEquals(team.getPosition(FloorPosition.JAMMER), skater6.getPosition());
        assertEquals(Role.JAMMER, skater6.getRole());
        assertNull(skater1.getPosition());
        assertEquals(Role.BENCH, skater1.getRole());

        team.field(skater1, Role.BLOCKER);

        assertEquals(skater1, team.getPosition(FloorPosition.BLOCKER1).getSkater());
        assertEquals(team.getPosition(FloorPosition.BLOCKER1), skater1.getPosition());
        assertEquals(Role.BLOCKER, skater1.getRole());
        assertNull(skater3.getPosition());
        assertEquals(Role.BENCH, skater3.getRole());

        team.field(skater2, Role.BLOCKER);

        assertTrue(team.hasNoPivot());
        assertEquals(skater2, team.getPosition(FloorPosition.PIVOT).getSkater());
        assertEquals(team.getPosition(FloorPosition.PIVOT), skater2.getPosition());
        assertEquals(Role.BLOCKER, skater2.getRole());

        team.field(skater4, Role.PIVOT);

        assertFalse(team.hasNoPivot());
        assertEquals(skater4, team.getPosition(FloorPosition.PIVOT).getSkater());
        assertEquals(team.getPosition(FloorPosition.PIVOT), skater4.getPosition());
        assertEquals(Role.PIVOT, skater4.getRole());
        assertEquals(skater2, team.getPosition(FloorPosition.BLOCKER2).getSkater());
        assertEquals(team.getPosition(FloorPosition.BLOCKER2), skater2.getPosition());
        assertEquals(Role.BLOCKER, skater2.getRole());

        team.field(skater5, Role.PIVOT);

        assertFalse(team.hasNoPivot());
        assertEquals(skater5, team.getPosition(FloorPosition.PIVOT).getSkater());
        assertEquals(team.getPosition(FloorPosition.PIVOT), skater5.getPosition());
        assertEquals(Role.PIVOT, skater5.getRole());
        assertEquals(skater4, team.getPosition(FloorPosition.BLOCKER3).getSkater());
        assertEquals(team.getPosition(FloorPosition.BLOCKER3), skater4.getPosition());
        assertEquals(Role.BLOCKER, skater4.getRole());

        g.startJam();
        assertEquals(skater6, team.getPosition(FloorPosition.JAMMER).getSkater());
        assertEquals(skater5, team.getPosition(FloorPosition.PIVOT).getSkater());
        assertEquals(skater1, team.getPosition(FloorPosition.BLOCKER1).getSkater());
        assertEquals(skater2, team.getPosition(FloorPosition.BLOCKER2).getSkater());
        assertEquals(skater4, team.getPosition(FloorPosition.BLOCKER3).getSkater());

        team.setStarPass(true);

        assertFalse(team.hasNoPivot());
        assertEquals(skater6, team.getPosition(FloorPosition.JAMMER).getSkater());
        assertEquals(team.getPosition(FloorPosition.JAMMER), skater6.getPosition());
        assertEquals(Role.BLOCKER, skater6.getRole());
        assertEquals(skater5, team.getPosition(FloorPosition.PIVOT).getSkater());
        assertEquals(team.getPosition(FloorPosition.PIVOT), skater5.getPosition());
        assertEquals(Role.JAMMER, skater5.getRole());

        skater6.setPenaltyBox(true);
        skater5.setPenaltyBox(true);
        skater2.setPenaltyBox(true);
        g.stopJamTO();

        assertEquals(team.getPosition(FloorPosition.BLOCKER1), skater1.getPosition());
        assertEquals(team.getPosition(FloorPosition.BLOCKER2), skater2.getPosition());
        assertNull(skater3.getPosition());
        assertEquals(team.getPosition(FloorPosition.BLOCKER3), skater4.getPosition());
        assertEquals(team.getPosition(FloorPosition.PIVOT), skater5.getPosition());
        assertEquals(team.getPosition(FloorPosition.JAMMER), skater6.getPosition());
        assertEquals(Role.BLOCKER, skater1.getRole());
        assertEquals(Role.BLOCKER, skater2.getRole());
        assertEquals(Role.BENCH, skater3.getRole());
        assertEquals(Role.BLOCKER, skater4.getRole());
        assertEquals(Role.JAMMER, skater5.getRole());
        assertEquals(Role.BLOCKER, skater6.getRole());
        assertEquals(skater5, team.getPosition(FloorPosition.JAMMER).getSkater());
        assertNull(team.getPosition(FloorPosition.PIVOT).getSkater());
        assertEquals(skater6, team.getPosition(FloorPosition.BLOCKER1).getSkater());
        assertEquals(skater2, team.getPosition(FloorPosition.BLOCKER2).getSkater());
        assertNull(team.getPosition(FloorPosition.BLOCKER3).getSkater());

        skater2.setPenaltyBox(false);
        skater4.setPenaltyBox(true);

        assertEquals(team.getPosition(FloorPosition.BLOCKER1), skater1.getPosition());
        assertEquals(team.getPosition(FloorPosition.BLOCKER2), skater2.getPosition());
        assertNull(skater3.getPosition());
        assertEquals(team.getPosition(FloorPosition.BLOCKER3), skater4.getPosition());
        assertEquals(team.getPosition(FloorPosition.PIVOT), skater5.getPosition());
        assertEquals(team.getPosition(FloorPosition.JAMMER), skater6.getPosition());
        assertEquals(Role.BLOCKER, skater1.getRole());
        assertEquals(Role.BLOCKER, skater2.getRole());
        assertEquals(Role.BENCH, skater3.getRole());
        assertEquals(Role.BLOCKER, skater4.getRole());
        assertEquals(Role.JAMMER, skater5.getRole());
        assertEquals(Role.BLOCKER, skater6.getRole());
        assertEquals(skater5, team.getPosition(FloorPosition.JAMMER).getSkater());
        assertNull(team.getPosition(FloorPosition.PIVOT).getSkater());
        assertEquals(skater6, team.getPosition(FloorPosition.BLOCKER1).getSkater());
        assertNull(team.getPosition(FloorPosition.BLOCKER2).getSkater());
        assertEquals(skater4, team.getPosition(FloorPosition.BLOCKER3).getSkater());

        team.execute(Team.ADVANCE_FIELDINGS);

        assertNull(skater1.getPosition());
        assertNull(skater2.getPosition());
        assertNull(skater3.getPosition());
        assertEquals(team.getPosition(FloorPosition.BLOCKER3), skater4.getPosition());
        assertEquals(team.getPosition(FloorPosition.JAMMER), skater5.getPosition());
        assertEquals(team.getPosition(FloorPosition.BLOCKER1), skater6.getPosition());
        assertEquals(Role.BENCH, skater1.getRole());
        assertEquals(Role.BENCH, skater2.getRole());
        assertEquals(Role.BENCH, skater3.getRole());
        assertEquals(Role.BLOCKER, skater4.getRole());
        assertEquals(Role.JAMMER, skater5.getRole());
        assertEquals(Role.BLOCKER, skater6.getRole());
        assertEquals(skater5, team.getPosition(FloorPosition.JAMMER).getSkater());
        assertNull(team.getPosition(FloorPosition.PIVOT).getSkater());
        assertEquals(skater6, team.getPosition(FloorPosition.BLOCKER1).getSkater());
        assertNull(team.getPosition(FloorPosition.BLOCKER2).getSkater());
        assertEquals(skater4, team.getPosition(FloorPosition.BLOCKER3).getSkater());

        team.field(skater1, Role.PIVOT);
        team.field(skater2, Role.BLOCKER);
        skater6.setPenaltyBox(false);
        g.startJam();
        g.stopJamTO();
        team.execute(Team.ADVANCE_FIELDINGS);

        assertNull(skater1.getPosition());
        assertNull(skater2.getPosition());
        assertNull(skater3.getPosition());
        assertEquals(team.getPosition(FloorPosition.BLOCKER3), skater4.getPosition());
        assertEquals(team.getPosition(FloorPosition.JAMMER), skater5.getPosition());
        assertNull(skater6.getPosition());
        assertEquals(Role.BENCH, skater1.getRole());
        assertEquals(Role.BENCH, skater2.getRole());
        assertEquals(Role.BENCH, skater3.getRole());
        assertEquals(Role.BLOCKER, skater4.getRole());
        assertEquals(Role.JAMMER, skater5.getRole());
        assertEquals(Role.BENCH, skater6.getRole());
        assertEquals(skater5, team.getPosition(FloorPosition.JAMMER).getSkater());
        assertNull(team.getPosition(FloorPosition.PIVOT).getSkater());
        assertNull(team.getPosition(FloorPosition.BLOCKER1).getSkater());
        assertNull(team.getPosition(FloorPosition.BLOCKER2).getSkater());
        assertEquals(skater4, team.getPosition(FloorPosition.BLOCKER3).getSkater());
    }

    @Test
    public void testFieldPivot() {
        Skater skater1 = new SkaterImpl(team, "S1");
        team.addSkater(skater1);

        g.startJam();
        team.getRunningOrUpcomingTeamJam().getFielding(FloorPosition.PIVOT).set(Fielding.NOT_FIELDED, true);
        g.stopJamTO();

        team.getPosition(FloorPosition.PIVOT).setSkater(skater1);

        assertEquals(skater1, team.getPosition(FloorPosition.PIVOT).getSkater());
        assertFalse(team.getRunningOrUpcomingTeamJam().hasNoPivot());
        assertTrue(team.getRunningOrEndedTeamJam().hasNoPivot());
        assertEquals(Role.BENCH, skater1.getRole());
        assertEquals(1, skater1.numberOf(Skater.FIELDING));

        team.execute(Team.ADVANCE_FIELDINGS);

        assertEquals(Role.PIVOT, skater1.getRole());
    }

    @Test
    public void testLostOnJammerPenalty() {
        sb.getSettings().set(ScoreBoard.SETTING_USE_LT, "true");
        team.execute(Team.ADVANCE_FIELDINGS);
        Skater skater1 = new SkaterImpl(team, "S1");
        team.addSkater(skater1);
        team.field(skater1, Role.JAMMER);
        g.startJam();

        skater1.getOrCreate(Skater.PENALTY, "1");
        assertTrue(team.isLost());
    }

    @Test
    public void testLostOnEligibleEndOfInitial() {
        g.startJam();
        team.execute(Team.ADD_TRIP);
        assertTrue(team.isLost());
    }

    @Test
    public void testNoLostOnIneligibleEndOfInitial() {
        g.startJam();
        g.getTeam(Team.ID_2).set(Team.LEAD, true);
        team.execute(Team.ADD_TRIP);
        assertFalse(team.isLost());
    }

    @Test
    public void testCalloffAndInjuryUnsetEachOther() {
        g.startJam();
        team.set(Team.LEAD, true);
        advance(15000);
        g.stopJamTO();
        assertTrue(team.isCalloff());
        assertFalse(team.isInjury());

        team.set(Team.INJURY, true);
        assertTrue(team.isInjury());
        assertFalse(team.isCalloff());

        team.set(Team.CALLOFF, true);
        assertTrue(team.isCalloff());
        assertFalse(team.isInjury());

        // check they can be set simultaneous on the SK sheet
        team.getRunningOrEndedTeamJam().set(TeamJam.INJURY, true);
        assertTrue(team.isCalloff());
        assertTrue(team.isInjury());
    }

    @Test
    public void testNoCalloffOnInj() {
        g.startJam();
        team.set(Team.LEAD, true);
        advance(17000);
        team.set(Team.INJURY, true);

        assertFalse(g.isInJam());
        assertTrue(team.isInjury());
        assertFalse(team.isCalloff());

        g.startJam();
        team.set(Team.LEAD, true);
        advance(20000);
        team.getOtherTeam().set(Team.INJURY, true);
        assertFalse(team.isCalloff());
    }

    @Test
    public void testRulesetChange() {
        Rulesets.Ruleset child =
            sb.getRulesets().addRuleset("child", sb.getRulesets().getRuleset(RulesetsImpl.ROOT_ID), "id");
        child.add(Ruleset.RULE, new ValWithId(Rule.NUMBER_TIMEOUTS.toString(), "1"));
        child.add(Ruleset.RULE, new ValWithId(Rule.NUMBER_REVIEWS.toString(), "0"));

        g.setRuleset(child);
        assertEquals(1, team.getTimeouts());
        assertEquals(0, team.getOfficialReviews());
    }

    @Test
    public void testUnStart() {
        team.execute(Team.ADVANCE_FIELDINGS);
        Skater skater1 = new SkaterImpl(team, "S1");
        team.addSkater(skater1);
        team.field(skater1, Role.JAMMER);

        g.startJam();

        assertEquals(skater1, team.getPosition(FloorPosition.JAMMER).getSkater());
        assertEquals(team.getPosition(FloorPosition.JAMMER), skater1.getPosition());
        assertEquals(Role.JAMMER, skater1.getRole());

        g.clockUndo(false);

        assertEquals(skater1, team.getPosition(FloorPosition.JAMMER).getSkater());
        assertEquals(team.getPosition(FloorPosition.JAMMER), skater1.getPosition());
        assertEquals(Role.JAMMER, skater1.getRole());
    }

    @Test
    public void testUnStop() {
        team.execute(Team.ADVANCE_FIELDINGS);
        Skater skater1 = new SkaterImpl(team, "S1");
        team.addSkater(skater1);
        team.field(skater1, Role.JAMMER);
        g.startJam();
        team.set(Team.LEAD, true);

        assertEquals(skater1, team.getPosition(FloorPosition.JAMMER).getSkater());
        assertEquals(team.getPosition(FloorPosition.JAMMER), skater1.getPosition());
        assertEquals(Role.JAMMER, skater1.getRole());
        assertFalse(team.isCalloff());

        g.stopJamTO();

        assertEquals(null, team.getPosition(FloorPosition.JAMMER).getSkater());
        assertEquals(team.getPosition(FloorPosition.JAMMER), skater1.getPosition());
        assertEquals(Role.JAMMER, skater1.getRole());
        assertTrue(team.isCalloff());

        g.clockUndo(false);

        assertEquals(skater1, team.getPosition(FloorPosition.JAMMER).getSkater());
        assertEquals(team.getPosition(FloorPosition.JAMMER), skater1.getPosition());
        assertEquals(Role.JAMMER, skater1.getRole());
        assertFalse(team.isCalloff());
    }

    @Test
    public void testUnStopAfterAdvance() {
        team.execute(Team.ADVANCE_FIELDINGS);
        Skater skater1 = new SkaterImpl(team, "S1");
        team.addSkater(skater1);
        team.field(skater1, Role.JAMMER);
        g.startJam();

        assertEquals(skater1, team.getPosition(FloorPosition.JAMMER).getSkater());
        assertEquals(team.getPosition(FloorPosition.JAMMER), skater1.getPosition());
        assertEquals(Role.JAMMER, skater1.getRole());

        g.stopJamTO();
        team.execute(Team.ADVANCE_FIELDINGS);

        assertEquals(null, team.getPosition(FloorPosition.JAMMER).getSkater());
        assertEquals(null, skater1.getPosition());
        assertEquals(Role.BENCH, skater1.getRole());

        g.clockUndo(false);

        assertEquals(skater1, team.getPosition(FloorPosition.JAMMER).getSkater());
        assertEquals(team.getPosition(FloorPosition.JAMMER), skater1.getPosition());
        assertEquals(Role.JAMMER, skater1.getRole());
    }
}

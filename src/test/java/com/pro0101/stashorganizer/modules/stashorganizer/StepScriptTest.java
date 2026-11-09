package com.pro0101.stashorganizer.modules.stashorganizer;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The step contract every converted component relies on. */
class StepScriptTest {
    private final List<String> calls = new ArrayList<>();

    @Test
    void runsOneStepPerTickUntilDone() {
        int[] callsToFirst = {0};

        StepScript script = new StepScript(
            () -> {
                calls.add("a");
                // Needs two ticks, like a walk or a slot click.
                return ++callsToFirst[0] < 2 ? StepResult.RUNNING : StepResult.DONE;
            },
            () -> {
                calls.add("b");
                return StepResult.DONE;
            },
            () -> {
                calls.add("c");
                return StepResult.DONE;
            });

        script.run();
        assertEquals(List.of("a"), calls);
        assertEquals(0, script.index(), "RUNNING stays on the same step");

        script.run();
        assertEquals(List.of("a", "a"), calls);
        assertEquals(1, script.index(), "DONE moves to the next step");

        script.run();
        assertEquals(List.of("a", "a", "b"), calls);

        script.run();
        assertEquals(List.of("a", "a", "b", "c"), calls);
        assertTrue(script.finished());

        script.run();
        assertEquals(4, calls.size(), "a finished script runs nothing");
    }

    @Test
    void jumpContinuesAtTheGivenStep() {
        StepScript script = new StepScript(
            () -> {
                calls.add("first");
                return StepResult.jumpTo(2);
            },
            () -> {
                calls.add("skipped");
                return StepResult.DONE;
            },
            () -> {
                calls.add("third");
                return StepResult.DONE;
            });

        script.run();
        assertEquals(2, script.index());
        assertEquals(List.of("first"), calls);

        script.run();
        assertTrue(script.finished());
        assertEquals(List.of("first", "third"), calls);
    }

    @Test
    void failureStopsUntilRestart() {
        StepScript script = new StepScript(
            () -> StepResult.FAILED,
            () -> {
                calls.add("never");
                return StepResult.DONE;
            });

        script.run();
        assertTrue(script.failed());
        assertFalse(script.finished());

        script.run();
        assertEquals(0, calls.size(), "a failed script runs nothing");

        script.restart();
        assertFalse(script.failed());
        assertEquals(0, script.index());
    }
}

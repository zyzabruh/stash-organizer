package com.example.addon.modules.stashorganizer;

/**
 * Runs an ordered list of {@link Step}s, one per tick.
 * <p>
 * A step that needs more ticks returns {@link StepResult#RUNNING} and is called again on the next
 * tick; {@link StepResult#DONE} moves to the next step, {@link StepResult#FAILED} stops the script
 * and {@link StepResult#jumpTo(int)} continues at another step, which is how the loops (take
 * another stack, reopen the box, retry a placement) are written.
 * <p>
 * The order and the loops of a component are therefore one list in its constructor instead of a
 * switch plus a set of "stage = N" assignments spread over every branch.
 */
public final class StepScript {
    private final Step[] steps;
    private int index;
    private boolean failed;

    public StepScript(Step... steps) {
        this.steps = steps.clone();
    }

    /** Runs the current step and applies its result. */
    public void run() {
        if (failed || index < 0 || index >= steps.length) return;

        StepResult result = steps[index].tick();

        switch (result.kind()) {
            case RUNNING -> {
            }
            case DONE -> index++;
            case FAILED -> failed = true;
            case JUMP -> index = result.target();
        }
    }

    public boolean finished() {
        return index >= steps.length;
    }

    public boolean failed() {
        return failed;
    }

    public int index() {
        return index;
    }

    public int size() {
        return steps.length;
    }

    /** Continues at the given step; used by a step that loops back. */
    public void jumpTo(int step) {
        index = step;
    }

    /** Starts over from the first step (a new container, a new box, a new chest). */
    public void restart() {
        index = 0;
        failed = false;
    }
}

package com.example.addon.modules.stashorganizer;

/**
 * One step of a component, run once per tick while it is the current step of its
 * {@link StepScript}. A step keeps its own state in fields and reports what happens next, so the
 * order of the steps and the loops between them are written once in the script instead of being
 * spread over a switch and a set of "stage = N" assignments.
 */
@FunctionalInterface
public interface Step {
    StepResult tick();
}

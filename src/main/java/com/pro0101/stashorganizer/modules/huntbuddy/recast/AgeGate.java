package com.pro0101.stashorganizer.modules.huntbuddy.recast;

public final class AgeGate {
    private int lastAge = -1;

    public boolean alreadySeen(int age) {
        return age == lastAge;
    }

    public void record(int age) {
        lastAge = age;
    }
}

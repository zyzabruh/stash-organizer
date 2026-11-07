package com.example.addon.modules.stashorganizer;

/**
 * The ordered phases of a run.
 * <p>
 * Each phase owns its tick handler and says whether a plan saved in that phase can be resumed, so
 * adding or reordering a phase touches one file instead of the module, the session and the saved
 * plan format.
 */
public enum StashPhase {
    Idle(false),
    Dump(false),
    Scan(false),
    Rescan(false),
    Place(true),
    Sort(true),
    Collect(true),
    Unpack(true),
    Pack(true),
    Label(true),
    Done(false);

    /** True when a saved plan stopped in this phase can be resumed after a disconnection. */
    public final boolean resumable;

    StashPhase(boolean resumable) {
        this.resumable = resumable;
    }

    /** Handler run once per tick while this phase is active. */
    public Runnable tick(StashSession session) {
        return switch (this) {
            case Dump -> session.dumper::tick;
            case Scan, Rescan -> session.scanner::tick;
            case Place -> session.placer::tickPhase;
            case Sort -> session.sorter::tick;
            case Collect -> session.collector::tick;
            case Unpack -> session.unpacker::tick;
            case Pack -> session.packer::tick;
            case Label -> session.labeler::tick;
            case Idle, Done -> () -> { };
        };
    }
}

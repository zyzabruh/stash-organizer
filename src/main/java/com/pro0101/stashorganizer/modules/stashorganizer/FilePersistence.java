package com.pro0101.stashorganizer.modules.stashorganizer;

/** The plan written to disk, next to the Meteor folder. */
public final class FilePersistence implements Persistence {
    @Override
    public void save(StashSession session) {
        StashSessionStore.save(session);
    }

    @Override
    public void delete() {
        StashSessionStore.delete();
    }
}

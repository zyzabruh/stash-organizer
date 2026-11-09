package com.pro0101.stashorganizer.modules.stashorganizer;

/**
 * Saving and deleting the plan of a session. A test uses {@link #NONE} so it never touches the
 * player's files; the game writes the plan next to the Meteor folder.
 */
public interface Persistence {
    Persistence NONE = new Persistence() {
        @Override
        public void save(StashSession session) {
        }

        @Override
        public void delete() {
        }
    };

    void save(StashSession session);

    void delete();
}

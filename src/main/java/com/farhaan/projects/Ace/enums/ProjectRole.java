package com.farhaan.projects.Ace.enums;

/** VIEWER < EDITOR < OWNER. Stored as text in the DB, so the order of the constants does not matter. */
public enum ProjectRole {
    EDITOR(2), VIEWER(1), OWNER(3);

    private final int rank;

    ProjectRole(int rank) {
        this.rank = rank;
    }

    /** true if this role is the same as, or stronger than, the minimum role. */
    public boolean atLeast(ProjectRole minimum) {
        return this.rank >= minimum.rank;
    }
}

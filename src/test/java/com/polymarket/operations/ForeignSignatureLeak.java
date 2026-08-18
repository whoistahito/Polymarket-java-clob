package com.polymarket.operations;

import org.slf4j.Logger;

/** Test-only forbidden dependency: proves the boundary rules actually fail. Never shipped. */
public final class ForeignSignatureLeak {

    /** slf4j is a legitimate SDK dependency, so this proves the rule bans any foreign type, not just transport ones. */
    public Logger logger() {
        return null;
    }
}

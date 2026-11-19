/**
 * The event outbox's upkeep: events that a listener has not finished are published again, from
 * one pod at a time, and completed ones are deleted after a while. Technical: no business module
 * uses it, and it uses none.
 */
@ApplicationModule(displayName = "Event outbox", allowedDependencies = {})
package com.siose.ff.logistics.outbox;

import org.springframework.modulith.ApplicationModule;

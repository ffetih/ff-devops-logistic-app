/**
 * Reporting: figures across the other modules, kept in tables of its own and filled from their
 * events. For now the delivered shipments, from TMS.
 *
 * <p>{@code tms} is an allowed dependency for its events only (they are in its base package).
 * TMS does not know that this module exists.
 */
@ApplicationModule(displayName = "Reporting", allowedDependencies = "tms")
package com.siose.ff.logistics.reporting;

import org.springframework.modulith.ApplicationModule;

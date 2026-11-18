/**
 * Whether this release should take traffic, read by the canary analysis on
 * {@code /actuator/health/release}. A technical module: no business module depends on it.
 */
@ApplicationModule(displayName = "Release health", allowedDependencies = {})
package com.siose.ff.logistics.release;

import org.springframework.modulith.ApplicationModule;

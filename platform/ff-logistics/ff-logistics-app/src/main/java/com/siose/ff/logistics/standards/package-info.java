/**
 * Logistics standards as pure functions: GS1 check digits (GLN, GTIN, SSCC), ISO 6346 container
 * codes, EN 16258 emissions and great-circle distances. No state, no Spring beans, no database; every other module may
 * use it, and it uses none of them.
 */
@ApplicationModule(displayName = "Standards", allowedDependencies = {})
package com.siose.ff.logistics.standards;

import org.springframework.modulith.ApplicationModule;

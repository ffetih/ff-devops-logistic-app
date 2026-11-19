/**
 * Transport management: shipments that move one piece of equipment from origin to destination
 * over transport legs, with their e-CMR consignment note number and their CO2e footprint.
 *
 * <p>{@code api} holds the REST controller, {@code domain} the entities and repositories,
 * {@code service} the use cases. All three are internal.
 */
@ApplicationModule(displayName = "TMS", allowedDependencies = "standards")
package com.siose.ff.logistics.tms;

import org.springframework.modulith.ApplicationModule;

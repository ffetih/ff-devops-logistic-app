/**
 * Geo dispatch: sales and delivery territories, and route plans that put a vehicle's stops in
 * driving order with a distance and an arrival time for each.
 *
 * <p>{@code api} holds the REST controllers, {@code domain} the entities and repositories,
 * {@code routing} the ordering and the road distances, {@code service} the use cases. All four are
 * internal.
 */
@ApplicationModule(displayName = "Geo", allowedDependencies = "standards")
package com.siose.ff.logistics.geo;

import org.springframework.modulith.ApplicationModule;

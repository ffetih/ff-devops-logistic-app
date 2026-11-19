/**
 * Warehouse management: warehouses with their GS1 location numbers, bin locations inside them,
 * and the inbound ASNs that announce a supplier's delivery.
 *
 * <p>{@code api} holds the REST controllers, {@code domain} the entities and repositories,
 * {@code service} the use cases. All three are internal; nothing outside the module uses them yet.
 */
@ApplicationModule(displayName = "WMS", allowedDependencies = "standards")
package com.siose.ff.logistics.wms;

import org.springframework.modulith.ApplicationModule;

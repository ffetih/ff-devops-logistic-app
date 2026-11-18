/**
 * Who is calling: the tenant, user and roles taken from the verified token. A technical module,
 * kept as small as it is so that a token can be checked end to end without any business data.
 */
@ApplicationModule(displayName = "Caller", allowedDependencies = {})
package com.siose.ff.logistics.caller;

import org.springframework.modulith.ApplicationModule;

package com.siose.ff.logistics.caller;

import com.siose.ff.base.tenant.TenantContext;
import com.siose.ff.base.tenant.TenantContext.TenantInfo;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Who the app thinks is calling: the tenant, user and roles it took from the verified token.
 * The first endpoint behind authentication, and the quickest way to check a token end to end.
 */
@RestController
class CallerController {

    @GetMapping("/api/v1/me")
    Caller me() {
        TenantInfo caller = TenantContext.requireCurrent();
        return new Caller(caller.tenantId(), caller.username(), caller.roles().stream().sorted().toList(),
                caller.authSource());
    }

    record Caller(String tenantId, String username, List<String> roles, String authSource) {
    }
}

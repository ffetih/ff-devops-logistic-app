package com.siose.ff.logistics.outbox;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deletes completed rows of {@code event_publication} once they are older than the retention, a
 * week by default, so the table does not grow forever. Open rows stay: their events are still to
 * be handled. Every pod may run it; deleting what another pod deleted already does no harm.
 */
@Component
@Profile("!migrate")
class CompletedEventPublicationCleanup {

    private static final Logger log = LoggerFactory.getLogger(CompletedEventPublicationCleanup.class);

    private final CompletedEventPublications completed;
    private final Duration retention;

    CompletedEventPublicationCleanup(
            CompletedEventPublications completed,
            @Value("${logistics.events.completed-retention:P7D}") Duration retention) {
        this.completed = completed;
        this.retention = retention;
    }

    @Scheduled(fixedDelayString = "${logistics.events.completed-cleanup-delay:PT1H}")
    void deleteOldCompletions() {
        log.debug("Deleting completed event publications older than {}", retention);
        completed.deletePublicationsOlderThan(retention);
    }
}

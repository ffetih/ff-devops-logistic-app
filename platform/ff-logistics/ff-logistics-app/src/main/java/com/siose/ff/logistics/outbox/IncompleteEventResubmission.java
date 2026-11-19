package com.siose.ff.logistics.outbox;

import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Publishes unfinished events again, every 30 seconds, from one pod at a time.
 *
 * <p>Four pods run this schedule. Without coordination, all four would publish the same open
 * row at the same moment, and its listener would run four times. Each run therefore first takes
 * a PostgreSQL session advisory lock, on one connection, and gives it back at the end: a pod
 * that does not get it skips the run. On a database without that lock (the tests' H2) the run
 * goes ahead.
 *
 * <p>Spring Modulith resubmits only rows in status {@code FAILED}: a listener threw. A row that a
 * stopped pod left {@code PUBLISHED} or {@code PROCESSING} turns {@code FAILED} once it is older
 * than {@code spring.modulith.events.staleness.*}. Which failed rows are due now is decided by
 * {@link EventRetryPolicy}; rows past the maximum attempts are counted in the gauge
 * {@code logistics.events.publications.exhausted} and logged when their number grows.
 *
 * <p>Not in the {@code migrate} profile: the migration Job must not run listeners.
 */
@Component
@Profile("!migrate")
public class IncompleteEventResubmission {

    /** Any number all pods agree on; it names the lock. */
    static final long LOCK_KEY = 5_105_000_001L;

    private static final Logger log = LoggerFactory.getLogger(IncompleteEventResubmission.class);

    private final IncompleteEventPublications incomplete;
    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final Duration minAge;
    private final EventRetryPolicy policy;
    private final int batchSize;
    private final int maxInFlight;
    private final AtomicLong exhausted = new AtomicLong();

    IncompleteEventResubmission(
            IncompleteEventPublications incomplete,
            DataSource dataSource,
            MeterRegistry meterRegistry,
            @Value("${logistics.events.resubmit-min-age:PT1M}") Duration minAge,
            @Value("${logistics.events.max-attempts:5}") int maxAttempts,
            @Value("${logistics.events.resubmit-max-delay:PT1H}") Duration maxDelay,
            @Value("${logistics.events.resubmit-batch-size:100}") int batchSize,
            @Value("${logistics.events.resubmit-max-in-flight:50}") int maxInFlight) {
        this.incomplete = incomplete;
        this.dataSource = dataSource;
        this.jdbc = new JdbcTemplate(dataSource);
        this.minAge = minAge;
        this.policy = new EventRetryPolicy(maxAttempts, minAge, maxDelay);
        this.batchSize = batchSize;
        this.maxInFlight = maxInFlight;
        meterRegistry.gauge("logistics.events.publications.exhausted", exhausted);
    }

    @Scheduled(
            fixedDelayString = "${logistics.events.resubmit-delay:PT30S}",
            initialDelayString = "${logistics.events.resubmit-delay:PT30S}")
    void resubmitOnSchedule() {
        resubmitOutstanding();
    }

    /** @return {@code true} when this pod ran the resubmission, {@code false} when another one held the lock */
    public boolean resubmitOutstanding() {
        try (Connection connection = dataSource.getConnection()) {
            if (!tryLock(connection)) {
                log.debug("Another pod is resubmitting events; skipping this run");
                return false;
            }
            try {
                Instant now = Instant.now();
                incomplete.resubmitIncompletePublications(ResubmissionOptions.defaults()
                        .withMinAge(minAge)
                        .withBatchSize(batchSize)
                        .withMaxInFlight(maxInFlight)
                        .withFilter(publication -> policy.isDue(publication, now)));
                countExhausted();
                return true;
            } finally {
                unlock(connection);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not lock the event resubmission", ex);
        }
    }

    /** Rows that will not be tried again. */
    private void countExhausted() {
        Long count = jdbc.queryForObject(
                "select count(*) from event_publication where completion_date is null and completion_attempts >= ?",
                Long.class, policy.maxAttempts());
        long now = count == null ? 0 : count;
        long before = exhausted.getAndSet(now);
        if (now > before) {
            log.warn("{} event publication(s) stopped retrying after {} attempts; see event_publication",
                    now, policy.maxAttempts());
        }
    }

    private boolean tryLock(Connection connection) throws SQLException {
        if (!isPostgres(connection)) {
            return true;
        }
        try (PreparedStatement statement = connection.prepareStatement("select pg_try_advisory_lock(?)")) {
            statement.setLong(1, LOCK_KEY);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getBoolean(1);
            }
        }
    }

    private void unlock(Connection connection) throws SQLException {
        if (!isPostgres(connection)) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("select pg_advisory_unlock(?)")) {
            statement.setLong(1, LOCK_KEY);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
            }
        }
    }

    private static boolean isPostgres(Connection connection) throws SQLException {
        return "PostgreSQL".equals(connection.getMetaData().getDatabaseProductName());
    }
}

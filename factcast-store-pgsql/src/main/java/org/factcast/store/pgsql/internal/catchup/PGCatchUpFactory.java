package org.factcast.store.pgsql.internal.catchup;

import java.util.LinkedList;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.factcast.core.Fact;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.store.pgsql.PGConfigurationProperties;
import org.factcast.store.pgsql.internal.PGConstants;
import org.factcast.store.pgsql.internal.PGPostQueryMatcher;
import org.factcast.store.pgsql.internal.query.PGFactIdToSerialMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Component
public class PGCatchUpFactory {

    final JdbcTemplate jdbc;

    final PGConfigurationProperties props;

    final PGFactIdToSerialMapper serMapper;

    public PGCatchup create(@NonNull SubscriptionRequestTO request,
            PGPostQueryMatcher postQueryMatcher, SubscriptionImpl<Fact> subscription,
            AtomicLong serial) {
        return new PGCatchup(jdbc, props, serMapper, request, postQueryMatcher, subscription,
                serial);
    }

    @RequiredArgsConstructor
    public static class PGCatchup implements Runnable {
        @NonNull
        final JdbcTemplate jdbc;

        @NonNull
        final PGConfigurationProperties props;

        @NonNull
        final PGFactIdToSerialMapper serMapper;

        @NonNull
        final SubscriptionRequestTO request;

        @NonNull
        final PGPostQueryMatcher postQueryMatcher;

        @NonNull
        final SubscriptionImpl<Fact> subscription;

        @NonNull
        final AtomicLong serial;

        private long clientId = 0;

        public LinkedList<Fact> doFetch(PGCatchUpFetchPage fetch) {
            boolean idsOnly = request.idOnly() && postQueryMatcher.canBeSkipped();
            if (idsOnly) {
                return fetch.fetchIdFacts(serial);
            } else {
                return fetch.fetchFacts(serial);
            }
        }

        @Override
        public void run() {
            PGCatchUpPrepare prep = new PGCatchUpPrepare(jdbc, request);
            clientId = prep.prepareCatchup(serial);

            if (clientId > 0) {
                try {
                    PGCatchUpFetchPage fetch = new PGCatchUpFetchPage(jdbc, props, request,
                            clientId);
                    while (true) {
                        LinkedList<Fact> facts = doFetch(fetch);
                        if (facts.isEmpty()) {
                            // we have reached the end
                            break;
                        }

                        while (!facts.isEmpty()) {
                            Fact f = facts.removeFirst();
                            UUID factId = f.id();

                            if (postQueryMatcher.test(f)) {
                                try {
                                    subscription.notifyElement(f);
                                    log.trace("{} notifyElement called with id={}", request,
                                            factId);
                                } catch (Throwable e) {
                                    // debug level, because it happens regularly
                                    // on
                                    // disconnecting clients.
                                    log.debug("{} exception from subscription: {}", request, e
                                            .getMessage());

                                    try {
                                        subscription.close();
                                    } catch (Exception e1) {
                                        log.warn("{} exception while closing subscription: {}",
                                                request, e1.getMessage());
                                    }
                                    throw e;
                                }
                            } else {
                                log.trace("{} filtered id={}", request, factId);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("While fetching ", e);
                } finally {
                    jdbc.update(PGConstants.DELETE_CATCH_BY_CID, clientId);
                }
            }
        }

    }

}
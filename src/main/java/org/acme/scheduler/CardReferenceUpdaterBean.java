package org.acme.scheduler;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.acme.domain.CardReference;
import org.acme.external.CardRequestDto;
import org.acme.rest.CardReferenceService;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.exceptions.MeilisearchApiException;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class CardReferenceUpdaterBean {

    private static final Logger LOG = Logger.getLogger(CardReferenceUpdaterBean.class);

    @RestClient
    CardReferenceService cardReferenceService;

    @Inject
    Client client;

    // Runs shortly after startup, then every 10 minutes. Imports catalog cards we don't have yet
    // (by riftboundId: the Flyway seed row must not block the import), then makes sure Meilisearch
    // holds every CardReference.
    // Not @Transactional: riftcodex takes ~20s per page (15 pages), far beyond the 60s transaction
    // timeout. Each page is committed in its own short transaction instead, so progress survives
    // a failure halfway through.
    @Scheduled(every = "10m", delayed = "10s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void cronJob() {
        try {
            importMissingCards();
        } catch (RuntimeException e) {
            LOG.warn("CardReference import from riftcodex failed", e);
        }
        try {
            syncSearchIndex();
        } catch (RuntimeException e) {
            LOG.warn("CardReference Meilisearch sync failed", e);
        }
    }

    private void importMissingCards() {
        Set<String> known = QuarkusTransaction.requiringNew().call(() -> CardReference.<CardReference>streamAll()
                .map(cardReference -> cardReference.riftboundId)
                .collect(Collectors.toSet()));
        int page = 1;
        int pages;
        int imported = 0;
        do {
            CardRequestDto result = cardReferenceService.getBatchCards(100, page);
            pages = result.pages;
            List<CardReference> missing = result.items.stream()
                    .filter(dto -> dto.set != null && dto.media != null)
                    .filter(dto -> known.add(dto.riftboundId))
                    .map(CardReference::fromCardReferenceDto)
                    .toList();
            if (!missing.isEmpty()) {
                QuarkusTransaction.requiringNew().run(() -> CardReference.persist(missing));
                imported += missing.size();
            }
        } while (++page <= pages);
        if (imported > 0) {
            LOG.infof("Imported %d new card references", imported);
        }
    }

    private void syncSearchIndex() {
        QuarkusTransaction.requiringNew().run(() -> {
            long inDb = CardReference.count();
            long indexed;
            try {
                indexed = client.index(CardReference.INDEX).getStats().getNumberOfDocuments();
            } catch (MeilisearchApiException e) {
                indexed = 0; // index_not_found: it gets created by the reindex below
            }
            if (indexed != inDb) {
                LOG.infof("Meilisearch has %d card references, database has %d: reindexing", indexed, inDb);
                CardReference.reindexAll();
            }
        });
    }

}

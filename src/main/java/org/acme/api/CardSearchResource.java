package org.acme.api;

import java.util.HashMap;
import java.util.List;

import org.acme.domain.CardReference;
import org.acme.dto.CardSearchDto;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.jboss.logging.Logger;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.SearchRequest;
import com.meilisearch.sdk.exceptions.MeilisearchException;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Card catalog search (autocomplete) backed by the Meilisearch {@code card_references} index,
 * which CardReference keeps in sync on persist/update. Meilisearch stays private: the SPA
 * goes through this endpoint, so the master key never leaves the server.
 */
@Path("/cards/search")
@RolesAllowed("user")
public class CardSearchResource {

    private static final Logger LOG = Logger.getLogger(CardSearchResource.class);

    static final String INDEX = CardReference.INDEX;
    static final int DEFAULT_LIMIT = 10;
    static final int MAX_LIMIT = 50;
    static final int MAX_QUERY_LENGTH = 100;

    @Inject
    Client client;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Search the card catalog by name (typo-tolerant, prefix matching)")
    public Response search(
            @Parameter(description = "Search text") @QueryParam("q") String q,
            @Parameter(description = "Max results (1-50)") @QueryParam("limit") @DefaultValue("10") int limit) {
        if (q == null || q.isBlank()) {
            return Response.ok(List.of()).build();
        }
        String query = q.strip();
        if (query.length() > MAX_QUERY_LENGTH) {
            query = query.substring(0, MAX_QUERY_LENGTH);
        }
        int boundedLimit = Math.max(1, Math.min(limit, MAX_LIMIT));

        SearchRequest request = SearchRequest.builder()
                .q(query)
                .limit(boundedLimit)
                .attributesToRetrieve(new String[] { "id", "riftbound_identifier", "name", "image_url" })
                .build();
        try {
            List<CardSearchDto> hits = client.index(INDEX).search(request).getHits().stream()
                    .map(CardSearchResource::toDto)
                    .toList();
            return Response.ok(hits).build();
        } catch (MeilisearchException e) {
            LOG.warn("Card search failed", e);
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).build();
        }
    }

    static CardSearchDto toDto(HashMap<String, Object> hit) {
        return new CardSearchDto(
                parseId(hit.get("id")),
                asString(hit.get("riftbound_identifier")),
                asString(hit.get("name")),
                asString(hit.get("image_url")));
    }

    private static Long parseId(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            // Indexed as a string (CardReference#updateIndexForCard).
            return Long.valueOf(raw.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String asString(Object raw) {
        return raw == null ? null : raw.toString();
    }
}

package org.acme.dto;

import java.time.Instant;
import java.util.List;

import org.acme.domain.Card;

public record DeckDto(Long id, Long ownerId, List<Card> ownedCards, List<Card> missingCards, Double completion, Instant createdAt, Instant updatedAt) {

}

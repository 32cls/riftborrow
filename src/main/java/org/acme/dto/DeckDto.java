package org.acme.dto;

import java.time.Instant;
import java.util.List;

import org.acme.domain.Card;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;

@JsonIdentityInfo (
  generator = ObjectIdGenerators.PropertyGenerator.class,
  property = "id")
public record DeckDto(Long id, Long ownerId, List<Card> ownedCards, List<Card> missingCards, Double completion, Instant createdAt, Instant updatedAt) {

}

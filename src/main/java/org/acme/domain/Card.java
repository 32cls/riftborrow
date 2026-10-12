package org.acme.domain;

import java.util.List;
import java.util.Map;

import org.acme.dto.BorrowableCardDto;
import org.acme.input.CardInput;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.ws.rs.NotFoundException;

@Entity
@NamedQueries({
    @NamedQuery(name = "Card.findBorrowableCards", query = "SELECT c.id, cr.name, cr.set, c.quality, c.owner.username, cr.riftboundId, cr.imageUrl, distance_meters(:location, u.location) as dist FROM Card c INNER JOIN User u ON u.id = c.owner.id INNER JOIN CardReference cr ON c.cardReference.id = cr.id WHERE cr.name LIKE :name AND c.language = :language AND c.quality <= :quality AND c.isBorrowed IS FALSE AND u.id <> :id ORDER BY dist ASC LIMIT 30"),
})
@JsonIdentityInfo(
  generator = ObjectIdGenerators.PropertyGenerator.class,
  property = "id")
public class Card extends PanacheEntity {

    public enum Language {
        CHINESE,
        ENGLISH,
        FRENCH,
        KOREAN
    }

    public enum Quality {
        MINT,
        NEAR_MINT,
        GOOD,
        PLAYED,
        POOR
    }

    public Quality quality;
    public Language language;
    public boolean isBorrowed;

    @ManyToOne
    public User owner;

    @ManyToOne
    public User borrower;

    @ManyToOne
    public CardReference cardReference;

    public Card(Language language, Quality quality, User owner, CardReference cardReference){
        this.language = language;
        this.quality = quality;
        this.owner = owner;
        this.cardReference = cardReference;
        this.isBorrowed = false;
    }

    public static List<BorrowableCardDto> findBorrowableCards(List<CardInput> cards, User user){
        List<BorrowableCardDto> borrowableCards = cards.stream().map(cardInput -> {
            BorrowableCardDto bCard = find("#Card.findBorrowableCards", Map.of("name", cardInput.name, "language", cardInput.language, "quality", cardInput.quality, "id", user.id, "location", user.getLocation())).project(BorrowableCardDto.class).firstResult();
            return bCard;
        }).toList();
        return borrowableCards;
    }

    public static Card inputToCard(CardInput input, User user) {
        CardReference cardReference = CardReference.findByName(input.name);
        if (cardReference == null) {
            throw new NotFoundException();
        }
        Card card = new Card(input.language, input.quality, user, cardReference);
        card.persist();
        return card;
    }
}

package org.acme.domain;

import java.time.Instant;
import java.util.List;

import org.acme.dto.DeckDto;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;

@Entity
public class Deck extends PanacheEntity {
    
    public String name;
    @OneToMany(cascade = CascadeType.ALL)
    public List<Card> cards;
    @ManyToOne
    public User owner;
    @CreationTimestamp
    private Instant createdAt;
    @UpdateTimestamp
    private Instant updatedAt;

    public static List<Deck> findDecksByUserId(User user) {
        return find("owner", user).list();
    }

    public static DeckDto mapDeckToDto(Deck deck, List<Card> missingCards){
        Double completion = missingCards.isEmpty() ? 100 : 100 - (((double) missingCards.size() / (double) deck.cards.size()) * 100);
        return new DeckDto(deck.id, deck.owner.id, deck.cards, missingCards, completion, deck.createdAt, deck.updatedAt);
    }

}

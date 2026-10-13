package org.acme.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.acme.dto.BorrowDto;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import io.quarkus.panache.common.Sort;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;

@Entity
public class Borrow extends PanacheEntity {

    @OneToOne
    public User owner;
    @OneToOne
    public User borrower;
    @OneToMany
    public List<Card> cards;

    public boolean returned = false;

    @CreationTimestamp
    private Instant createdAt;
    @UpdateTimestamp
    private Instant updatedAt;

    public Borrow(User owner, User borrower, List<Card> cards) {
        this.owner = owner;
        this.borrower = borrower;
        this.cards = cards;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public static List<Borrow> findByUserId(User user){
        return find("owner = ?1 OR borrower = ?2", Sort.descending("updatedAt"), user, user).list();
    }

    public static List<Borrow> createBorrowings(List<String> cardIds, User user){
        List<Card> cards = Card.findByIds(cardIds);

        Map<Long, List<Card>> byOwner = cards.stream().collect(Collectors.groupingBy(c -> c.owner.id));

        return byOwner.entrySet().stream().map(entry -> {
            User owner = User.findById(entry.getKey());
            Borrow b = new Borrow(owner, user, entry.getValue());
            b.persist();
            cards.forEach(card -> {
                card.borrower = user;
                card.isBorrowed = true;
                card.persist();
            });
            return b;
        }).toList();
    }

    public static BorrowDto mapBorrowingToDto(Borrow borrow) {
        return new BorrowDto(
                borrow.id,
                borrow.owner.id, borrow.owner.username,
                borrow.borrower.id, borrow.borrower.username,
                borrow.cards.stream().map(c -> c.id).toList(),
                borrow.returned,
                borrow.getCreatedAt(), borrow.getUpdatedAt());
    }

    public static List<BorrowDto> mapBorrowingsToDto(List<Borrow> borrowings) {
        return borrowings.stream().map(Borrow::mapBorrowingToDto).toList();
    }

}

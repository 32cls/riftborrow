package org.acme.api;

import java.net.URI;
import java.util.List;

import org.acme.domain.Card;
import org.acme.domain.Deck;
import org.acme.domain.User;
import org.acme.dto.DeckDto;
import org.acme.input.DeckInput;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

@Path("/decks")
@RolesAllowed("user")
public class DeckResource {

    @Inject
    SecurityIdentity identity;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public Response decks() {
        User authenticatedUser = User.findById(identity.<Long>getAttribute("userId"));
        List<Deck> decks = Deck.findDecksByUserId(authenticatedUser);
        return Response.ok(decks).build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    @Path("/{deckId}")
    public Response deck(@Context SecurityContext ctx, String deckId) {
        User authenticatedUser = User.findById(identity.<Long>getAttribute("userId"));
        Deck deck = Deck.findById(deckId);
        if (deck == null){
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        if (authenticatedUser.id != deck.owner.id) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        List<Card> missingCards = deck.cards.stream().filter(card -> !authenticatedUser.inventory.contains(card)).toList();
        DeckDto deckDto = Deck.mapDeckToDto(deck, missingCards);
        return Response.ok(deckDto).build();
    }

    @POST
    @Transactional
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createDeck(@Context SecurityContext ctx, DeckInput deckInput) {
        User authenticatedUser = User.findById(identity.<Long>getAttribute("userId"));
        Deck deck = new Deck();
        deck.owner = authenticatedUser;
        deck.name = deckInput.name;
        deck.cards = deckInput.cards.stream().map(cardInput -> Card.inputToCard(cardInput, authenticatedUser)).toList();
        deck.persist();
        return Response.created(URI.create("/decks/"+deck.id)).build();
    }

    @DELETE
    @Path("/{deckId}")
    @Transactional
    public Response deleteDeck(Long deckId) {
        User authenticatedUser = User.findById(identity.<Long>getAttribute("userId"));
        Deck deck = Deck.findById(deckId);
        if (deck == null){
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        if (deck.owner.id != authenticatedUser.id){
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        Deck.deleteById(deckId);
        return Response.noContent().build();
    }
}

package org.acme.api;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

import org.acme.domain.Card;
import org.acme.domain.Card.Language;
import org.acme.domain.Card.Quality;
import org.acme.domain.CardReference;
import org.acme.domain.User;
import org.acme.dto.BorrowableCardDto;
import org.acme.input.CardInput;
import org.jboss.resteasy.reactive.RestForm;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@RolesAllowed("user")
@Path("/cards")
public class CardResource {

    @Inject
    Validator validator;

    @Inject
    SecurityIdentity identity;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response listCards() {
        List<Card> cards = Card.listAll();
        return Response.ok(cards).build();
    }

    @POST
    @Transactional
    @Consumes(MediaType.APPLICATION_JSON)
    public Response addCard(CardInput cardInput) {
        Set<ConstraintViolation<CardInput>> violations = validator.validate(cardInput);
        if (violations.isEmpty()) {
            User authenticatedUser = User.findById(identity.<Long>getAttribute("userId"));
            Card card = Card.inputToCard(cardInput, authenticatedUser);
            return Response.created(URI.create("/cards/"+card.id)).build();
        } else {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
    }

    @POST
    @Transactional
    @Path("/list")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response addCardList(@RestForm("list") File file) {
        User authenticatedUser = User.findById(identity.<Long>getAttribute("userId"));
        if (!file.canRead()){
            return Response.status(Response.Status.BAD_REQUEST.getStatusCode(), "File cannot be read").build();
        }
        Scanner scanner;
        try {
            scanner = new Scanner(file);
            List<Card> cards = new ArrayList<>();
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                String[] parsedLine = line.split(" ", 2);
                int quantity = Integer.parseInt(parsedLine[0]);
                String parsedCardName = parsedLine[1].split(" \\(", 2)[0].replace(",", "").replace(" -", "");
                CardReference cardReference = CardReference.findByName(parsedCardName);
                for (int i = 0; i < quantity; i++) {
                    cards.add(new Card(Language.ENGLISH, Quality.NEAR_MINT, authenticatedUser, cardReference));
                }
                Card.persist(cards);
            }
            scanner.close();
        } catch (FileNotFoundException e){
            return Response.status(Response.Status.BAD_REQUEST.getStatusCode(), "File not found").build();
        }
        return Response.created(URI.create("/cards")).build();
    }

    @POST
    @Transactional
    @Path("/borrowable")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response findBorrowableCards(List<CardInput> cardsInput){
        User authenticatedUser = User.findById(identity.<Long>getAttribute("userId"));
        if (authenticatedUser.getLocation() == null) {
            return Response.status(422).build();
        }
        List<BorrowableCardDto> cards = Card.findBorrowableCards(cardsInput, authenticatedUser);
        return Response.ok(cards).build();
    }

    @DELETE
    @Path("/{cardId}")
    @Transactional
    public Response deleteCard(@PathParam("cardId") Long cardId) {
        User authenticatedUser = User.findById(identity.<Long>getAttribute("userId"));
        Card found = Card.findById(cardId);
        if (found == null){
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        if (found.owner.id != authenticatedUser.id){
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        Card.deleteById(cardId);
        return Response.noContent().build();
    }
}

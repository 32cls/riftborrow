package org.acme.api;

import java.util.List;

import org.acme.domain.Borrow;
import org.acme.domain.User;
import org.acme.dto.BorrowDto;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

@Path("/borrows")
@RolesAllowed("user")
public class BorrowResource {

    @Inject
    SecurityIdentity identity;

    @POST
    @Transactional
    @Consumes(MediaType.APPLICATION_JSON)
    public Response borrowCard(List<String> cardIds){
        User authenticatedUser = User.findById(identity.<Long>getAttribute("userId"));
        List<Borrow> borrowings = Borrow.createBorrowings(cardIds, authenticatedUser);
        List<BorrowDto> dtos = Borrow.mapBorrowingsToDto(borrowings);
        return Response.ok(dtos).build();
    }

    @PUT
    @Transactional
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response returnBorrow(Long borrowId){
        User authenticatedUser = User.findById(identity.<Long>getAttribute("userId"));
        Borrow borrow = Borrow.findById(borrowId);
        if (borrow.borrower.id != authenticatedUser.id) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        borrow.returned = true;
        borrow.persist();
        return Response.ok(borrow).build();
    }

    @GET
    @Transactional
    public Response listBorrowings(@Context SecurityContext ctx){
        User authenticatedUser = User.findById(identity.<Long>getAttribute("userId"));
        List<Borrow> borrowings = Borrow.findByUserId(authenticatedUser);
        List<BorrowDto> dtos = Borrow.mapBorrowingsToDto(borrowings);
        return Response.ok(dtos).build();
    }

}

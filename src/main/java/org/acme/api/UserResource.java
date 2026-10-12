package org.acme.api;

import org.acme.domain.User;
import org.acme.input.PointInput;
import org.locationtech.jts.geom.Coordinate;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/users")
@RolesAllowed("user")
public class UserResource {

    @Inject
    SecurityIdentity identity;

    @PATCH
    @Path("/location")
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public Response updateLocation(PointInput pointInput) {
        User authenticatedUser = User.findById(identity.<Long>getAttribute("userId"));
        authenticatedUser.updateLocation(new Coordinate(pointInput.X, pointInput.Y));
        return Response.ok().build();
    }

}

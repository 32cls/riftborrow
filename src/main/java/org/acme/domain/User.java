package org.acme.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import io.quarkus.hibernate.orm.panache.PanacheEntity;

import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;

import io.quarkus.security.jpa.Password;
import io.quarkus.security.jpa.Roles;
import io.quarkus.security.jpa.UserDefinition;
import io.quarkus.security.jpa.Username;

@Entity
@Table(name="app_users")
@UserDefinition
@JsonIdentityInfo (
  generator = ObjectIdGenerators.PropertyGenerator.class,
  property = "id")
public class User extends PanacheEntity {

    private static final int SRID = 4326;

    @Column(unique=true)
    @Username
    public String username;

    @Column(unique=true)
    public String discordId;

    @JsonIgnore
    public String issuer;

    @JsonIgnore
    @Password
    public String password;

    @Roles
    public String role;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "owner")
    public List<Card> inventory;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "borrower")
    public List<Card> borrowing;

    @JsonIgnore
    private Point location;

    @JsonIgnore
    public Point getLocation() {
		return location;
	}

    public void updateLocation(Coordinate coordinate) {
        GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), SRID);
        this.location = geometryFactory.createPoint(coordinate);
        this.persist();
    }

    public static User add(String issuer, String discordId, String username) {
        User user = new User();
        user.issuer = issuer;
        user.discordId = discordId;
        user.username = username;
        user.role = "user";
        user.persist();
        return user;
    }

    public static Long getOrCreate(String issuer, String discordId, String username) {
        User existing = findByDiscordId(discordId);
        if (existing == null){
            existing = User.add(issuer, discordId, username);
        }
        return existing.id;
    }

    public static User findByDiscordId(String discordId) {
        return find("discordId", discordId).firstResult();
    }

}

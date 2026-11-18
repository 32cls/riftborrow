package org.acme.domain;

import java.util.List;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Index;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.persistence.Entity;

import org.acme.external.CardReferenceDto;
import org.json.JSONArray;
import org.json.JSONObject;

@Entity
public class CardReference extends PanacheEntity {

    public static final String INDEX = "card_references";

    public String riftboundId;

    public String name;

    public String set;

    public String imageUrl;

    public boolean isNew;

    public CardReference(){
    }

    public CardReference(String riftboundId, String name, String set, String imageUrl, boolean isNew){
        this.riftboundId = riftboundId;
        this.name = name;
        this.set = set;
        this.imageUrl = imageUrl;
        this.isNew = isNew;
    }

    public static CardReference findByName(String name){
        return find("name", name).firstResult();
    }

    public static CardReference fromCardReferenceDto(CardReferenceDto cardReferenceDto) {
        return new CardReference(cardReferenceDto.riftboundId, normalizeName(cardReferenceDto.name), cardReferenceDto.set.setId, cardReferenceDto.media.imageUrl, cardReferenceDto.isNew);
    }

    // Catalog names are stored without "," and " -" (e.g. "Jinx, Loose Cannon" -> "Jinx Loose Cannon");
    // every lookup by name must go through this so both sides match.
    public static String normalizeName(String name) {
        return name == null ? null : name.strip().replace(",", "").replace(" -", "");
    }

    // Pushes every CardReference to Meilisearch in one call. The index is maintained only through
    // this (CardReferenceUpdaterBean), so a persist never depends on Meilisearch being up.
    public static void reindexAll() {
        List<CardReference> all = listAll();
        JSONArray documents = new JSONArray();
        all.forEach(cardReference -> documents.put(cardReference.toIndexDocument()));
        Index index = CDI.current().select(Client.class).get().index(INDEX);
        index.addDocuments(documents.toString(), "id");
        index.updateFilterableAttributesSettings(new String[]{"name", "riftbound_identifier"});
    }

    private JSONObject toIndexDocument() {
        return new JSONObject().put("id", this.id.toString()).put("riftbound_identifier", this.riftboundId).put("name", this.name).put("image_url", this.imageUrl);
    }
}

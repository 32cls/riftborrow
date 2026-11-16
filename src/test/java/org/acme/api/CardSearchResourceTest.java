package org.acme.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.Map;

import org.acme.dto.CardSearchDto;
import org.junit.jupiter.api.Test;

class CardSearchResourceTest {

    @Test
    void mapsIndexedDocument() {
        HashMap<String, Object> hit = new HashMap<>(Map.of(
                "id", "42",
                "riftbound_identifier", "OGN-001",
                "name", "Jinx Loose Cannon",
                "image_url", "https://example.org/jinx.png"));

        CardSearchDto dto = CardSearchResource.toDto(hit);

        assertEquals(42L, dto.id());
        assertEquals("OGN-001", dto.riftboundId());
        assertEquals("Jinx Loose Cannon", dto.name());
        assertEquals("https://example.org/jinx.png", dto.imageUrl());
    }

    @Test
    void toleratesMissingOrInvalidFields() {
        HashMap<String, Object> hit = new HashMap<>(Map.of("id", "not-a-number", "name", "Flash"));

        CardSearchDto dto = CardSearchResource.toDto(hit);

        assertNull(dto.id());
        assertEquals("Flash", dto.name());
        assertNull(dto.riftboundId());
        assertNull(dto.imageUrl());
    }
}

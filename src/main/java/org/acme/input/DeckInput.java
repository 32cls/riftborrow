package org.acme.input;

import java.util.List;

import jakarta.validation.constraints.NotBlank;

public class DeckInput {
    @NotBlank 
    public String name;
    @NotBlank 
    public List<CardInput> cards;
}

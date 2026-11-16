package org.acme.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record CardSearchDto(Long id, String riftboundId, String name, String imageUrl) {

}

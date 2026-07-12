package ar.com.viajar.dto.response;

public record PlaceSuggestionResponse(
        String placeId,
        String description,
        String mainText,
        String secondaryText
) {}

package ar.com.viajar.dto.response;

public record PlaceDetailsResponse(
        String placeId,
        String name,
        String formattedAddress,
        double lat,
        double lng
) {}

package ar.com.viajar.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "trip_segments")
@Getter
@Setter
public class TripSegment {

    @Id
    @UuidGenerator
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(columnDefinition = "text")
    private UUID id;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "trip_id", nullable = false, columnDefinition = "text")
    private UUID tripId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "from_stop_id", columnDefinition = "text")
    private UUID fromStopId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "to_stop_id", columnDefinition = "text")
    private UUID toStopId;

    @Column(name = "distance_km", nullable = false)
    private double distanceKm;

    @Column(name = "suggested_price", nullable = false)
    private double suggestedPrice;

    /** Precio por pasajero/asiento reservado en este tramo, no un total del viaje completo. */
    @Column(name = "final_price", nullable = false)
    private double finalPrice;

    @Column(name = "`order`", nullable = false)
    private int order;
}

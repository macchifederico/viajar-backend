package ar.com.viajar.domain;

import ar.com.viajar.domain.enums.BookingStatus;
import ar.com.viajar.domain.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bookings")
@Getter
@Setter
public class Booking {

    @Id
    @UuidGenerator
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(columnDefinition = "text")
    private UUID id;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "passenger_id", nullable = false, columnDefinition = "text")
    private UUID passengerId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "trip_id", nullable = false, columnDefinition = "text")
    private UUID tripId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "from_stop_id", columnDefinition = "text")
    private UUID fromStopId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "to_stop_id", columnDefinition = "text")
    private UUID toStopId;

    @Column(name = "seat_number", nullable = false)
    private int seatNumber;

    @Column(name = "total_price", nullable = false)
    private double totalPrice;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "payment_status", nullable = false)
    private PaymentStatus paymentStatus = PaymentStatus.pending;

    @Column(name = "mp_payment_id")
    private String mpPaymentId;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(nullable = false)
    private BookingStatus status = BookingStatus.confirmed;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "share_token", nullable = false, unique = true, columnDefinition = "text")
    private UUID shareToken;

    @Setter(AccessLevel.NONE)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}

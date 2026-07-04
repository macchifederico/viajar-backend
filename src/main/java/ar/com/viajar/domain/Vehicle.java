package ar.com.viajar.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vehicles")
@Getter
@Setter
public class Vehicle {

    @Id
    @UuidGenerator
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(columnDefinition = "text")
    private UUID id;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "driver_id", nullable = false, columnDefinition = "text")
    private UUID driverId;

    @Column(nullable = false)
    private String brand;

    @Column(nullable = false)
    private String model;

    @Column(nullable = false)
    private int year;

    @Column(nullable = false, unique = true)
    private String plate;

    @Column(nullable = false)
    private String color;

    @Column(name = "photo_url")
    private String photoUrl;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "cedula_url")
    private String cedulaUrl;

    @Column(name = "insurance_policy")
    private String insurancePolicy;

    @Column(name = "insurance_url")
    private String insuranceUrl;

    @Column(name = "insurance_expires_at")
    private Instant insuranceExpiresAt;

    @Column(name = "vtv_url")
    private String vtvUrl;

    @Column(name = "vtv_expires_at")
    private Instant vtvExpiresAt;

    @Column(nullable = false)
    private int doors = 4;

    @Column(name = "has_ac", nullable = false)
    private boolean hasAc = false;

    @Column(name = "has_seatbelts", nullable = false)
    private boolean hasSeatbelts = true;
}

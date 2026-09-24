package fr.emse.ismin.reservation.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Person who books rooms. Their location (building and floor) is used to
 * find the closest room during an automatic assignment.
 */
@Entity
@Table(name = "organizers")
@Getter
@Setter
@NoArgsConstructor
public class Organizer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "INTEGER")
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 254)
    private String email;

    /** Lower-cased email, unique, used to compare emails without considering case. */
    @Setter(AccessLevel.NONE)
    @Column(name = "normalized_email", nullable = false, unique = true, length = 254)
    private String normalizedEmail;

    @ManyToOne(optional = false)
    @JoinColumn(name = "building_id", nullable = false)
    private Building building;

    @Column(nullable = false)
    private Integer floor;

    /**
     * Sets the email and keeps its normalized key in sync.
     *
     * @param email the new email address
     */
    public void setEmail(String email) {
        this.email = email;
        this.normalizedEmail = TextNormalizer.normalize(email);
    }
}

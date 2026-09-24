package fr.emse.ismin.reservation.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Building of the university. Floors are numbered from {@code 0} (ground floor)
 * to {@code numberOfFloors - 1}.
 */
@Entity
@Table(name = "buildings")
@Getter
@Setter
@NoArgsConstructor
public class Building {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "INTEGER")
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    /** Lower-cased name, unique, used to compare names without considering case. */
    @Setter(AccessLevel.NONE)
    @Column(name = "normalized_name", nullable = false, unique = true, length = 100)
    private String normalizedName;

    @Column(name = "number_of_floors", nullable = false)
    private Integer numberOfFloors;

    /**
     * Sets the name and keeps its normalized key in sync.
     *
     * @param name the new name
     */
    public void setName(String name) {
        this.name = name;
        this.normalizedName = TextNormalizer.normalize(name);
    }
}

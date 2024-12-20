package searchengine.model;

import lombok.Data;

import javax.persistence.*;

@Data
@Entity
@Table(
        name = "`index`",
        uniqueConstraints = @UniqueConstraint(columnNames = {"page_id", "lemma_id"})
)
public class IndexEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "page_id", nullable = false)
    private PageEntity page;

    @ManyToOne
    @JoinColumn(name = "lemma_id", nullable = false)
    private LemmaEntity lemma;

    @Column(name = "`rank`", nullable = false) // Имя обернуто в обратные кавычки
    private Float rank;
}

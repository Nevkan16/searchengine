package searchengine.model;

import lombok.Data;

import javax.persistence.*;

@Data
@Entity
@Table(
        name = "index_table"
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

    @Column(name = "rank_in", nullable = false)
    private Float rank;
}

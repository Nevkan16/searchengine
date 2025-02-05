package searchengine.model;

import lombok.Data;

import javax.persistence.*;
import java.util.List;

@Data
@Entity
@Table(
        name = "page",
        uniqueConstraints = @UniqueConstraint(columnNames = {"site_id", "path"}),
        indexes = @Index(name = "idx_path", columnList = "path")
)
public class PageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "site_id", nullable = false)
    private SiteEntity site;

    @Column(name = "path", nullable = false, length = 255)
    private String path;

    @Column(name = "code", nullable = false)
    private Integer code;

    @Column(name = "content", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String content;

    @OneToMany(mappedBy = "page", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<IndexEntity> indexes;
}

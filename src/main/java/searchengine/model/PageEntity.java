package searchengine.model;

import lombok.Data;

import javax.persistence.*;

@Data
@Entity
@Table(name = "page", indexes = @Index(name = "idx_page_path", columnList = "path"))
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
}

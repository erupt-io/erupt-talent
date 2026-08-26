package xyz.erupt.talent.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Per-prefix, per-day counter backing the resume / offer numbering rule.
 * Not exposed as an erupt model — it is pure infrastructure for {@code SeqService}.
 *
 * @author YuePeng
 */
@Entity
@Table(name = "r_seq")
@Getter
@Setter
public class TalentSeq {

    /**
     * Composite natural key, e.g. {@code JL20260821}.
     */
    @Id
    @Column(length = 40)
    private String id;

    @Column(nullable = false)
    private Integer value;

}

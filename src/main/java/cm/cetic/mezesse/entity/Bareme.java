package cm.cetic.mezesse.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Barème modifiable par le SG : une ligne = une mention et sa borne basse (incluse). */
@Entity
@Table(name = "Bareme")
@Getter
@Setter
public class Bareme {

  @Id @Cuid private String id;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  private TypeBareme type;

  private String libelle;
  private String libelleLong;
  private BigDecimal min;
}

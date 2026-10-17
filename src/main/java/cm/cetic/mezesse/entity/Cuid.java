package cm.cetic.mezesse.entity;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.security.SecureRandom;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicInteger;
import org.hibernate.annotations.IdGeneratorType;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.BeforeExecutionGenerator;
import org.hibernate.generator.EventType;
import org.hibernate.generator.EventTypeSets;

/**
 * Identifiant de type cuid (« cmh0x2k7a0000abcd1234efgh ») : même forme que ceux créés par Prisma,
 * pour que les anciennes et les nouvelles lignes se ressemblent.
 */
@IdGeneratorType(Cuid.Generateur.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface Cuid {

  final class Generateur implements BeforeExecutionGenerator {

    private static final SecureRandom HASARD = new SecureRandom();
    private static final AtomicInteger COMPTEUR = new AtomicInteger(HASARD.nextInt(1 << 20));
    private static final String EMPREINTE = bloc(ProcessHandle.current().pid() * 31 + HASARD.nextInt(1 << 16), 4);

    @Override
    public Object generate(SharedSessionContractImplementor session, Object owner, Object actuel, EventType type) {
      return actuel != null ? actuel : nouveau();
    }

    @Override
    public EnumSet<EventType> getEventTypes() {
      return EventTypeSets.INSERT_ONLY;
    }

    @Override
    public boolean allowAssignedIdentifiers() {
      return true;
    }

    public static String nouveau() {
      long base = 36L * 36 * 36 * 36;
      return "c"
          + Long.toString(System.currentTimeMillis(), 36)
          + bloc(COMPTEUR.getAndIncrement() & 0x7fffffff, 4)
          + EMPREINTE
          + bloc(HASARD.nextLong(base), 4)
          + bloc(HASARD.nextLong(base), 4);
    }

    private static String bloc(long valeur, int longueur) {
      String s = Long.toString(Math.floorMod(valeur, (long) Math.pow(36, longueur)), 36);
      return "0".repeat(longueur - s.length()) + s;
    }
  }
}

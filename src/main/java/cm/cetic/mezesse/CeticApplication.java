package cm.cetic.mezesse;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CeticApplication {

  public static void main(String[] args) {
    // Les dates sont stockées en UTC (colonnes TIMESTAMP sans fuseau), comme le faisait Prisma.
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    SpringApplication.run(CeticApplication.class, args);
  }
}

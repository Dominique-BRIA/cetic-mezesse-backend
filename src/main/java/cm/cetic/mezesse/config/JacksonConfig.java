package cm.cetic.mezesse.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StringDeserializer;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Formats JSON : instants à la milliseconde, dates souples à la lecture, textes sans espaces autour. */
@Configuration
public class JacksonConfig {

  /** « 2026-10-16T21:23:12.345Z ». */
  static final DateTimeFormatter INSTANT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

  @Bean
  Jackson2ObjectMapperBuilderCustomizer formats() {
    return builder ->
        builder
            .serializerByType(
                Instant.class,
                new JsonSerializer<Instant>() {
                  @Override
                  public void serialize(Instant valeur, JsonGenerator gen, SerializerProvider p) throws IOException {
                    gen.writeString(INSTANT.format(valeur));
                  }
                })
            .deserializerByType(LocalDate.class, new DateSouple())
            .deserializerByType(String.class, new TexteNettoye());
  }

  /** Accepte « 2011-05-26 » comme « 2011-05-26T00:00:00.000Z » (dates envoyées par d'anciens clients). */
  static class DateSouple extends JsonDeserializer<LocalDate> {
    @Override
    public LocalDate deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
      String t = p.getValueAsString();
      if (t == null || t.isBlank()) return null;
      try {
        return LocalDate.parse(t.length() > 10 ? t.substring(0, 10) : t);
      } catch (DateTimeParseException e) {
        return (LocalDate) ctx.handleWeirdStringValue(LocalDate.class, t, "date invalide");
      }
    }
  }

  /**
   * Retire les espaces autour des textes reçus. Les mots de passe y échappent : leurs champs utilisent
   * le désérialiseur standard (@JsonDeserialize(using = StringDeserializer.class)).
   */
  static class TexteNettoye extends StringDeserializer {
    @Override
    public String deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
      String s = super.deserialize(p, ctx);
      return s == null ? null : s.strip();
    }
  }
}

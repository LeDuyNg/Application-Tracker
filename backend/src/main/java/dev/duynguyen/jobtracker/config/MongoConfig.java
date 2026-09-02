package dev.duynguyen.jobtracker.config;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

/**
 * Pins {@code LocalDate} storage to <strong>UTC midnight</strong>.
 *
 * <p><strong>Why this is not optional.</strong> Spring Data's default converts a
 * {@code LocalDate} to a BSON date using the <em>JVM's default timezone</em>. That makes the
 * stored value depend on where the process happens to run: {@code 2026-08-02} becomes
 * {@code 07:00Z} on a machine in Los Angeles, {@code 04:00Z} in New York, and {@code 00:00Z}
 * on the UTC VPS. Any arithmetic against those timestamps — "days to first response", date
 * range filters — then silently produces different answers in dev and prod, from identical
 * data.
 *
 * <p>Caught by {@code StatsServiceIT}, which computed an average of 5.1 days locally where
 * the hand-derived answer was 5.4. SCHEMA.md §7 specifies UTC midnight; these converters are
 * what actually enforce it.
 *
 * <p>{@code appliedDate} and {@code followUpDate} are calendar dates — "the day I applied" is
 * the same day regardless of who reads it — so a fixed offset is correct here. Genuine points
 * in time ({@code scheduledAt}, {@code completedAt}, {@code lastContactAt}) are {@code Instant}
 * and are unaffected.
 */
@Configuration
public class MongoConfig {

    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        return new MongoCustomConversions(List.of(
                LocalDateToDateConverter.INSTANCE,
                DateToLocalDateConverter.INSTANCE));
    }

    @WritingConverter
    enum LocalDateToDateConverter implements Converter<LocalDate, Date> {
        INSTANCE;

        @Override
        public Date convert(LocalDate source) {
            return Date.from(source.atStartOfDay(ZoneOffset.UTC).toInstant());
        }
    }

    @ReadingConverter
    enum DateToLocalDateConverter implements Converter<Date, LocalDate> {
        INSTANCE;

        @Override
        public LocalDate convert(Date source) {
            return Instant.ofEpochMilli(source.getTime()).atZone(ZoneOffset.UTC).toLocalDate();
        }
    }
}

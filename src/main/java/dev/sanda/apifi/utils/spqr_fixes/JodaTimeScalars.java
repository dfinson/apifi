package dev.sanda.apifi.utils.spqr_fixes;

import graphql.GraphQLException;
import graphql.schema.GraphQLScalarType;
import io.leangen.graphql.util.Scalars;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.joda.time.DateMidnight;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Duration;
import org.joda.time.Instant;
import org.joda.time.Interval;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.joda.time.LocalTime;
import org.joda.time.MonthDay;
import org.joda.time.Period;
import org.joda.time.PeriodType;
import org.joda.time.YearMonth;
import org.joda.time.chrono.ISOChronology;

@SuppressWarnings("WeakerAccess")
public class JodaTimeScalars {

  public static final GraphQLScalarType GraphQLInstant = Scalars.temporalScalar(
    Instant.class,
    "Moment",
    "an instant in time",
    Instant::parse,
    i -> new Instant(i.toEpochMilli())
  );

  @SuppressWarnings("deprecation")
  public static final GraphQLScalarType GraphQLDateMidnight = Scalars.temporalScalar(
    DateMidnight.class,
    "DateMidnight",
    "a date and midnight time with a time-zone",
    DateMidnight::parse,
    i -> new DateMidnight(i.toEpochMilli(), DateTimeZone.UTC)
  );

  public static final GraphQLScalarType GraphQLLocalTime = Scalars.temporalScalar(
    LocalTime.class,
    "TimeLocal",
    "a local time",
    LocalTime::parse,
    i -> utc(i).toLocalTime()
  );

  public static final GraphQLScalarType GraphQLLocalDate = Scalars.temporalScalar(
    LocalDate.class,
    "DateLocal",
    "a local date",
    LocalDate::parse,
    i -> utc(i).toLocalDate()
  );

  public static final GraphQLScalarType GraphQLLocalDateTime = Scalars.temporalScalar(
    LocalDateTime.class,
    "DateTimeLocal",
    "a local date-time",
    LocalDateTime::parse,
    i -> utc(i).toLocalDateTime()
  );

  public static final GraphQLScalarType GraphQLDateTime = Scalars.temporalScalar(
    DateTime.class,
    "DateTime",
    "a date-time with a time-zone",
    DateTime::parse,
    JodaTimeScalars::utc
  );

  public static final GraphQLScalarType GraphQLDateTimeZone = Scalars.temporalScalar(
    DateTimeZone.class,
    "DateTimeZone",
    "a time zone",
    DateTimeZone::forID,
    i -> DateTimeZone.forOffsetHours((int) i.toEpochMilli())
  );

  public static final GraphQLScalarType GraphQLInterval = Scalars.temporalScalar(
    Interval.class,
    "Interval",
    "a time interval",
    Interval::parse,
    i -> {
      throw new GraphQLException(
        "Interval can not be deserialized from a numeric value"
      );
    }
  );

  public static final GraphQLScalarType GraphQLDuration = Scalars.temporalScalar(
    Duration.class,
    "TimeSpan",
    "an amount of time",
    Duration::parse,
    i -> new Duration(i.toEpochMilli())
  );

  public static final GraphQLScalarType GraphQLPeriod = Scalars.temporalScalar(
    Period.class,
    "TimePeriod",
    "a time interval",
    Period::parse,
    i -> new Period(i.toEpochMilli(), PeriodType.yearMonthDayTime())
  );

  public static final GraphQLScalarType GraphQLMonthDay = Scalars.temporalScalar(
    MonthDay.class,
    "MonthDay",
    "a month and a day",
    MonthDay::parse,
    i -> new MonthDay(i.toEpochMilli(), ISOChronology.getInstanceUTC())
  );

  public static final GraphQLScalarType GraphQLYearMonth = Scalars.temporalScalar(
    YearMonth.class,
    "YearMonth",
    "a year and a month",
    YearMonth::parse,
    i -> new YearMonth(i.toEpochMilli(), ISOChronology.getInstanceUTC())
  );

  private static DateTime utc(java.time.Instant instant) {
    return new DateTime(instant.toEpochMilli(), DateTimeZone.UTC);
  }

  private static final Map<Type, GraphQLScalarType> SCALAR_MAPPING = getScalarMapping();

  public static boolean isScalar(Type javaType) {
    return SCALAR_MAPPING.containsKey(javaType);
  }

  public static GraphQLScalarType toGraphQLScalarType(Type javaType) {
    return SCALAR_MAPPING.get(javaType);
  }

  @SuppressWarnings("deprecation")
  private static Map<Type, GraphQLScalarType> getScalarMapping() {
    Map<Type, GraphQLScalarType> scalarMapping = new HashMap<>();
    scalarMapping.put(Instant.class, GraphQLInstant);
    scalarMapping.put(LocalDateTime.class, GraphQLLocalDateTime);
    scalarMapping.put(DateTime.class, GraphQLDateTime);
    scalarMapping.put(DateMidnight.class, GraphQLDateMidnight);
    scalarMapping.put(Interval.class, GraphQLInterval);
    scalarMapping.put(Duration.class, GraphQLDuration);
    scalarMapping.put(Period.class, GraphQLPeriod);
    scalarMapping.put(LocalTime.class, GraphQLLocalTime);
    scalarMapping.put(LocalDate.class, GraphQLLocalDate);
    scalarMapping.put(MonthDay.class, GraphQLMonthDay);
    scalarMapping.put(YearMonth.class, GraphQLYearMonth);
    scalarMapping.put(DateTimeZone.class, GraphQLDateTimeZone);
    return Collections.unmodifiableMap(scalarMapping);
  }
}

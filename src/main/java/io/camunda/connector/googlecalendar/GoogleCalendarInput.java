package io.camunda.connector.googlecalendar;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.cherrytemplate.CherryInput;
import io.camunda.connector.cherrytemplate.RunnerParameter;
import io.camunda.connector.googlecalendar.api.CalendarDescription;
import io.camunda.connector.googlecalendar.api.GoogleAuthenticate;
import io.camunda.connector.googlecalendar.api.GoogleCalendarAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * the JsonIgnoreProperties is mandatory: the template may contain additional widget to help the designer, especially on the OPTIONAL parameters
 * This avoids the MAPPING Exception
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GoogleCalendarInput implements CherryInput {
    /**
     * Attention, each Input here must be added in the GoogleCalendarFunction, list of InputVariables
     */
    public static final String GOOGLECALENDARFUNCTION = "Google Calendar Outbound Connector";
    protected static final String CALFUNCTION = "calFunction";

    /* ------------------------------------------------------------------ */
    /*  Function                                                           */
    /* ------------------------------------------------------------------ */
    protected static final String CALFUNCTION_V_CREATE = "CREATE";
    protected static final String CALFUNCTION_V_DELETE = "DELETE";
    protected static final String CALFUNCTION_V_DELETEOCCURRENCE = "DELETEOCCURRENCE";
    protected static final String CALFUNCTION_V_UPDATE = "UPDATE";
    protected static final String CALFUNCTION_V_UPDATEOCCURRENCE = "UPDATEOCCURRENCE";
    protected static final String CALFUNCTION_V_GETEVENT = "GETEVENT";
    protected static final String CALFUNCTION_V_SEARCHEVENT = "SEARCHEVENT";
    public static final RunnerParameter parameterFunction = new RunnerParameter(
            CALFUNCTION,
            "Function", String.class, RunnerParameter.Level.REQUIRED, "Operation to perform")
            .addChoice(CALFUNCTION_V_CREATE, "Create event")
            .addChoice(CALFUNCTION_V_DELETE, "Delete event")
            .addChoice(CALFUNCTION_V_DELETEOCCURRENCE, "Delete occurrence")
            .addChoice(CALFUNCTION_V_UPDATE, "Update event")
            .addChoice(CALFUNCTION_V_UPDATEOCCURRENCE, "Update occurrence")
            .addChoice(CALFUNCTION_V_GETEVENT, "Get event")
            .addChoice(CALFUNCTION_V_SEARCHEVENT, "Search events");
    protected static final String SOURCE_CALENDAR_ID = "sourceCalendarId";

    /* ------------------------------------------------------------------ */
    /*  Shared: eventId / calendarId                                       */
    /* ------------------------------------------------------------------ */
    // -- shared --
    public static final RunnerParameter parameterSourceCalendarId = new RunnerParameter(
            SOURCE_CALENDAR_ID, "Calendar ID", String.class, RunnerParameter.Level.REQUIRED,
            "ID of the calendar. A event is uniq in a calendar")
            .addCondition(CALFUNCTION, List.of(CALFUNCTION_V_DELETE, CALFUNCTION_V_DELETEOCCURRENCE,
                    CALFUNCTION_V_UPDATE, CALFUNCTION_V_UPDATEOCCURRENCE,
                    CALFUNCTION_V_GETEVENT, CALFUNCTION_V_SEARCHEVENT))
            .setDefaultValue(GoogleCalendarAPI.DEFAULT_CALENDAR)
            .setGroup("sourceevent");
    protected static final String SOURCE_DATE_FROM = "sourceDateFrom";
    public static final RunnerParameter parameterSourceDateFrom = new RunnerParameter(
            SOURCE_DATE_FROM, "From date", String.class, RunnerParameter.Level.REQUIRED,
            "Beginning of the search period, ISO-8601, e.g. \"2026-07-22T00:00:00-07:00\"")
            .addCondition(CALFUNCTION, List.of(CALFUNCTION_V_SEARCHEVENT, CALFUNCTION_V_UPDATEOCCURRENCE, CALFUNCTION_V_DELETEOCCURRENCE))
            .setGroup("sourceevent");
    protected static final String SOURCE_DATE_TO = "sourceDateTo";
    public static final RunnerParameter parameterSourceDateTo = new RunnerParameter(
            SOURCE_DATE_TO, "To date", String.class, RunnerParameter.Level.OPTIONAL,
            "End of the search period, ISO-8601, e.g. \"2026-07-23T00:00:00-07:00\"")
            .addCondition(CALFUNCTION, List.of(CALFUNCTION_V_SEARCHEVENT, CALFUNCTION_V_UPDATEOCCURRENCE, CALFUNCTION_V_DELETEOCCURRENCE))
            .setGroup("sourceevent");
    protected static final String SOURCE_TIME_ZONE = "sourceTimeZone";
    public static final RunnerParameter parameterSourceTimeZone = new RunnerParameter(
            SOURCE_TIME_ZONE, "Time zone", String.class, RunnerParameter.Level.OPTIONAL,
            "IANA timezone used for the dates returned in the result, e.g. 'Europe/Paris'")
            .addCondition(CALFUNCTION, List.of(CALFUNCTION_V_SEARCHEVENT))
            .setGroup("sourceevent");
    protected static final String SOURCE_EVENT_ID = "sourceEventId";
    public static final RunnerParameter parameterSourceEventId = new RunnerParameter(
            SOURCE_EVENT_ID, "Event ID", String.class, RunnerParameter.Level.REQUIRED,
            "ID of the event (master series ID for recurring events)")
            .addCondition(CALFUNCTION, List.of(CALFUNCTION_V_DELETE, CALFUNCTION_V_DELETEOCCURRENCE,
                    CALFUNCTION_V_UPDATE, CALFUNCTION_V_UPDATEOCCURRENCE, CALFUNCTION_V_GETEVENT))
            .setGroup("sourceevent");
    protected static final String CALENDAR_ID = "calendarId";
    public static final RunnerParameter parameterCalendarId = new RunnerParameter(
            CALENDAR_ID, "Calendar ID", String.class, RunnerParameter.Level.REQUIRED,
            "ID of the calendar where the event lives. Use 'primary' for the default calendar.")
            .addCondition(CALFUNCTION, List.of(CALFUNCTION_V_CREATE))
            .setDefaultValue(GoogleCalendarAPI.DEFAULT_CALENDAR)
            .setGroup("event");
    protected static final String TIME_START = "timeStart";
    public static final RunnerParameter parameterTimeStart = new RunnerParameter(
            TIME_START, "New start time", String.class, RunnerParameter.Level.OPTIONAL,
            "New time of day for the start of each matched occurrence, e.g. \"15:00\" or \"15:00:00\". The occurrence's own date is kept; leave empty to keep the current start time.")
            .addCondition(CALFUNCTION, List.of(CALFUNCTION_V_UPDATEOCCURRENCE))
            .setGroup("event");
    protected static final String TIME_END = "timeEnd";
    public static final RunnerParameter parameterTimeEnd = new RunnerParameter(
            TIME_END, "New end time", String.class, RunnerParameter.Level.OPTIONAL,
            "New time of day for the end of each matched occurrence, e.g. \"15:30\" or \"15:30:00\". Only applied when New start time is also set; leave empty to keep the current end time.")
            .addCondition(CALFUNCTION, List.of(CALFUNCTION_V_UPDATEOCCURRENCE))
            .setGroup("event");
    protected static final String START_DATE = "startDate";

    /* ------------------------------------------------------------------ */
    /*  CalendarDescription fields                                         */
    /* ------------------------------------------------------------------ */
    public static final RunnerParameter parameterStartDate = new RunnerParameter(
            START_DATE, "Start date", Long.class, RunnerParameter.Level.REQUIRED,
            "Start date and time of the event, for example \"2026-07-22T10:00:00-07:00\" (timezone can be given in a separate field, or with the field)")
            .addCondition(CALFUNCTION, List.of(CALFUNCTION_V_CREATE))
            .setGroup("event");
    protected static final String END_DATE = "endDate";
    public static final RunnerParameter parameterEndDate = new RunnerParameter(
            END_DATE, "End date", Long.class, RunnerParameter.Level.REQUIRED, "End date and time of the event, for example \"2026-07-22T10:00:00-07:00\" ")
            .addCondition(CALFUNCTION, List.of(CALFUNCTION_V_CREATE))
            .setGroup("event");
    protected static final String TIME_ZONE = "timeZone";
    public static final RunnerParameter parameterTimeZone = new RunnerParameter(
            TIME_ZONE, "Time zone", String.class, RunnerParameter.Level.OPTIONAL,
            "IANA timezone, e.g. 'Europe/Paris'. Used for timed events only.")
            .addCondition(CALFUNCTION, List.of(CALFUNCTION_V_CREATE, CALFUNCTION_V_UPDATE, CALFUNCTION_V_UPDATEOCCURRENCE))
            .setGroup("event");
    protected static final String TITLE = "title";
    public static final RunnerParameter parameterTitle = new RunnerParameter(
            TITLE, "Title", String.class, RunnerParameter.Level.REQUIRED, "Title of the event (summary)")
            .addCondition(CALFUNCTION, List.of(CALFUNCTION_V_CREATE))
            .setGroup("event");
    protected static final String DESCRIPTION = "description";
    public static final RunnerParameter parameterDescription = new RunnerParameter(
            DESCRIPTION, "Description", String.class, RunnerParameter.Level.OPTIONAL,
            "Free-text description shown in the event detail view")
            .addCondition(CALFUNCTION, List.of(CALFUNCTION_V_CREATE, CALFUNCTION_V_UPDATE, CALFUNCTION_V_UPDATEOCCURRENCE))
            .setGroup("event");
    protected static final String LIST_ATTENDES = "listAttendes";
    public static final RunnerParameter parameterListAttendes = new RunnerParameter(
            LIST_ATTENDES, "Attendees", List.class, RunnerParameter.Level.OPTIONAL,
            "List of attendee email addresses")
            .addCondition(CALFUNCTION, List.of(CALFUNCTION_V_CREATE, CALFUNCTION_V_UPDATE, CALFUNCTION_V_UPDATEOCCURRENCE))
            .setGroup("event");
    protected static final String CREATE_GOOGLE_MEET = "createGoogleMeet";
    public static final RunnerParameter parameterCreateGoogleMeet = new RunnerParameter(
            CREATE_GOOGLE_MEET, "Create Google Meet", Boolean.class, RunnerParameter.Level.OPTIONAL,
            "When true, a Google Meet video-conference link is generated")
            .addCondition(CALFUNCTION, List.of(CALFUNCTION_V_CREATE, CALFUNCTION_V_UPDATE, CALFUNCTION_V_UPDATEOCCURRENCE))
            .setDefaultValue(false)
            .setVisibleInTemplate()
            .setGroup("event");
    protected static final String ALL_DAY = "allDay";
    public static final RunnerParameter parameterAllDay = new RunnerParameter(
            ALL_DAY, "All day", Boolean.class, RunnerParameter.Level.OPTIONAL,
            "When true, the event spans full days with no time-of-day stored")
            .addCondition(CALFUNCTION, List.of(CALFUNCTION_V_CREATE))
            .setVisibleInTemplate()
            .setDefaultValue(false)
            .setGroup("event");
    protected static final String LOCALISATION = "localisation";
    public static final RunnerParameter parameterLocalisation = new RunnerParameter(
            LOCALISATION, "Location", String.class, RunnerParameter.Level.OPTIONAL,
            "Physical location of the event, e.g. '10 Rue de Rivoli, Paris'")
            .addCondition(CALFUNCTION, List.of(CALFUNCTION_V_CREATE, CALFUNCTION_V_UPDATE, CALFUNCTION_V_UPDATEOCCURRENCE))
            .setGroup("event");
    protected static final String TRANSPARENCY = "transparency";
    public static final RunnerParameter parameterTransparency = new RunnerParameter(
            TRANSPARENCY, "Transparency", String.class, RunnerParameter.Level.OPTIONAL,
            "Free/busy status: OPAQUE = busy (default), TRANSPARENT = available")
            .addChoice("OPAQUE", "Busy")
            .addChoice("TRANSPARENT", "Available / Free")
            .addCondition(CALFUNCTION, List.of(CALFUNCTION_V_CREATE, CALFUNCTION_V_UPDATE, CALFUNCTION_V_UPDATEOCCURRENCE))
            .setGroup("event");
    protected static final String VISIBILITY = "visibility";
    public static final RunnerParameter parameterVisibility = new RunnerParameter(
            VISIBILITY, "Visibility", String.class, RunnerParameter.Level.OPTIONAL,
            "Who can see event details")
            .addChoice("DEFAULT", "Default (inherits calendar setting)")
            .addChoice("PUBLIC", "Public")
            .addChoice("PRIVATE", "Private (attendees only)")
            .addChoice("CONFIDENTIAL", "Confidential")
            .addCondition(CALFUNCTION, List.of(CALFUNCTION_V_CREATE, CALFUNCTION_V_UPDATE, CALFUNCTION_V_UPDATEOCCURRENCE))
            .setGroup("event");
    protected static final String COLOR = "color";
    public static final RunnerParameter parameterColor = new RunnerParameter(
            COLOR, "Color", String.class, RunnerParameter.Level.OPTIONAL,
            "Color displayed for this event in the calendar UI")
            .addChoice("TOMATO", "Tomato")
            .addChoice("FLAMINGO", "Flamingo")
            .addChoice("TANGERINE", "Tangerine")
            .addChoice("BANANA", "Banana")
            .addChoice("SAGE", "Sage")
            .addChoice("BASIL", "Basil")
            .addChoice("PEACOCK", "Peacock")
            .addChoice("BLUEBERRY", "Blueberry")
            .addChoice("LAVENDER", "Lavender")
            .addChoice("GRAPE", "Grape")
            .addChoice("GRAPHITE", "Graphite")
            .addCondition(CALFUNCTION, List.of(CALFUNCTION_V_CREATE, CALFUNCTION_V_UPDATE, CALFUNCTION_V_UPDATEOCCURRENCE))
            .setGroup("event");
    protected static final String ALL_AFTER_THIS_OCCURRENCE = "allAfterThisOccurrence";
    public static final RunnerParameter parameterAllAfterThisOccurrence = new RunnerParameter(
            ALL_AFTER_THIS_OCCURRENCE, "Delete all after this occurrence?", Boolean.class, RunnerParameter.Level.OPTIONAL,
            "When true, all occurrences on or after the given date are also deleted")
            .addCondition(CALFUNCTION, List.of(CALFUNCTION_V_DELETEOCCURRENCE, CALFUNCTION_V_UPDATEOCCURRENCE))
            .setVisibleInTemplate()
            .setDefaultValue(false)
            .setGroup("event");
    protected static final String RECURRING = "recurring";

    /* ------------------------------------------------------------------ */
    /*  Recurrence fields (CREATE only): map to CalendarDescription.RecurrenceRule */
    /* ------------------------------------------------------------------ */
    public static final RunnerParameter parameterRecurring = new RunnerParameter(
            RECURRING, "Recurring event", Boolean.class, RunnerParameter.Level.OPTIONAL,
            "When true, the event repeats according to the recurrence fields below")
            .addCondition(CALFUNCTION, List.of(CALFUNCTION_V_CREATE))
            .setVisibleInTemplate()
            .setGroup("event");
    protected static final String RECURRING_FREQUENCY = "recurringFrequency";
    public static final RunnerParameter parameterRecurringFrequency = new RunnerParameter(
            RECURRING_FREQUENCY, "Frequency", String.class, RunnerParameter.Level.REQUIRED,
            "How often the event repeats")
            .addChoice("DAILY", "Daily")
            .addChoice("WEEKLY", "Weekly")
            .addChoice("MONTHLY", "Monthly")
            .addChoice("YEARLY", "Yearly")
            .addCondition(RECURRING, List.of("true"))
            .setVisibleInTemplate()
            .setGroup("event");
    protected static final String RECURRING_INTERVAL = "recurringInterval";
    public static final RunnerParameter parameterRecurringInterval = new RunnerParameter(
            RECURRING_INTERVAL, "Interval", Integer.class, RunnerParameter.Level.OPTIONAL,
            "Repeat every N units of the chosen frequency. Default: 1")
            .addCondition(RECURRING, List.of("true"))
            .setVisibleInTemplate()
            .setGroup("event");
    protected static final String RECURRING_COUNT = "recurringCount";
    public static final RunnerParameter parameterRecurringCount = new RunnerParameter(
            RECURRING_COUNT, "Count", Integer.class, RunnerParameter.Level.OPTIONAL,
            "Maximum number of occurrences. Leave empty for no limit (or use Until instead)")
            .addCondition(RECURRING, List.of("true"))
            .setVisibleInTemplate()
            .setGroup("event");
    protected static final String RECURRING_UNTIL = "recurringUntil";
    public static final RunnerParameter parameterRecurringUntil = new RunnerParameter(
            RECURRING_UNTIL, "Until", String.class, RunnerParameter.Level.OPTIONAL,
            "Last date of the series (inclusive). Format: \"yyyyMMdd\" for all-day events, \"yyyyMMddTHHmmssZ\" for timed events. Ignored when Count is set")
            .addCondition(RECURRING, List.of("true"))
            .setVisibleInTemplate()
            .setGroup("event");
    protected static final String RECURRING_DAYS_OF_WEEK = "recurringDaysOfWeek";
    public static final RunnerParameter parameterRecurringDaysOfWeek = new RunnerParameter(
            RECURRING_DAYS_OF_WEEK, "Days of week", String.class, RunnerParameter.Level.OPTIONAL,
            "Days of the week for WEEKLY rules, separated by ',' or ';'. Valid values: MO, TU, WE, TH, FR, SA, SU. Example: \"MO,WE\"")
            .addCondition(RECURRING, List.of("true"))
            .setVisibleInTemplate()
            .setGroup("event");
    // bind to startDate
    protected static final String START_DATE_MODIFY = "startDateModify";

    /* ------------------------------------------------------------------ */
    /*  UPDATE-specific fields: null means "keep the current value"        */
    /* ------------------------------------------------------------------ */
    public static final RunnerParameter parameterStartDateModify = new RunnerParameter(
            START_DATE_MODIFY, "New start date", Long.class, RunnerParameter.Level.OPTIONAL,
            "New start date and time of the event, for example \"2026-07-22T10:00:00-07:00\". Leave empty to keep the current start date.")
            .addCondition(CALFUNCTION, List.of(CALFUNCTION_V_UPDATE))
            .setBindingName(START_DATE)
            .setGroup("event");
    ///  bind to EndDate
    protected static final String END_DATE_MODIFY = "endDateModify";
    public static final RunnerParameter parameterEndDateModify = new RunnerParameter(
            END_DATE_MODIFY, "New end date", Long.class, RunnerParameter.Level.OPTIONAL,
            "New end date and time of the event. Leave empty to keep the current end date.")
            .addCondition(CALFUNCTION, List.of(CALFUNCTION_V_UPDATE))
            .setBindingName(END_DATE)
            .setGroup("event");
    // bind to title
    protected static final String TITLE_MODIFY = "titleModify";

    /* ------------------------------------------------------------------ */
    /*  GoogleAuthenticate fields                                          */
    /* ------------------------------------------------------------------ */
    public static final RunnerParameter parameterTitleModify = new RunnerParameter(
            TITLE_MODIFY, "New title", String.class, RunnerParameter.Level.OPTIONAL,
            "New title of the event (summary). Leave empty to keep the current title.")
            .addCondition(CALFUNCTION, List.of(CALFUNCTION_V_UPDATE, CALFUNCTION_V_UPDATEOCCURRENCE))
            .setBindingName(TITLE)
            .setGroup("event");
    // bind to calendarId
    protected static final String CALENDAR_ID_MODIFY = "calendarIdModify";
    public static final RunnerParameter parameterCalendarIdModify = new RunnerParameter(
            CALENDAR_ID_MODIFY, "Move to calendar ID", String.class,
            RunnerParameter.Level.OPTIONAL,
            "Calendarid to retrieve the event. An event is unique IN calendar.")
            .addCondition(CALFUNCTION, List.of(CALFUNCTION_V_UPDATE, CALFUNCTION_V_UPDATEOCCURRENCE))
            .setDefaultValue(GoogleCalendarAPI.DEFAULT_CALENDAR)
            .setBindingName(CALENDAR_ID)
            .setGroup("event");
    protected static final String CLIENT_ID = "clientId";
    public static final RunnerParameter parameterClientId = new RunnerParameter(
            CLIENT_ID, "Client ID", String.class, RunnerParameter.Level.REQUIRED,
            "OAuth2 Client ID from Google Cloud Console (APIs & Services → Credentials)")
            .setGroup("authenticate");
    protected static final String CLIENT_SECRET = "clientSecret";
    public static final RunnerParameter parameterClientSecret = new RunnerParameter(
            CLIENT_SECRET, "Client secret", String.class, RunnerParameter.Level.REQUIRED,
            "OAuth2 Client Secret from Google Cloud Console")
            .setGroup("authenticate");
    protected static final String REFRESH_TOKEN = "refreshToken";
    public static final RunnerParameter parameterRefreshToken = new RunnerParameter(
            REFRESH_TOKEN, "Refresh token", String.class, RunnerParameter.Level.REQUIRED,
            "OAuth2 Refresh Token obtained after the user grants calendar access")
            .setGroup("authenticate");
    protected static final String APPLICATION_NAME = "applicationName";
    public static final RunnerParameter parameterApplicationName = new RunnerParameter(
            APPLICATION_NAME, "Application name", String.class, RunnerParameter.Level.OPTIONAL,
            "Name shown in Google API quota dashboard. Defaults to 'Camunda Google Calendar Connector'.")
            .setGroup("authenticate");
    @JsonIgnore
    private final Logger logger = LoggerFactory.getLogger(GoogleCalendarInput.class.getName());
    @JsonIgnore
    public List<RunnerParameter> inputParametersList = List.of(
            parameterFunction,

            parameterSourceCalendarId,
            parameterSourceEventId,
            parameterSourceDateFrom,
            parameterSourceDateTo,
            parameterSourceTimeZone,

            parameterCalendarId,
            parameterCalendarIdModify,
            parameterTimeStart,
            parameterTimeEnd,
            parameterAllDay,
            parameterStartDate,
            parameterStartDateModify,
            parameterEndDate,
            parameterEndDateModify,
            parameterTimeZone,
            parameterTitle,
            parameterTitleModify,
            parameterDescription,
            parameterListAttendes,
            parameterCreateGoogleMeet,
            parameterLocalisation,
            parameterTransparency,
            parameterVisibility,
            parameterColor,
            parameterRecurring,
            parameterRecurringFrequency,
            parameterRecurringInterval,
            parameterRecurringCount,
            parameterRecurringUntil,
            parameterRecurringDaysOfWeek,
            parameterAllAfterThisOccurrence,

            // GoogleAuthenticate
            parameterClientId,
            parameterClientSecret,
            parameterRefreshToken,
            parameterApplicationName);
    private String calFunction;
    private String sourceCalendarId;
    // SEARCHEVENT: search period and optional time zone
    private String sourceDateFrom;
    private String sourceDateTo;
    private String sourceTimeZone;
    private String sourceEventId;
    private String calendarId;
    // UPDATEOCCURRENCE only: replaces the time-of-day of matched occurrences, keeping their own date
    private String timeStart;
    private String timeEnd;
    // Dates are bound as ISO-8601 Strings (FEEL date and time), then parsed to ZonedDateTime:
    // Jackson cannot deserialize protobuf types like com.google.type.DateTime
    private String startDate;
    private String endDate;
    private String timeZone;
    private String title;
    private String description;
    private List<String> listAttendes;
    private boolean createGoogleMeet;
    private boolean allDay;
    private String localisation;
    private String transparency;
    private String visibility;
    private String color;
    private boolean allAfterThisOccurrence;
    private boolean recurring;
    private String recurringFrequency;
    private Integer recurringInterval;
    private Integer recurringCount;
    private String recurringUntil;
    private String recurringDaysOfWeek;
    private String clientId;
    private String clientSecret;
    private String refreshToken;
    private String applicationName;

    /**
     * Parses an ISO-8601 date value. Accepts a FEEL "date and time" with zone or offset
     * (e.g. "2026-07-22T10:00:00-07:00[America/Los_Angeles]"), a local date-time
     * (e.g. "2026-07-22T10:00:00", interpreted in {@code defaultZone}), or a date only
     * ("2026-07-22"). A value carrying its own offset/zone wins over {@code defaultZone}.
     *
     * @throws DateTimeParseException when the value matches none of the accepted formats
     */
    @JsonIgnore
    private static ZonedDateTime parseDateTime(String value, ZoneId defaultZone) {
        if (value == null || value.isBlank())
            return null;
        try {
            return ZonedDateTime.parse(value);
        } catch (DateTimeParseException e) {
            // not a zoned/offset date-time, try the next format
        }
        try {
            return LocalDateTime.parse(value).atZone(defaultZone);
        } catch (DateTimeParseException e) {
            // not a local date-time, try date only
        }
        return LocalDate.parse(value).atStartOfDay(defaultZone);
    }

    /**
     * True when the value carries its own offset or zone (e.g. "2026-07-22T10:00:00-07:00").
     */
    private static boolean hasExplicitZone(String value) {
        if (value == null || value.isBlank())
            return false;
        try {
            ZonedDateTime.parse(value);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    /**
     * Resolves a timezone parameter to a ZoneId; empty means UTC.
     */
    private static ZoneId zoneOrUTC(String timeZoneValue, String parameterName) {
        if (timeZoneValue == null || timeZoneValue.isBlank())
            return ZoneOffset.UTC;
        try {
            return ZoneId.of(timeZoneValue);
        } catch (DateTimeException e) {
            throw new ConnectorException(GoogleCalendarError.DATE_PARSING_ERROR,
                    "Source [" + parameterName + "]: unknown timezone [" + timeZoneValue
                            + "]. Expected an IANA identifier, e.g. \"America/Los_Angeles\"");
        }
    }

    /**
     * Parses a local time of day, e.g. "15:00" or "15:00:00" (no offset/zone).
     */
    private static LocalTime parseTime(String value, String parameterName) {
        if (value == null || value.isBlank())
            return null;
        try {
            return LocalTime.parse(value);
        } catch (DateTimeParseException e) {
            throw new ConnectorException(GoogleCalendarError.DATE_PARSING_ERROR,
                    "Source [" + parameterName + "]: cannot parse time [" + value
                            + "]. Expected a local time, e.g. \"15:00\" or \"15:00:00\"");
        }
    }

    private static ConnectorException buildDateParsingException(String source, String value) {
        return new ConnectorException(GoogleCalendarError.DATE_PARSING_ERROR,
                "Source [" + source + "]: cannot parse date [" + value
                        + "]. Expected ISO-8601, e.g. \"2026-07-22T10:00:00-07:00\" or \"2026-07-22\"");
    }

    /**
     * Formats a ZonedDateTime to RRULE date format (yyyyMMdd for dates, yyyyMMddTHHmmssZ for datetimes).
     */
    private static String formatToRRuleDate(ZonedDateTime dateTime) {
        if (dateTime.toLocalTime().equals(LocalTime.MIDNIGHT))
            return DateTimeFormatter.ofPattern("yyyyMMdd").format(dateTime);
        return DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC).format(dateTime);
    }

    /**
     * Validates that the value matches RRULE format: yyyyMMdd or yyyyMMddTHHmmssZ.
     */
    private static boolean isValidRRuleFormat(String value) {
        if (value == null || value.isBlank())
            return false;
        return value.matches("\\d{8}(T\\d{6}Z)?");
    }

    /* ------------------------------------------------------------------ */
    /*  Build complete objects                                             */
    /* ------------------------------------------------------------------ */
    @JsonIgnore
    public CalendarDescription getCalendarDescription() {
        CalendarDescription calendarDescription = new CalendarDescription();
        try {
            calendarDescription.startDate = parseDateTime(startDate, zoneOrUTC(timeZone, TIME_ZONE));
        } catch (DateTimeParseException e) {
            throw buildDateParsingException(START_DATE, startDate);
        }
        try {
            calendarDescription.endDate = parseDateTime(endDate, zoneOrUTC(timeZone, TIME_ZONE));
        } catch (DateTimeParseException e) {
            throw buildDateParsingException(END_DATE, endDate);
        }
        // Flag wall times given without offset and without a timeZone parameter:
        // on UPDATE they must be re-anchored in the current event's timezone
        boolean noTimeZoneParameter = timeZone == null || timeZone.isBlank();
        calendarDescription.startDateIsLocal = noTimeZoneParameter
                && calendarDescription.startDate != null && !hasExplicitZone(startDate);
        calendarDescription.endDateIsLocal = noTimeZoneParameter
                && calendarDescription.endDate != null && !hasExplicitZone(endDate);
        calendarDescription.timeZone = getTimeZone();
        calendarDescription.title = getTitle();
        calendarDescription.description = getDescription();
        calendarDescription.listAttendes = getListAttendes();
        calendarDescription.createGoogleMeet = createGoogleMeet;
        calendarDescription.allDay = allDay;
        calendarDescription.localisation = localisation;
        calendarDescription.calendarId = calendarId;
        if (transparency != null)
            calendarDescription.transparency = CalendarDescription.Transparency.valueOf(transparency);
        if (visibility != null)
            calendarDescription.visibility = CalendarDescription.Visibility.valueOf(visibility);
        if (color != null)
            calendarDescription.color = CalendarDescription.EventColor.valueOf(color);
        calendarDescription.recurrenceRule = getRecurrenceRule();
        return calendarDescription;
    }

    /* ------------------------------------------------------------------ */
    /*  Cherry / RunnerParameter definitions                               */
    /* ------------------------------------------------------------------ */

    /**
     * Builds the RecurrenceRule from the recurring* fields; null when {@link #recurring} is false.
     */
    @JsonIgnore
    private CalendarDescription.RecurrenceRule getRecurrenceRule() {
        if (!recurring)
            return null;
        CalendarDescription.RecurrenceRule rule = new CalendarDescription.RecurrenceRule();
        if (recurringFrequency == null || recurringFrequency.isBlank())
            throw new ConnectorException(GoogleCalendarError.INCOMPLETE_PARAMETERS,
                    "Parameter [" + RECURRING_FREQUENCY + "] is required when [" + RECURRING + "] is true");
        try {
            rule.frequency = CalendarDescription.RecurrenceRule.Frequency.valueOf(recurringFrequency);
        } catch (IllegalArgumentException e) {
            throw new ConnectorException(GoogleCalendarError.BAD_INPUTPARAMETER,
                    "Parameter [" + RECURRING_FREQUENCY + "]: unknown frequency [" + recurringFrequency
                            + "]. Expected DAILY, WEEKLY, MONTHLY or YEARLY");
        }
        if (recurringInterval != null)
            rule.interval = recurringInterval;
        rule.count = recurringCount;
        rule.until = formatRecurringUntil(recurringUntil);
        if (recurringDaysOfWeek != null && !recurringDaysOfWeek.isBlank()) {
            // accept "MO;TU" as well as "MO, WE"
            rule.daysOfWeek = Arrays.stream(recurringDaysOfWeek.split("[;,]"))
                    .map(String::trim)
                    .filter(day -> !day.isEmpty())
                    .toList();
        }
        return rule;
    }

    @JsonIgnore
    public GoogleAuthenticate getGoogleAuthenticate() {
        GoogleAuthenticate googleAuthenticate = new GoogleAuthenticate();
        googleAuthenticate.setClientId(clientId);
        googleAuthenticate.setClientSecret(clientSecret);
        googleAuthenticate.setRefreshToken(refreshToken);
        googleAuthenticate.setApplicationName(applicationName);
        return googleAuthenticate;
    }

    public String getCalFunction() {
        return calFunction;
    }

    public String getSourceCalendarId() {
        return sourceCalendarId;
    }

    public String getSourceEventId() {
        return sourceEventId;
    }

    public ZonedDateTime getSourceDateFrom() {
        try {
            return parseDateTime(sourceDateFrom, zoneOrUTC(sourceTimeZone, SOURCE_TIME_ZONE));
        } catch (DateTimeParseException e) {
            throw buildDateParsingException(SOURCE_DATE_FROM, sourceDateFrom);
        }
    }

    public ZonedDateTime getSourceDateTo() {
        try {
            return parseDateTime(sourceDateTo, zoneOrUTC(sourceTimeZone, SOURCE_TIME_ZONE));
        } catch (DateTimeParseException e) {
            throw buildDateParsingException(SOURCE_DATE_TO, sourceDateTo);
        }
    }

    public String getSourceTimeZone() {
        return sourceTimeZone;
    }

    public String getCalendarId() {
        return calendarId;
    }

    public ZonedDateTime getStartDate() {
        try {
            return parseDateTime(startDate, zoneOrUTC(timeZone, TIME_ZONE));
        } catch (DateTimeParseException e) {
            throw buildDateParsingException(START_DATE, startDate);
        }
    }

    public ZonedDateTime getEndDate() {
        try {
            return parseDateTime(endDate, zoneOrUTC(timeZone, TIME_ZONE));
        } catch (DateTimeParseException e) {
            throw buildDateParsingException(END_DATE, endDate);
        }
    }

    // -- CalendarDescription --

    public String getTimeZone() {
        return timeZone;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getListAttendes() {
        return listAttendes;
    }

    public boolean isCreateGoogleMeet() {
        return createGoogleMeet;
    }

    public boolean isAllDay() {
        return allDay;
    }

    public String getLocalisation() {
        return localisation;
    }

    public String getTransparency() {
        return transparency;
    }

    public String getVisibility() {
        return visibility;
    }

    public String getColor() {
        return color;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    // -- Recurrence (CREATE only) --

    public String getRefreshToken() {
        return refreshToken;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public boolean isAllAfterThisOccurrence() {
        return allAfterThisOccurrence;
    }

    public boolean isRecurring() {
        return recurring;
    }

    public String getRecurringFrequency() {
        return recurringFrequency;
    }

    public Integer getRecurringInterval() {
        return recurringInterval;
    }


    // -- UPDATE-specific: new values to apply; leave empty to keep the current value --

    public Integer getRecurringCount() {
        return recurringCount;
    }

    public String getRecurringUntil() {
        return recurringUntil;
    }

    public String getRecurringDaysOfWeek() {
        return recurringDaysOfWeek;
    }


    // -- GoogleAuthenticate --

    public String getTimeStart() {
        return timeStart;
    }

    public String getTimeEnd() {
        return timeEnd;
    }

    /**
     * Parses {@link #timeStart}. Named getStartTime() (not getTimeStart()) as requested;
     * the explicit @JsonProperty is required so Jackson still binds it from the "timeStart"
     * input variable despite the getter name not matching the field name.
     */
    @JsonProperty(TIME_START)
    public LocalTime getTimeStartLocalTime() {
        if (timeStart == null) {
            return null;
        }
        return parseTime(timeStart, TIME_START);
    }

    /**
     * Parses {@link #timeEnd}. See {@link #getTimeStartLocalTime()} for the @JsonProperty note.
     */
    @JsonProperty(TIME_END)
    public LocalTime getTimeEndLocalTime() {
        if (timeEnd == null) {
            return null;
        }
        return parseTime(timeEnd, TIME_END);
    }

    /* ------------------------------------------------------------------ */
    /*  Parameter list                                                     */
    /* ------------------------------------------------------------------ */

    /**
     * Validates and formats the recurring until value. Accepts ZonedDateTime/LocalDateTime or yyyyMMdd/yyyyMMddTHHmmssZ format.
     */
    private String formatRecurringUntil(String value) {
        if (value == null || value.isBlank())
            return null;
        try {
            ZonedDateTime dateTime = parseDateTime(value, zoneOrUTC(timeZone, TIME_ZONE));
            return formatToRRuleDate(dateTime);
        } catch (DateTimeParseException e) {
            // not a datetime, try to verify it's in the expected RRULE format
        }
        if (isValidRRuleFormat(value))
            return value;
        throw buildDateParsingException(RECURRING_UNTIL, value);
    }

    @JsonIgnore
    @Override
    public List<Map<String, Object>> getInputParameters() {
        return inputParametersList.stream().map(t -> t.toMap(null)).toList();
    }
}

package io.camunda.connector.googlecalendar.api;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.ZonedDateTime;
import java.util.List;

public class CalendarDescription {

    /**
     * ID of the event. Set when the description is returned by GETEVENT or SEARCHEVENT; ignored as input.
     */
    public String eventId;

    /**
     * Start date and time of the event. For all-day events only the date part (year, month, day) is used.
     */
    public ZonedDateTime startDate;

    /**
     * End date and time of the event. For all-day events only the date part is used; the end date is exclusive (set it to the day after the last day).
     */
    public ZonedDateTime endDate;

    /**
     * IANA timezone identifier, e.g. "Europe/Paris" or "America/New_York". Used for timed events; ignored when {@link #allDay} is true.
     */
    public String timeZone;

    /**
     * True when {@link #startDate} was provided as a wall time without explicit offset/zone AND no
     * {@link #timeZone} was given. On UPDATE, such a date is re-anchored in the current event's timezone.
     * Internal flag: not part of the connector output.
     */
    @JsonIgnore
    public boolean startDateIsLocal;

    /**
     * Same as {@link #startDateIsLocal}, for {@link #endDate}.
     */
    @JsonIgnore
    public boolean endDateIsLocal;

    /**
     * Title of the event as it appears in the calendar (maps to Google's "summary" field).
     */
    public String title;

    /**
     * Optional free-text description shown in the event detail view.
     */
    public String description;

    /**
     * List of attendee email addresses. Each address receives an invitation email.
     */
    public List<String> listAttendes;

    /**
     * When true, a Google Meet video-conference link is generated and attached to the event.
     */
    public boolean createGoogleMeet;

    /**
     * When true, the event spans full days and no time-of-day is stored (start/end use date-only format).
     */
    public boolean allDay;

    /**
     * Physical location of the event, e.g. "10 Rue de Rivoli, Paris" or a room name.
     */
    public String localisation;

    /**
     * ID of the target Google Calendar. Use "primary" for the user's default calendar, or a full calendar ID (e.g. "abc123@group.calendar.google.com"). Null defaults to "primary".
     */
    public String calendarId;

    /**
     * Recurrence rule for repeating events. Null means the event occurs only once.
     */
    public RecurrenceRule recurrenceRule;

    /**
     * Free/busy status shown to other users. OPAQUE = busy (default), TRANSPARENT = available. Null uses the Google Calendar default.
     */
    public Transparency transparency;

    /**
     * Controls who can see the event details. Null inherits the calendar's default visibility setting.
     */
    public Visibility visibility;

    /**
     * Color displayed for this event in the calendar UI. Null uses the calendar's default color.
     */
    public EventColor color;

    // -----------------------------------------------------------------------

    /**
     * Compact human-readable description, used in execution logs. Skips null fields.
     */
    @JsonIgnore
    public String getLogInformation() {
        StringBuilder sb = new StringBuilder("CalendarInfo:");
        if (title != null)
            sb.append("title[").append(title).append("]");
        if (startDate != null || endDate != null)
            sb.append("From [").append(startDate == null ? "null" : startDate).append("->").append(endDate == null ? "null" : endDate).append("]");
        if (timeZone != null)
            sb.append(" timeZone[").append(timeZone).append(']');
        if (description != null)
            sb.append(" description[").append(description).append(']');
        if (listAttendes != null && !listAttendes.isEmpty())
            sb.append(" attendees[").append(String.join(",", listAttendes)).append(']');
        if (allDay)
            sb.append(" allDay");
        if (localisation != null)
            sb.append(" localisation[").append(localisation).append(']');
        if (transparency != null)
            sb.append(" transparency[").append(transparency).append(']');
        if (visibility != null)
            sb.append(" visibility[").append(visibility).append(']');
        if (color != null)
            sb.append(" color[").append(color).append(']');
        return sb.toString();
    }

    /**
     * Transparency controls how the event appears in free/busy queries.
     * OPAQUE = busy (default), TRANSPARENT = available/free.
     */
    public enum Transparency {OPAQUE, TRANSPARENT}

    /**
     * Who can see the event details.
     * DEFAULT = inherits calendar setting, PUBLIC = everyone, PRIVATE = only attendees, CONFIDENTIAL = like PRIVATE.
     */
    public enum Visibility {DEFAULT, PUBLIC, PRIVATE, CONFIDENTIAL}

    /**
     * Color override for the event. Maps to Google Calendar color IDs 1–11.
     * See https://developers.google.com/calendar/api/v3/reference/colors/get
     */
    public enum EventColor {
        TOMATO("1"), FLAMINGO("2"), TANGERINE("3"), BANANA("4"),
        SAGE("5"), BASIL("6"), PEACOCK("7"), BLUEBERRY("8"),
        LAVENDER("9"), GRAPE("10"), GRAPHITE("11");

        public final String colorId;

        EventColor(String colorId) {
            this.colorId = colorId;
        }
    }

    /**
     * Defines a recurrence rule for a recurring calendar event.
     * The {@link RecurrenceRule#toRRule()} method produces the RFC 5545 RRULE string
     * that Google Calendar expects.
     *
     * <p>Example — every 2 weeks on Monday and Wednesday, 10 times:
     * <pre>
     *   RecurrenceRule r = new RecurrenceRule();
     *   r.frequency = RecurrenceRule.Frequency.WEEKLY;
     *   r.interval = 2;
     *   r.count = 10;
     *   r.daysOfWeek = List.of("MO", "WE");
     * </pre>
     */
    public static class RecurrenceRule {

        /**
         * How often the event repeats. Required.
         */
        public Frequency frequency;
        /**
         * Repeat every N units of the chosen frequency. Default: 1.
         */
        public int interval = 1;
        /**
         * Maximum number of occurrences. Null means no limit (use {@link #until} instead,
         * or leave both null for an infinite series).
         */
        public Integer count;
        /**
         * Last date/time of the series (inclusive). Format: "yyyyMMdd" for all-day events,
         * "yyyyMMddTHHmmssZ" for timed events. Ignored when {@link #count} is set.
         */
        public String until;
        /**
         * Days of the week for WEEKLY (and sometimes MONTHLY/YEARLY) rules.
         * Valid values: MO, TU, WE, TH, FR, SA, SU.
         * Example: List.of("MO", "WE", "FR").
         */
        public List<String> daysOfWeek;

        /**
         * Builds the RFC 5545 RRULE string, e.g. {@code "RRULE:FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE"}.
         */
        public String toRRule() {
            StringBuilder sb = new StringBuilder("RRULE:FREQ=").append(frequency.name());
            if (interval > 1) {
                sb.append(";INTERVAL=").append(interval);
            }
            if (count != null) {
                sb.append(";COUNT=").append(count);
            } else if (until != null && !until.isBlank()) {
                sb.append(";UNTIL=").append(until);
            }
            if (daysOfWeek != null && !daysOfWeek.isEmpty()) {
                sb.append(";BYDAY=").append(String.join(",", daysOfWeek));
            }
            return sb.toString();
        }

        public enum Frequency {DAILY, WEEKLY, MONTHLY, YEARLY}
    }
}

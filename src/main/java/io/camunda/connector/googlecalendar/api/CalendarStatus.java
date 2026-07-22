package io.camunda.connector.googlecalendar.api;

import java.util.List;

public class CalendarStatus {

    /**
     * The event ID (master series ID for recurring events, or single event ID).
     */
    public String eventId;

    /**
     * The calendar ID this event belongs to.
     */
    public String calendarId;

    /**
     * URL to open the event in Google Calendar.
     */
    public String htmlLink;

    /**
     * Google Meet video-conference URL, populated when createGoogleMeet was requested.
     */
    public String meetLink;

    /**
     * Event status as returned by Google Calendar (e.g. "confirmed", "cancelled").
     */
    public String status;

    /**
     * For recurring events: list of individual occurrences.
     * Populated by {@code getCalendarOccurrence()} and optionally by {@code createEvent()}
     * when the created event has a recurrence rule.
     */
    public List<Occurrence> occurrences;

    /**
     * Event descriptions. Populated by {@code getEvent()} (one entry) and
     * {@code searchEvents()} (one entry per event found in the period).
     */
    public List<CalendarDescription> listCalendarDescription;

    /**
     * Compact human-readable description, used in execution logs. Skips null fields.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("CalendarStatus{");
        if (eventId != null) sb.append("eventId=").append(eventId).append(", ");
        if (calendarId != null) sb.append("calendarId=").append(calendarId).append(", ");
        if (status != null) sb.append("status=").append(status).append(", ");
        if (htmlLink != null) sb.append("htmlLink=").append(htmlLink).append(", ");
        if (meetLink != null) sb.append("meetLink=").append(meetLink).append(", ");
        if (occurrences != null)
            sb.append("occurrences=").append(occurrences.size()).append(", ");
        if (listCalendarDescription != null)
            sb.append("descriptions=").append(listCalendarDescription.size()).append(", ");
        if (sb.charAt(sb.length() - 2) == ',')
            sb.setLength(sb.length() - 2);
        return sb.append('}').toString();
    }

    /**
     * Represents one occurrence of a recurring event.
     */
    public static class Occurrence {
        /**
         * Occurrence-specific event ID (usable directly in delete/update calls).
         */
        public String occurrenceId;
        /**
         * Start date/time of this occurrence in ISO-8601 format.
         */
        public String startDate;
        /**
         * End date/time of this occurrence in ISO-8601 format.
         */
        public String endDate;
        /**
         * Status of this occurrence ("confirmed", "cancelled", etc.).
         */
        public String status;
    }
}

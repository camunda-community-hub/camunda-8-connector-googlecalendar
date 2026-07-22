package io.camunda.connector.googlecalendar;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.connector.cherrytemplate.CherryOutput;
import io.camunda.connector.cherrytemplate.RunnerParameter;
import io.camunda.connector.googlecalendar.api.CalendarDescription;
import io.camunda.connector.googlecalendar.api.CalendarStatus;

import java.util.List;
import java.util.Map;

public class GoogleCalendarOutput implements CherryOutput {

    public static final String EVENT_ID = "eventId";
    public static final String CALENDAR_ID = "calendarId";
    public static final String HTML_LINK = "htmlLink";
    public static final String MEET_LINK = "meetLink";
    public static final String STATUS = "status";
    public static final String OCCURRENCES = "occurrences";
    public static final String LIST_GOOGLE_CALENDAR_EVENT = "listGoogleCalendarEvent";
    public static final RunnerParameter parameterEventId = new RunnerParameter(
            EVENT_ID, "Event ID", String.class, RunnerParameter.Level.OPTIONAL,
            "ID of the event. For recurring events this is the master series ID.");
    public static final RunnerParameter parameterCalendarId = new RunnerParameter(
            CALENDAR_ID, "Calendar ID", String.class, RunnerParameter.Level.OPTIONAL,
            "ID of the calendar the event belongs to.");
    public static final RunnerParameter parameterHtmlLink = new RunnerParameter(
            HTML_LINK, "HTML link", String.class, RunnerParameter.Level.OPTIONAL,
            "URL to open the event in the Google Calendar web UI.");
    public static final RunnerParameter parameterMeetLink = new RunnerParameter(
            MEET_LINK, "Meet link", String.class, RunnerParameter.Level.OPTIONAL,
            "Google Meet video-conference URL. Present only when a Meet link was generated.");
    public static final RunnerParameter parameterStatus = new RunnerParameter(
            STATUS, "Status", String.class, RunnerParameter.Level.OPTIONAL,
            "Event status returned by Google Calendar (e.g. 'confirmed', 'cancelled').");
    public static final RunnerParameter parameterOccurrences = new RunnerParameter(
            OCCURRENCES, "Occurrences", List.class, RunnerParameter.Level.OPTIONAL,
            "List of individual occurrences for a recurring event. Each entry contains occurrenceId, startDate, endDate, status.");
    public static final RunnerParameter parameterListGoogleCalendarEvent = new RunnerParameter(
            LIST_GOOGLE_CALENDAR_EVENT, "Google Event (all fields)", List.class, RunnerParameter.Level.OPTIONAL,
            "Event descriptions: one entry for GETEVENT, one entry per event found for SEARCHEVENT. Each entry contains eventId, title, startDate, endDate, attendees...");

    /* ------------------------------------------------------------------ */
    /*  RunnerParameters                                                   */
    /* ------------------------------------------------------------------ */
    /**
     * ID of the created/updated/deleted event (master series ID for recurring events).
     */
    public String eventId;
    /**
     * Calendar ID where the event lives.
     */
    public String calendarId;
    /**
     * URL to open the event in Google Calendar.
     */
    public String htmlLink;
    /**
     * Google Meet video-conference URL, present when a Meet link was requested.
     */
    public String meetLink;
    /**
     * Event status as returned by Google Calendar (e.g. "confirmed", "cancelled").
     */
    public String status;
    /**
     * For recurring events: list of individual occurrences (populated by getOccurrences and createEvent with recurrence).
     */
    public List<CalendarStatus.Occurrence> occurrences;
    /**
     * Event descriptions: one entry for GETEVENT, one entry per event found for SEARCHEVENT.
     */
    public List<CalendarDescription> listGoogleCalendarEvent;

    /* ------------------------------------------------------------------ */
    /*  Parameter list                                                     */
    /* ------------------------------------------------------------------ */
    public List<RunnerParameter> outputParametersList = List.of(
            parameterEventId,
            parameterCalendarId,
            parameterHtmlLink,
            parameterMeetLink,
            parameterStatus,
            parameterOccurrences,
            parameterListGoogleCalendarEvent);

    @JsonIgnore
    @Override
    public List<Map<String, Object>> getOutputParameters() {
        return outputParametersList.stream().map(t -> t.toMap(null)).toList();
    }

    /* ------------------------------------------------------------------ */
    /*  Populate from CalendarStatus                                       */
    /* ------------------------------------------------------------------ */

    public void populate(CalendarStatus calendarStatus) {
        this.eventId = calendarStatus.eventId;
        this.calendarId = calendarStatus.calendarId;
        this.htmlLink = calendarStatus.htmlLink;
        this.meetLink = calendarStatus.meetLink;
        this.status = calendarStatus.status;
        this.occurrences = calendarStatus.occurrences;
        this.listGoogleCalendarEvent = calendarStatus.listCalendarDescription;
    }
}

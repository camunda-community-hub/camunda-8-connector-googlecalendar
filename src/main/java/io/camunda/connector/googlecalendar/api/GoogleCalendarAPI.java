package io.camunda.connector.googlecalendar.api;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.*;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.UserCredentials;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.googlecalendar.GoogleCalendarError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class GoogleCalendarAPI {

    public static final String DEFAULT_CALENDAR = "primary";
    private static final Logger logger = LoggerFactory.getLogger(GoogleCalendarAPI.class);
    private static final GsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    /**
     * Creates a Google Calendar event.
     *
     * @param googleAuthenticate OAuth2 credentials
     * @return CalendarStatus containing the created event details
     */
    public CalendarStatus createEvent(GoogleAuthenticate googleAuthenticate,
                                      CalendarDescription calendarDescription) throws IOException, GeneralSecurityException {

        Calendar service = buildCalendarService(googleAuthenticate);

        Event event = new Event()
                .setSummary(calendarDescription.title)
                .setDescription(calendarDescription.description)
                .setLocation(calendarDescription.localisation);

        EventDateTime start;
        EventDateTime end;
        if (calendarDescription.allDay) {
            start = new EventDateTime().setDate(toDateOnly(calendarDescription.startDate));
            end = new EventDateTime().setDate(toDateOnly(calendarDescription.endDate));
        } else {
            start = new EventDateTime()
                    .setDateTime(toCalendarDateTime(calendarDescription.startDate))
                    .setTimeZone(calendarDescription.timeZone);
            end = new EventDateTime()
                    .setDateTime(toCalendarDateTime(calendarDescription.endDate))
                    .setTimeZone(calendarDescription.timeZone);
        }
        event.setStart(start);
        event.setEnd(end);

        if (calendarDescription.listAttendes != null && !calendarDescription.listAttendes.isEmpty()) {
            List<EventAttendee> attendees = calendarDescription.listAttendes.stream()
                    .map(email -> new EventAttendee().setEmail(email))
                    .collect(Collectors.toList());
            event.setAttendees(attendees);
        }

        if (calendarDescription.recurrenceRule != null) {
            event.setRecurrence(List.of(calendarDescription.recurrenceRule.toRRule()));
        }
        if (calendarDescription.transparency != null) {
            event.setTransparency(calendarDescription.transparency.name().toLowerCase());
        }
        if (calendarDescription.visibility != null) {
            event.setVisibility(calendarDescription.visibility.name().toLowerCase());
        }
        if (calendarDescription.color != null) {
            event.setColorId(calendarDescription.color.colorId);
        }

        int conferenceDataVersion = 0;
        if (Boolean.TRUE.equals(calendarDescription.createGoogleMeet)) {
            ConferenceData conferenceData = new ConferenceData()
                    .setCreateRequest(new CreateConferenceRequest()
                            .setRequestId(UUID.randomUUID().toString())
                            .setConferenceSolutionKey(new ConferenceSolutionKey().setType("hangoutsMeet")));
            event.setConferenceData(conferenceData);
            conferenceDataVersion = 1;
        }

        String calendarId = getCalendarIdForAPI(calendarDescription.calendarId);

        Event createdEvent = service.events()
                .insert(calendarId, event)
                .setConferenceDataVersion(conferenceDataVersion)
                .execute();


        CalendarStatus status = new CalendarStatus();
        status.eventId = createdEvent.getId();
        status.calendarId = calendarId;
        status.htmlLink = createdEvent.getHtmlLink();
        status.status = createdEvent.getStatus();
        if (calendarDescription.createGoogleMeet && createdEvent.getConferenceData() != null
                && createdEvent.getConferenceData().getEntryPoints() != null) {
            status.meetLink = createdEvent.getConferenceData().getEntryPoints().stream()
                    .filter(ep -> "video".equals(ep.getEntryPointType()))
                    .map(ep -> ep.getUri())
                    .findFirst()
                    .orElse(null);
        }
        if (calendarDescription.recurrenceRule != null) {
            status.occurrences = fetchOccurrences(service, calendarId, createdEvent.getId());
        }
        logger.info("GoogleCalendarAPI: Created event: {} id[{}] link[{}]", calendarDescription.getLogInformation(), createdEvent.getId(), createdEvent.getHtmlLink());

        return status;
    }

    public CalendarStatus deleteCalendarEvent(GoogleAuthenticate googleAuthenticate,
                                              String calendarId,
                                              String eventId) throws IOException, GeneralSecurityException {
        Calendar service = buildCalendarService(googleAuthenticate);

        String resolvedCalendarId = getCalendarIdForAPI(calendarId);
        service.events().delete(resolvedCalendarId, eventId).execute();

        logger.info("GoogleCalendarAPI: Deleted event [{}] from calendar [{}]", eventId, resolvedCalendarId);

        CalendarStatus status = new CalendarStatus();
        status.eventId = eventId;
        status.status = "cancelled";
        return status;
    }

    /**
     * Returns all occurrences of a recurring event.
     * The master event ID (returned by {@code createEvent()}) is passed as {@code eventId}.
     * Each occurrence is listed in {@link CalendarStatus#occurrences} with its own
     * {@code occurrenceId} that can be used directly in delete or update calls.
     */
    public CalendarStatus getCalendarOccurrence(GoogleAuthenticate googleAuthenticate,
                                                String calendarId,
                                                String eventId) throws IOException, GeneralSecurityException {
        Calendar service = buildCalendarService(googleAuthenticate);
        String resolvedCalendarId = getCalendarIdForAPI(calendarId);

        CalendarStatus status = new CalendarStatus();
        status.eventId = eventId;
        status.calendarId = resolvedCalendarId;
        status.occurrences = fetchOccurrences(service, resolvedCalendarId, eventId);
        return status;
    }


    /**
     * Updates an existing calendar event.
     *
     * @param calendarId          Calendar where the event currently lives ("primary" or a full calendar ID).
     * @param eventId             ID of the event to update.
     * @param calendarDescription Fields to update. Set only the fields you want to change; null fields are left untouched.
     *                            If {@code calendarDescription.googleCalendar} is set and differs from {@code calendarId},
     *                            the event is moved to that calendar first, then updated there.
     */
    public CalendarStatus updateCalendarEvent(GoogleAuthenticate googleAuthenticate,
                                              String calendarId,
                                              String eventId,
                                              CalendarDescription calendarDescription) throws IOException, GeneralSecurityException {
        Calendar service = buildCalendarService(googleAuthenticate);

        String sourceCalendarId = getCalendarIdForAPI(calendarId);
        String targetCalendarId = calendarDescription.calendarId == null ? sourceCalendarId : calendarDescription.calendarId;

        // Move to a different calendar first if requested
        if (!targetCalendarId.equals(sourceCalendarId)) {
            try {
                service.events().move(sourceCalendarId, eventId, targetCalendarId).execute();
                logger.info("GoogleCalendarAPI: Moved event [{}] from calendar [{}] to [{}]", eventId, sourceCalendarId, targetCalendarId);
            } catch (IOException e) {
                throw new ConnectorException(GoogleCalendarError.UNKOWN_EVENT, "EventId[" + eventId + "] calendarId[" + calendarId + "] : " + e.getMessage());
            }
        }

        Event event = service.events().get(targetCalendarId, eventId).execute();
        if (event == null)
            throw new ConnectorException(GoogleCalendarError.UNKOWN_EVENT, "EventId[" + eventId + "] calendarId[" + calendarId + "] does not exist");

        int conferenceDataVersion = applyDescriptionToEvent(event, calendarDescription);

        Event updatedEvent = service.events()
                .update(targetCalendarId, eventId, event)
                .setConferenceDataVersion(conferenceDataVersion)
                .execute();


        CalendarStatus status = new CalendarStatus();
        status.eventId = updatedEvent.getId();
        status.calendarId = targetCalendarId;
        status.htmlLink = updatedEvent.getHtmlLink();
        status.status = updatedEvent.getStatus();
        if (updatedEvent.getConferenceData() != null && updatedEvent.getConferenceData().getEntryPoints() != null) {
            status.meetLink = updatedEvent.getConferenceData().getEntryPoints().stream()
                    .filter(ep -> "video".equals(ep.getEntryPointType()))
                    .map(ep -> ep.getUri())
                    .findFirst()
                    .orElse(null);
        }
        logger.info("GoogleCalendarAPI: Update event: {} id[{}] link[{}]", calendarDescription.getLogInformation(), updatedEvent.getId(), updatedEvent.getHtmlLink());
        return status;
    }

    /**
     * Updates the occurrences of a recurring event in a period.
     * The period starts at {@code dateFrom} (mandatory) and ends at {@code dateTo} (inclusive),
     * or has no end when {@code isAllAfterThisOccurrence} is true.
     * Each occurrence found in the period is updated with the non-null fields of the description.
     */
    public CalendarStatus updateCalendarEventOccurrence(GoogleAuthenticate googleAuthenticate,
                                                        String calendarId,
                                                        String eventId,
                                                        ZonedDateTime dateFrom,
                                                        ZonedDateTime dateTo,
                                                        String timeZone,
                                                        Boolean isAllAfterThisOccurrence,
                                                        CalendarDescription calendarDescription,
                                                        LocalTime startTime,
                                                        LocalTime endTime) throws IOException, GeneralSecurityException {
        Calendar service = buildCalendarService(googleAuthenticate);
        String resolvedCalendarId = getCalendarIdForAPI(calendarId);

        // the function-level timezone is the fallback for the new dates
        if (calendarDescription.timeZone == null || calendarDescription.timeZone.isBlank())
            calendarDescription.timeZone = timeZone;

        List<Event> occurrences = findOccurrencesInRange(service, resolvedCalendarId, eventId,
                dateFrom, dateTo, Boolean.TRUE.equals(isAllAfterThisOccurrence));

        StringBuffer traceOccurences = new StringBuffer();

        List<CalendarStatus.Occurrence> updatedOccurrences = new ArrayList<>();
        int count = 0;
        for (Event occurrence : occurrences) {
            count++;
            int conferenceDataVersion = applyDescriptionToEvent(occurrence, calendarDescription);
            if (startTime != null || endTime != null) {
                applyTimeOverride(occurrence, startTime, endTime);
            }
            Event updatedEvent = service.events()
                    .patch(resolvedCalendarId, occurrence.getId(), occurrence)
                    .setConferenceDataVersion(conferenceDataVersion)
                    .execute();

            CalendarStatus.Occurrence o = new CalendarStatus.Occurrence();
            o.occurrenceId = updatedEvent.getId();
            o.startDate = formatEventDateTime(updatedEvent.getStart());
            o.endDate = formatEventDateTime(updatedEvent.getEnd());
            o.status = updatedEvent.getStatus();
            updatedOccurrences.add(o);
            traceOccurences.append(o.occurrenceId).append(",");

            logger.debug("GoogleCalendarAPI: Updated occurrence [{}] [{}/{}] start[{}] colorId[{}] of series [{}]",
                    o.occurrenceId, count, occurrences.size(), o.startDate, updatedEvent.getColorId(), eventId);
        }
        logger.info("GoogleCalendarAPI: Updated occurrences [{}] change[{}] occurrence: {}", occurrences.size(), calendarDescription.getLogInformation(), traceOccurences);

        CalendarStatus status = new CalendarStatus();
        status.eventId = eventId;
        status.calendarId = resolvedCalendarId;
        status.occurrences = updatedOccurrences;
        return status;
    }

    /**
     * Deletes the occurrences of a recurring event in a period.
     * The period starts at {@code dateFrom} (mandatory) and ends at {@code dateTo} (inclusive),
     * or has no end when {@code allAfterThisOccurrence} is true.
     */
    public CalendarStatus deleteCalendarOccurrence(GoogleAuthenticate googleAuthenticate,
                                                   String calendarId,
                                                   String eventId,
                                                   ZonedDateTime dateFrom,
                                                   ZonedDateTime dateTo,
                                                   boolean allAfterThisOccurrence) throws IOException, GeneralSecurityException {
        Calendar service = buildCalendarService(googleAuthenticate);
        String resolvedCalendarId = getCalendarIdForAPI(calendarId);

        List<Event> occurrences = findOccurrencesInRange(service, resolvedCalendarId, eventId,
                dateFrom, dateTo, allAfterThisOccurrence);

        StringBuffer traceOccurences = new StringBuffer();
        List<CalendarStatus.Occurrence> deletedOccurrences = new ArrayList<>();
        for (Event occurrence : occurrences) {
            service.events().delete(resolvedCalendarId, occurrence.getId()).execute();
            traceOccurences.append(occurrence.getId()).append(",");
            logger.debug("GoogleCalendarAPI: Deleted occurrence [{}] start[{}] from series [{}]",
                    occurrence.getId(), formatEventDateTime(occurrence.getStart()), eventId);

            CalendarStatus.Occurrence o = new CalendarStatus.Occurrence();
            o.occurrenceId = occurrence.getId();
            o.startDate = formatEventDateTime(occurrence.getStart());
            o.endDate = formatEventDateTime(occurrence.getEnd());
            o.status = "cancelled";
            deletedOccurrences.add(o);
        }
        logger.info("GoogleCalendarAPI: Delete occurrences [{}] from[{}] to [{}] (allAfterThisOccurrence? {}) occurrences: {}",
                occurrences.size(),
                dateFrom, dateTo, allAfterThisOccurrence, traceOccurences);

        CalendarStatus status = new CalendarStatus();
        status.eventId = eventId;
        status.calendarId = resolvedCalendarId;
        status.status = "cancelled";
        status.occurrences = deletedOccurrences;
        return status;
    }

    /**
     * Replaces the time-of-day of an occurrence's start (and end, if provided) while keeping
     * the occurrence's own date and timezone. Used so a single UPDATEOCCURRENCE call can shift
     * many occurrences spread over different dates to a new time, e.g. move a daily standup
     * from 09:00 to 09:30 for every occurrence in a period.
     */
    private void applyTimeOverride(Event occurrence, LocalTime startTime, LocalTime endTime) {
        EventDateTime currentStart = occurrence.getStart();
        ZoneId zone = resolveEventZone(currentStart);
        if (startTime != null) {
            occurrence.setStart(new EventDateTime()
                    .setDateTime(toCalendarDateTime(combineDateAndTime(currentStart, zone, startTime)))
                    .setTimeZone(currentStart != null ? currentStart.getTimeZone() : null));
        }
        if (endTime != null) {
            EventDateTime currentEnd = occurrence.getEnd();
            occurrence.setEnd(new EventDateTime()
                    .setDateTime(toCalendarDateTime(combineDateAndTime(currentEnd, zone, endTime)))
                    .setTimeZone(currentEnd != null ? currentEnd.getTimeZone() : null));
        }
    }

    /**
     * Timezone of an EventDateTime, or UTC when the occurrence carries no explicit timezone.
     */
    private ZoneId resolveEventZone(EventDateTime edt) {
        if (edt != null && edt.getTimeZone() != null && !edt.getTimeZone().isBlank())
            return ZoneId.of(edt.getTimeZone());
        return ZoneOffset.UTC;
    }

    /**
     * Combines the date of an existing EventDateTime with a new time-of-day, in the given zone.
     */
    private ZonedDateTime combineDateAndTime(EventDateTime edt, ZoneId zone, LocalTime time) {
        com.google.api.client.util.DateTime dt = edt.getDateTime() != null ? edt.getDateTime() : edt.getDate();
        LocalDate date = Instant.ofEpochMilli(dt.getValue()).atZone(zone).toLocalDate();
        return date.atTime(time).atZone(zone);
    }

    /**
     * Applies the non-null fields of the description to an existing event.
     * Shared by {@code updateCalendarEvent} and {@code updateCalendarEventOccurrence}.
     *
     * @return the conferenceDataVersion to use on the update call (1 when a Google Meet was added)
     */
    private int applyDescriptionToEvent(Event event, CalendarDescription calendarDescription) {
        if (calendarDescription.title != null) event.setSummary(calendarDescription.title);
        if (calendarDescription.description != null) event.setDescription(calendarDescription.description);
        if (calendarDescription.localisation != null) event.setLocation(calendarDescription.localisation);

        if (calendarDescription.startDate != null) {
            event.setStart(buildUpdatedEventDateTime(calendarDescription.startDate,
                    calendarDescription.startDateIsLocal, calendarDescription.timeZone, event.getStart()));
        }
        if (calendarDescription.endDate != null) {
            event.setEnd(buildUpdatedEventDateTime(calendarDescription.endDate,
                    calendarDescription.endDateIsLocal, calendarDescription.timeZone, event.getEnd()));
        }

        if (calendarDescription.listAttendes != null && !calendarDescription.listAttendes.isEmpty()) {
            List<EventAttendee> attendees = calendarDescription.listAttendes.stream()
                    .map(email -> new EventAttendee().setEmail(email))
                    .collect(Collectors.toList());
            event.setAttendees(attendees);
        }
        if (calendarDescription.transparency != null) {
            event.setTransparency(calendarDescription.transparency.name().toLowerCase());
        }
        if (calendarDescription.visibility != null) {
            event.setVisibility(calendarDescription.visibility.name().toLowerCase());
        }
        if (calendarDescription.color != null) {
            event.setColorId(calendarDescription.color.colorId);
        }

        int conferenceDataVersion = 0;
        if (calendarDescription.createGoogleMeet && event.getConferenceData() == null) {
            ConferenceData conferenceData = new ConferenceData()
                    .setCreateRequest(new CreateConferenceRequest()
                            .setRequestId(UUID.randomUUID().toString())
                            .setConferenceSolutionKey(new ConferenceSolutionKey().setType("hangoutsMeet")));
            event.setConferenceData(conferenceData);
            conferenceDataVersion = 1;
        }
        return conferenceDataVersion;
    }

    /**
     * Returns the occurrences of a recurring event whose start is in the requested period.
     * {@code dateFrom} is mandatory; the period ends at {@code dateTo} (inclusive), or is
     * unbounded when {@code allAfterThisOccurrence} is true.
     */
    private List<Event> findOccurrencesInRange(Calendar service,
                                               String calendarId,
                                               String eventId,
                                               ZonedDateTime dateFrom,
                                               ZonedDateTime dateTo,
                                               boolean allAfterThisOccurrence) throws IOException {
        if (dateFrom == null)
            throw new ConnectorException(GoogleCalendarError.BAD_INPUTPARAMETER,
                    "Parameter [sourceDateFrom] is mandatory to select occurrences");
        if (dateTo == null && !allAfterThisOccurrence)
            throw new ConnectorException(GoogleCalendarError.BAD_INPUTPARAMETER,
                    "Provide [sourceDateTo] or set [allAfterThisOccurrence] to true to select occurrences");

        long fromMillis = dateFrom.toInstant().toEpochMilli();
        Long toMillis = allAfterThisOccurrence ? null : dateTo.toInstant().toEpochMilli();

        List<Event> instances = service.events()
                .instances(calendarId, eventId)
                .execute()
                .getItems();

        List<Event> selection = new ArrayList<>();
        for (Event instance : instances) {
            Long startMillis = getStartMillis(instance);
            if (startMillis == null) continue;
            if (startMillis >= fromMillis && (toMillis == null || startMillis <= toMillis))
                selection.add(instance);
        }
        return selection;
    }

    /**
     * Start of an occurrence in epoch milliseconds (timed or all-day event), or null.
     */
    private Long getStartMillis(Event instance) {
        EventDateTime start = instance.getStart();
        if (start == null) return null;
        if (start.getDateTime() != null) return start.getDateTime().getValue();
        if (start.getDate() != null) return start.getDate().getValue();
        return null;
    }

    // -----------------------------------------------------------------------

    /**
     * Fetches all occurrences of a recurring event and maps them to {@link CalendarStatus.Occurrence}.
     */
    private List<CalendarStatus.Occurrence> fetchOccurrences(Calendar service,
                                                             String calendarId,
                                                             String masterEventId) throws IOException {
        List<Event> instances = service.events()
                .instances(calendarId, masterEventId)
                .execute()
                .getItems();

        List<CalendarStatus.Occurrence> occurrences = new ArrayList<>();
        for (Event instance : instances) {
            CalendarStatus.Occurrence o = new CalendarStatus.Occurrence();
            o.occurrenceId = instance.getId();
            o.status = instance.getStatus();
            o.startDate = formatEventDateTime(instance.getStart());
            o.endDate = formatEventDateTime(instance.getEnd());
            occurrences.add(o);
        }
        return occurrences;
    }

    /**
     * Formats a Google Calendar {@link EventDateTime} to an ISO-8601 string.
     */
    private String formatEventDateTime(EventDateTime edt) {
        if (edt == null) return null;
        if (edt.getDate() != null) return edt.getDate().toStringRfc3339();
        if (edt.getDateTime() != null) return edt.getDateTime().toStringRfc3339();
        return null;
    }

    /**
     * Returns the description of one event.
     *
     * @param calendarId calendar where the event lives ("primary" when null)
     * @param eventId    ID of the event to fetch
     * @return CalendarStatus with {@code listCalendarDescription} containing one entry
     */
    public CalendarStatus getEvent(GoogleAuthenticate googleAuthenticate,
                                   String calendarId,
                                   String eventId) throws IOException, GeneralSecurityException {
        Calendar service = buildCalendarService(googleAuthenticate);
        String resolvedCalendarId = getCalendarIdForAPI(calendarId);

        Event event = service.events().get(resolvedCalendarId, eventId).execute();
        if (event == null)
            throw new ConnectorException(GoogleCalendarError.UNKOWN_EVENT, "Event[" + eventId + "] calendarid[" + calendarId + "] does not exist");

        CalendarStatus status = new CalendarStatus();
        status.eventId = eventId;
        status.calendarId = resolvedCalendarId;
        status.htmlLink = event.getHtmlLink();
        status.status = event.getStatus();
        status.listCalendarDescription = List.of(toCalendarDescription(event, resolvedCalendarId));
        return status;
    }

    /**
     * Returns all events of a calendar in the given period.
     *
     * @param calendarId calendar to search in ("primary" when null)
     * @param dateFrom   beginning of the period (inclusive)
     * @param dateTo     end of the period (exclusive)
     * @param timeZone   optional IANA timezone used for the dates in the response
     * @return CalendarStatus with {@code listCalendarDescription} containing one entry per event found
     */
    public CalendarStatus searchEvents(GoogleAuthenticate googleAuthenticate,
                                       String calendarId,
                                       ZonedDateTime dateFrom,
                                       ZonedDateTime dateTo,
                                       String timeZone) throws IOException, GeneralSecurityException {
        Calendar service = buildCalendarService(googleAuthenticate);
        String resolvedCalendarId = getCalendarIdForAPI(calendarId);

        Calendar.Events.List request = service.events().list(resolvedCalendarId)
                .setTimeMin(toCalendarDateTime(dateFrom == null ? ZonedDateTime.now() : dateFrom))
                .setTimeMax(toCalendarDateTime(dateTo == null ? ZonedDateTime.now() : dateTo))
                .setSingleEvents(true)
                .setOrderBy("startTime");
        if (timeZone != null && !timeZone.isBlank()) {
            request.setTimeZone(timeZone);
        }

        List<CalendarDescription> descriptions = new ArrayList<>();
        String pageToken = null;
        do {
            request.setPageToken(pageToken);
            Events events = request.execute();
            if (events.getItems() != null) {
                for (Event event : events.getItems()) {
                    descriptions.add(toCalendarDescription(event, resolvedCalendarId));
                }
            }
            pageToken = events.getNextPageToken();
        } while (pageToken != null);

        logger.info("Found {} events in calendar [{}] between [{}] and [{}]",
                descriptions.size(), resolvedCalendarId, dateFrom, dateTo);

        CalendarStatus status = new CalendarStatus();
        status.calendarId = resolvedCalendarId;
        status.listCalendarDescription = descriptions;
        return status;
    }

    /**
     * Maps a Google Calendar Event to a CalendarDescription.
     */
    private CalendarDescription toCalendarDescription(Event event, String calendarId) {
        CalendarDescription description = new CalendarDescription();
        description.eventId = event.getId();
        description.calendarId = calendarId;
        description.title = event.getSummary();
        description.description = event.getDescription();
        description.localisation = event.getLocation();

        EventDateTime start = event.getStart();
        if (start != null) {
            description.allDay = start.getDate() != null;
            description.startDate = toZonedDateTime(start);
            description.timeZone = start.getTimeZone();
        }
        description.endDate = toZonedDateTime(event.getEnd());

        if (event.getAttendees() != null) {
            description.listAttendes = event.getAttendees().stream()
                    .map(EventAttendee::getEmail)
                    .collect(Collectors.toList());
        }
        description.createGoogleMeet = event.getConferenceData() != null;

        // Google returns lowercase values ("opaque", "public"...); unknown values are left null
        try {
            if (event.getTransparency() != null)
                description.transparency = CalendarDescription.Transparency.valueOf(event.getTransparency().toUpperCase());
            if (event.getVisibility() != null)
                description.visibility = CalendarDescription.Visibility.valueOf(event.getVisibility().toUpperCase());
        } catch (IllegalArgumentException e) {
            // unknown enum value: keep the field null
        }
        if (event.getColorId() != null) {
            for (CalendarDescription.EventColor color : CalendarDescription.EventColor.values()) {
                if (color.colorId.equals(event.getColorId())) {
                    description.color = color;
                }
            }
        }
        return description;
    }

    /**
     * Converts a Google EventDateTime (timed or all-day) to a ZonedDateTime in UTC.
     */
    private ZonedDateTime toZonedDateTime(EventDateTime edt) {
        if (edt == null) return null;
        com.google.api.client.util.DateTime dt = edt.getDateTime() != null ? edt.getDateTime() : edt.getDate();
        if (dt == null) return null;
        return Instant.ofEpochMilli(dt.getValue()).atZone(ZoneOffset.UTC);
    }

    private Calendar buildCalendarService(GoogleAuthenticate auth) throws ConnectorException {
        long beginTimeBuild = System.currentTimeMillis();
        try {
            HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

            UserCredentials credentials = UserCredentials.newBuilder()
                    .setClientId(auth.getClientId())
                    .setClientSecret(auth.getClientSecret())
                    .setRefreshToken(auth.getRefreshToken())
                    .build();

            HttpRequestInitializer requestInitializer = new HttpCredentialsAdapter(credentials);

            return new Calendar.Builder(httpTransport, JSON_FACTORY, requestInitializer)
                    .setApplicationName(auth.getApplicationName())
                    .build();
        } catch (Exception e) {
            logger.error("Can't build the Google Calendar service: {}", e.getMessage(), e);
            throw new ConnectorException(GoogleCalendarError.BAD_CREDENTIAL,
                    "Can't build the Google Calendar service, check clientId/clientSecret/refreshToken: " + e.getMessage());
        } finally {
            if (System.currentTimeMillis() - beginTimeBuild > 100) {
                logger.info("Building Google Calendar service with {} seconds", System.currentTimeMillis() - beginTimeBuild);

            }
        }
    }

    /**
     * Builds the new start/end of an updated event. When no timezone is provided with the update
     * and the new date carries no explicit offset, the current event's timezone is kept: the new
     * wall time is re-anchored in that timezone instead of the provisional UTC interpretation.
     */
    private EventDateTime buildUpdatedEventDateTime(ZonedDateTime newDate,
                                                    boolean dateIsLocal,
                                                    String timeZone,
                                                    EventDateTime currentEventDateTime) {
        String resolvedTimeZone = timeZone;
        ZonedDateTime resolvedDate = newDate;
        if (resolvedTimeZone == null || resolvedTimeZone.isBlank()) {
            // no timezone in the update: keep the same timezone as the current event
            resolvedTimeZone = currentEventDateTime != null ? currentEventDateTime.getTimeZone() : null;
            if (dateIsLocal && resolvedTimeZone != null) {
                resolvedDate = newDate.toLocalDateTime().atZone(ZoneId.of(resolvedTimeZone));
            }
        }
        return new EventDateTime()
                .setDateTime(toCalendarDateTime(resolvedDate))
                .setTimeZone(resolvedTimeZone);
    }

    private com.google.api.client.util.DateTime toCalendarDateTime(ZonedDateTime dt) {
        return new com.google.api.client.util.DateTime(dt.toInstant().toEpochMilli());
    }

    private com.google.api.client.util.DateTime toDateOnly(ZonedDateTime dt) {
        return new com.google.api.client.util.DateTime(dt.toLocalDate().toString());
    }


    private String getCalendarIdForAPI(String calendarId) {
        if (calendarId == null
                || (calendarId != null && calendarId.isEmpty())) return DEFAULT_CALENDAR;
        return calendarId;
    }
}

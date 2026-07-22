package io.camunda.connector.googlecalendar;

/* ******************************************************************** */
/*                                                                      */
/*  GoogleCalendarFunction                                                         */
/*                                                                      */
/*  This connector is the main connector, doing the distribution on     */
/*  specific function                                                   */
/* ******************************************************************** */

import com.google.api.client.auth.oauth2.TokenResponseException;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.cherrytemplate.CherryConnector;
import io.camunda.connector.googlecalendar.api.CalendarStatus;
import io.camunda.connector.googlecalendar.api.GoogleCalendarAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Do not register any InputVariables: we want to get all
 */
@OutboundConnector(name = GoogleCalendarInput.GOOGLECALENDARFUNCTION, type = "c-google-calendar-function", inputVariables = {
        GoogleCalendarInput.CALFUNCTION,
        // shared
        GoogleCalendarInput.SOURCE_EVENT_ID,
        GoogleCalendarInput.SOURCE_CALENDAR_ID,
        GoogleCalendarInput.CALENDAR_ID,
        // UPDATEOCCURRENCE only
        GoogleCalendarInput.TIME_START,
        GoogleCalendarInput.TIME_END,
        // CalendarDescription
        GoogleCalendarInput.START_DATE,
        GoogleCalendarInput.END_DATE,
        GoogleCalendarInput.TIME_ZONE,
        GoogleCalendarInput.TITLE,
        GoogleCalendarInput.DESCRIPTION,
        GoogleCalendarInput.LIST_ATTENDES,
        GoogleCalendarInput.CREATE_GOOGLE_MEET,
        GoogleCalendarInput.ALL_DAY,
        GoogleCalendarInput.LOCALISATION,
        GoogleCalendarInput.CALENDAR_ID,
        GoogleCalendarInput.TRANSPARENCY,
        GoogleCalendarInput.VISIBILITY,
        GoogleCalendarInput.COLOR,
        GoogleCalendarInput.ALL_AFTER_THIS_OCCURRENCE,
        GoogleCalendarInput.TIME_START,
        GoogleCalendarInput.TIME_END,

        // Recurrence (CREATE only)
        GoogleCalendarInput.RECURRING,
        GoogleCalendarInput.RECURRING_FREQUENCY,
        GoogleCalendarInput.RECURRING_INTERVAL,
        GoogleCalendarInput.RECURRING_COUNT,
        GoogleCalendarInput.RECURRING_UNTIL,
        GoogleCalendarInput.RECURRING_DAYS_OF_WEEK,
        // GETEVENT / SEARCHEVENT
        GoogleCalendarInput.SOURCE_DATE_FROM,
        GoogleCalendarInput.SOURCE_DATE_TO,
        GoogleCalendarInput.SOURCE_TIME_ZONE,
        // GoogleAuthenticate
        GoogleCalendarInput.CLIENT_ID,
        GoogleCalendarInput.CLIENT_SECRET,
        GoogleCalendarInput.REFRESH_TOKEN,
        GoogleCalendarInput.APPLICATION_NAME
})

public class GoogleCalendarFunction implements OutboundConnectorFunction, CherryConnector {

    private static final String WORKER_LOGO = "data:image/png;base64,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";


    private final Logger logger = LoggerFactory.getLogger(GoogleCalendarFunction.class.getName());

    /**
     * Detects an OAuth authentication failure anywhere in the exception cause chain.
     * Google clients report bad credentials either as a TokenResponseException or as an
     * IOException whose message carries the OAuth error code (invalid_grant, invalid_client...).
     */
    private static boolean isCredentialError(Throwable e) {
        while (e != null) {
            if (e instanceof TokenResponseException) {
                return true;
            }
            String message = e.getMessage();
            if (message != null && (message.contains("invalid_grant")
                    || message.contains("invalid_client")
                    || message.contains("unauthorized_client")
                    || message.contains("Error getting access token"))) {
                return true;
            }
            e = e.getCause();
        }
        return false;
    }

    @Override
    public GoogleCalendarOutput execute(OutboundConnectorContext outboundConnectorContext) throws ConnectorException {
        long beginTime = System.currentTimeMillis();
        GoogleCalendarInput input;
        try {
            input = outboundConnectorContext.bindVariables(GoogleCalendarInput.class);
        } catch (ConnectorException ce) {
            logger.error("Bind input error ", ce);
            throw ce;
        } catch (Exception e) {
            logger.error("Bind input error ", e);
            throw new ConnectorException(GoogleCalendarError.BAD_INPUTPARAMETER,
                    "GoogleCalendar can't bind variables: " + e.getMessage());
        }

        String calFunction = input.getCalFunction();
        if (calFunction == null || calFunction.isBlank()) {
            throw new ConnectorException(GoogleCalendarError.BAD_INPUTPARAMETER, "Function is required");
        }

        GoogleCalendarAPI api = new GoogleCalendarAPI();
        CalendarStatus calendarStatus;

        try {
            calendarStatus = switch (calFunction) {
                case GoogleCalendarInput.CALFUNCTION_V_CREATE -> api.createEvent(
                        input.getGoogleAuthenticate(),
                        input.getCalendarDescription());

                case GoogleCalendarInput.CALFUNCTION_V_UPDATE -> api.updateCalendarEvent(
                        input.getGoogleAuthenticate(),
                        input.getSourceCalendarId(),
                        input.getSourceEventId(),
                        input.getCalendarDescription());

                case GoogleCalendarInput.CALFUNCTION_V_DELETE -> api.deleteCalendarEvent(
                        input.getGoogleAuthenticate(),
                        input.getSourceCalendarId(),
                        input.getSourceEventId());

                case GoogleCalendarInput.CALFUNCTION_V_UPDATEOCCURRENCE -> api.updateCalendarEventOccurrence(
                        input.getGoogleAuthenticate(),
                        input.getSourceCalendarId(),
                        input.getSourceEventId(),
                        input.getSourceDateFrom(),
                        input.getSourceDateTo(),
                        input.getSourceTimeZone(),
                        input.isAllAfterThisOccurrence(),
                        input.getCalendarDescription(),
                        input.getTimeStartLocalTime(),
                        input.getTimeEndLocalTime());

                case GoogleCalendarInput.CALFUNCTION_V_DELETEOCCURRENCE -> api.deleteCalendarOccurrence(
                        input.getGoogleAuthenticate(),
                        input.getSourceCalendarId(),
                        input.getSourceEventId(),
                        input.getSourceDateFrom(),
                        input.getSourceDateTo(),
                        input.isAllAfterThisOccurrence());

                case GoogleCalendarInput.CALFUNCTION_V_GETEVENT -> api.getEvent(
                        input.getGoogleAuthenticate(),
                        input.getSourceCalendarId(),
                        input.getSourceEventId());

                case GoogleCalendarInput.CALFUNCTION_V_SEARCHEVENT -> api.searchEvents(
                        input.getGoogleAuthenticate(),
                        input.getSourceCalendarId(),
                        input.getSourceDateFrom(),
                        input.getSourceDateTo(),
                        input.getSourceTimeZone());

                default -> throw new ConnectorException(GoogleCalendarError.BAD_INPUTPARAMETER,
                        "Unknown function: " + calFunction);
            };
            logger.info("GoogleCalendar: execute [{}] status[{}] in {} ms", calFunction, calendarStatus, System.currentTimeMillis() - beginTime);
        } catch (ConnectorException ce) {
            throw ce;
        } catch (Exception e) {
            logger.error("GoogleCalendar execution error function[{}]: {}", calFunction, e.getMessage(), e);
            if (isCredentialError(e)) {
                throw new ConnectorException(GoogleCalendarError.BAD_CREDENTIAL,
                        "Authentication to Google failed executing [" + calFunction
                                + "], check clientId/clientSecret/refreshToken: " + e.getMessage());
            }
            throw new ConnectorException(GoogleCalendarError.OPERATION_EXECUTION,
                    "Error executing function [" + calFunction + "]: " + e.getMessage());
        }

        GoogleCalendarOutput output = new GoogleCalendarOutput();
        output.populate(calendarStatus);
        return output;
    }

    @Override
    public String getDescription() {
        return "Simulate any connector";
    }

    @Override
    public String getLogo() {
        return WORKER_LOGO;
    }

    @Override
    public String getCollectionName() {
        return "";
    }

    @Override
    public Map<String, String> getListBpmnErrors() {
        return Map.of(GoogleCalendarError.BAD_INPUTPARAMETER, GoogleCalendarError.BAD_INPUTPARAMETER_EXPLANATION,
                GoogleCalendarError.OPERATION_EXECUTION, GoogleCalendarError.OPERATION_EXECUTION_EXPLANATION,
                GoogleCalendarError.DATE_PARSING_ERROR, GoogleCalendarError.DATE_PARSING_ERROR_EXPLANATION,
                GoogleCalendarError.BAD_CREDENTIAL, GoogleCalendarError.BAD_CREDENTIAL_EXPLANATION,
                GoogleCalendarError.UNKNOWN_FUNCTION, GoogleCalendarError.UNKNOWN_FUNCTION_EXPLANATION,
                GoogleCalendarError.UNKOWN_EVENT, GoogleCalendarError.UNKOWN_EVENT_EXPLANATION,
                GoogleCalendarError.INCOMPLETE_PARAMETERS, GoogleCalendarError.INCOMPLETE_PARAMETERS_EXPLANATION);
    }

    @Override
    public Class<?> getInputParameterClass() {
        return GoogleCalendarInput.class;
    }

    @Override
    public Class<?> getOutputParameterClass() {
        return GoogleCalendarOutput.class;
    }

    /**
     * Only task at this moment (no InboundConnector)
     *
     * @return list of items where the function applies
     */
    @Override
    public List<String> getAppliesTo() {
        return List.of("bpmn:Task", "bpmn:ServiceTask");
    }

    @Override
    public String getElementType() {
        return "bpmn:ServiceTask";
    }

    @Override
    public int getVersion() {
        return 0;
    }

    @Override
    public String getRelease() {
        try (InputStream is = GoogleCalendarFunction.class.getResourceAsStream(
                "/META-INF/maven/io.camunda.connector/pdf-function/pom.properties")) {
            if (is != null) {
                java.util.Properties props = new java.util.Properties();
                props.load(is);
                String version = props.getProperty("version");
                if (version != null)
                    return version;
            }
        } catch (Exception e) {
            // fall through
        }
        return "";
    }


}

package io.camunda.connector.googlecalendar;

public class GoogleCalendarError {


    public static final String BAD_INPUTPARAMETER = "BAD_INPUTPARAMETER";
    public static final String BAD_INPUTPARAMETER_EXPLANATION = "During the bind, some input does not have the expected type";

    public static final String OPERATION_EXECUTION = "OPERATION_EXECUTION";
    public static final String OPERATION_EXECUTION_EXPLANATION = "Error during an operation";

    public static final String BAD_CREDENTIAL = "BAD_CREDENTIAL";
    public static final String BAD_CREDENTIAL_EXPLANATION = "Authentication to Google failed. Check the clientId, clientSecret and refreshToken values";

    public static final String DATE_PARSING_ERROR = "DATE_PARSING_ERROR";
    public static final String DATE_PARSING_ERROR_EXPLANATION = "A date parameter cannot be parsed. Dates must be ISO-8601, e.g. \"2026-07-22T10:00:00-07:00\" or \"2026-07-22\". The message gives the source parameter (startDate, endDate, startDateOccurrence)";

    public static final String UNKNOWN_FUNCTION = "UNKNOWN_FUNCTION";
    public static final String UNKNOWN_FUNCTION_EXPLANATION = "The function is unknown";


    public static final String INCOMPLETE_PARAMETERS = "INCOMPLETE_PARAMETERS";
    public static final String INCOMPLETE_PARAMETERS_EXPLANATION = "Parameter is incomplete";

    public static final String UNKOWN_EVENT = "UNKOWN_EVENT";
    public static final String UNKOWN_EVENT_EXPLANATION = "Event does not exist";


}

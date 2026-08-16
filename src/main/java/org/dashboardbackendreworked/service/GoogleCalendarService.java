package org.dashboardbackendreworked.service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

import org.dashboardbackendreworked.models.GoogleCalendarModels.EventModel;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;

@Component
public class GoogleCalendarService implements CommandLineRunner {
    private static final String APPLICATION_NAME = "Google Calendar API Java Quickstart";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";

    private static final List<String> SCOPES =
      Collections.singletonList(CalendarScopes.CALENDAR_READONLY);
    private static final String CREDENTIALS_FILE_PATH = "/Credentials/googleCredentials.json";
     

    @Override
    public void run(String[] args) {
        List<EventModel> events = this.getCalendarEvents(10);
        for (EventModel event : events) {
            System.out.println(event);
        }
    }

    private Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
        InputStream in = GoogleCalendarAPI.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
        if (in == null) {
          throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);
        }
        GoogleClientSecrets clientSecrets =
            GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
            HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
            .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
            .setAccessType("offline")
            .build();
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(3000).build();
        Credential credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
        return credential;
      }

    public List<EventModel> getCalendarEvents(int maxEvents) {
        ObjectMapper mapper = new ObjectMapper();
        List<EventModel> eventModelList = new ArrayList<EventModel>();
        try {
            final NetHttpTransport netHttpTransport = GoogleNetHttpTransport.newTrustedTransport();
            Calendar service = new Calendar.Builder(netHttpTransport, JSON_FACTORY, this.getCredentials(netHttpTransport)).setApplicationName(APPLICATION_NAME).build();
            DateTime time = new DateTime(System.currentTimeMillis());
            Events calendarEvents = service.events()
                .list("primary")
                .setMaxResults(maxEvents)
                .setTimeMin(time)
                .setOrderBy("startTime")
                .setSingleEvents(true)
                .execute();
             List<Event> events = calendarEvents.getItems();
             if (events.isEmpty()) {
                 return Collections.emptyList(); 
             }
             for (Event event : events) {
                 JsonNode eventJson = mapper.readTree(event.toString());
                 System.out.println(eventJson.toString());
                 String id = eventJson.get("id").asText();
                 String eventTitle = eventJson.get("summary").asText();
                 LocalDate startDate = LocalDate.parse(eventJson.get("start").get("date").asText());
                 LocalDate endDate = LocalDate.parse(eventJson.get("end").get("date").asText());
                 String creatorEmail = eventJson.get("creator").get("email").asText();
                 String eventType = eventJson.get("eventType").asText();

                 EventModel eventModel = EventModel.builder()
                     .id(id)
                     .eventTitle(eventTitle)
                     .eventType(eventType)
                     .creatorEmail(creatorEmail)
                     .startDate(startDate)
                     .endDate(endDate)
                    .build();

                 eventModelList.add(eventModel);
             }
             return eventModelList;

        } catch ( Exception e ) {
            System.out.println(e.getMessage());
            return Collections.emptyList(); 
        }

    }

}

package org.dashboardbackendreworked.service.GoogleCalendarServices;

import java.util.List;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;

import org.dashboardbackendreworked.models.GoogleCalendarModels.EventModel;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;

@Service
public class GoogleCalendarService {
    private static final String APPLICATION_NAME = "Google Calendar API Java Quickstart";

    public List<EventModel> getCalendarEvents(int maxEvents) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<EventModel> eventModelList = new ArrayList<EventModel>();
        final NetHttpTransport netHttpTransport = GoogleNetHttpTransport.newTrustedTransport();
        
        Calendar service = new Calendar.Builder(netHttpTransport, GoogleCalendarAuthService.JSON_FACTORY, GoogleCalendarAuthService.getCredentials(netHttpTransport)).setApplicationName(APPLICATION_NAME).build();
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

    }

}

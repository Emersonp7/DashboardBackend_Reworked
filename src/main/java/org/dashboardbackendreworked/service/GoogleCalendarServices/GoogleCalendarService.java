package org.dashboardbackendreworked.service.GoogleCalendarServices;

import java.util.List;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;

import java.util.stream.Stream;


import org.dashboardbackendreworked.models.GoogleCalendarModels.EventModel;
import org.springframework.stereotype.Service;


import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.CalendarList;
import com.google.api.services.calendar.model.CalendarListEntry;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Events;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;

@Service
public class GoogleCalendarService {
    private static final String APPLICATION_NAME = "Google Calendar API Java Quickstart";

    private EventModel createEventModel(Event event) {
        String calendarType = "Misc.";
        EventDateTime startDate = event.getStart();
        EventDateTime endDate = event.getEnd();

        DateTime startDateTime = startDate.getDateTime();
        DateTime endDateTime = endDate.getDateTime();
        if (startDateTime == null) {
            startDateTime = startDate.getDate();
        }
        if (endDateTime == null) {
            endDateTime = endDate.getDate();
        }

        String eventDisplayName = event.getCreator().getDisplayName();
        if ( eventDisplayName == null && event.getCreator().getEmail().equals("emersonp935@gmail.com")) {
            calendarType = "Primary";
        } else if (eventDisplayName.equals("Holidays in United States")) {
            calendarType = "Holidays";
        } else if (eventDisplayName.contains("(Canvas)")) {
            calendarType = "Canvas Assignments" ;
        }


        return EventModel.builder()
                .id(event.getId())
                .eventTitle(event.getSummary())
                .location(event.getLocation())
                .description(event.getDescription())
                .eventType(event.getEventType())
                .calendarType(calendarType)
                .creatorEmail(event.getCreator().getEmail())
                .displayName(event.getCreator().getDisplayName())
                .startDate(this.getLocalDateTime(startDateTime))
                .endDate(this.getLocalDateTime(endDateTime))
            .build();
    }

    private OffsetDateTime getLocalDateTime(DateTime dateTime) {
        Instant epochMiliValue = Instant.ofEpochMilli(dateTime.getValue());
        if (dateTime.isDateOnly()) {
            return epochMiliValue.atOffset(ZoneOffset.UTC);
        }
        return epochMiliValue.atOffset(ZoneOffset.ofTotalSeconds(dateTime.getTimeZoneShift() * 60));
    }
    private Calendar getCalendarService() throws Exception {
        final NetHttpTransport netHttpTransport = GoogleNetHttpTransport.newTrustedTransport();
        return new Calendar.Builder(netHttpTransport, GoogleCalendarAuthService.JSON_FACTORY, GoogleCalendarAuthService.getCredentials(netHttpTransport)).setApplicationName(APPLICATION_NAME).build();
    }

    public List<EventModel> getDateEvents(LocalDate date) throws Exception {
        List<EventModel> eventModelList = new ArrayList<EventModel>();
        LocalDateTime localDateTime = LocalDateTime.of(date, LocalTime.MIN);
        
        Instant instant = localDateTime.atZone(ZoneId.of("America/New_York")).toInstant();
        DateTime selectedDate = new DateTime(instant.toEpochMilli());
        DateTime selectedEndDate = new DateTime(instant.plusSeconds(60*60*24).toEpochMilli());
        
        Calendar calendarService = this.getCalendarService();
        Events calendarEvents = calendarService.events()
                             .list("primary")
                             .setTimeMin(selectedDate)
                             .setTimeMax(selectedEndDate)
                             .setSingleEvents(true)
                         .execute();

        List<Event> events = calendarEvents.getItems();
        if (events.isEmpty()) {
             return Collections.emptyList(); 
        }

        for (Event event: events) {
            EventModel eventModel = this.createEventModel(event);
            eventModelList.add(eventModel);
        }

        return eventModelList;
        
    }
    
    public List<EventModel> getCalendarEvents(int maxEvents) throws Exception {
        List<EventModel> eventModelList = new ArrayList<>();
        List<String> calendarIds =  new ArrayList<>();
        DateTime time = new DateTime(System.currentTimeMillis());
        Calendar service = this.getCalendarService();
        CalendarList calendarList = service.calendarList().list().execute();
        List<Event> allCalendarEvents = new ArrayList<>();

        for (CalendarListEntry entry:  calendarList.getItems()) {
            calendarIds.add(entry.getId());
        }

        for (String id: calendarIds) {
            Events calendarEvents = service.events()
                .list(id)
                .setMaxResults(maxEvents)
                .setTimeMin(time)
                .setOrderBy("startTime")
                .setSingleEvents(true)
                .execute();
            calendarEvents.getItems().forEach(event -> allCalendarEvents.add(event));

        }

        for (Event event : allCalendarEvents) {
            EventModel eventModel = this.createEventModel(event);
            eventModelList.add(eventModel);
        }
        return eventModelList;
    }
}

package org.dashboardbackendreworked.routers.GoogleCalendarRouters;

import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate; import java.util.List;

import org.dashboardbackendreworked.models.GoogleCalendarModels.EventModel;
import org.dashboardbackendreworked.service.GoogleCalendarServices.GoogleCalendarAuthService;
import org.dashboardbackendreworked.service.GoogleCalendarServices.GoogleCalendarService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/google")
public class GoogleCalendarRouter {
    final private GoogleCalendarService calendarService;

    public GoogleCalendarRouter ( GoogleCalendarAuthService authService, GoogleCalendarService calendarService ) {
        this.calendarService = calendarService;
    }
    
    @GetMapping("/events/{date}")
    public List<EventModel> getSpecificDateEvents(@PathVariable String date) throws Exception {
        LocalDate localDate = LocalDate.parse(date);
        return this.calendarService.getDateEvents(localDate);
    }

    @GetMapping("/events")
    public List<EventModel> getAllEVents(@RequestParam(required = false) Integer limit) throws Exception {
        if (limit == null) {
            return this.calendarService.getCalendarEvents(10);
        }
        return this.calendarService.getCalendarEvents(limit); }
}

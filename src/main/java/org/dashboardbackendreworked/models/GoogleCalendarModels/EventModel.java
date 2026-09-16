package org.dashboardbackendreworked.models.GoogleCalendarModels;

import java.time.OffsetDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EventModel {
    private String eventTitle;
    private String location;
    private String description;
    private OffsetDateTime startDate;
    private OffsetDateTime endDate;
    private String eventType;
    private String creatorEmail;
    private String id;

    @Override
    public String toString() {
        return String.format("Event: %s - Date: %s", this.eventTitle, this.startDate.toString());
    }
}

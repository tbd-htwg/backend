package com.tripplanning.like;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LikeId implements java.io.Serializable {
    private Long user_id;
    private Long trip_id;
}

//Embedded ID: Ein Paar aus user_id und trip_id darf nur einmal existieren
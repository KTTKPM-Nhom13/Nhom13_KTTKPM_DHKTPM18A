package com.cab.booking.integration.driver;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverDto {

    private String id;
    private String name;
    private String vehicleType;
    private Double rating;
    private List<String> tags;
}

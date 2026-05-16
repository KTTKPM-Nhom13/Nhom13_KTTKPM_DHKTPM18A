package iuh.fit.pricing_service.service;

import org.springframework.stereotype.Service;

@Service
public class ZoneService {

    public String determineZone(double lat, double lng) {
        int gridSize = 1;
        int latZone = (int) Math.floor(lat * gridSize);
        int lngZone = (int) Math.floor(lng * gridSize);
        return String.format("Z%02d%02d", latZone + 50, lngZone + 100);
    }
}

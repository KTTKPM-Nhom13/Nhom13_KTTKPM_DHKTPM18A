package iuh.fit.pricing_service.service;

import org.springframework.stereotype.Service;

@Service
public class ZoneService {

    private static final int GRID_SCALE = 1000;
    private static final int LAT_OFFSET = 90 * GRID_SCALE;
    private static final int LNG_OFFSET = 180 * GRID_SCALE;

    public String determineZone(double lat, double lng) {
        int latZone = (int) Math.floor(lat * GRID_SCALE) + LAT_OFFSET;
        int lngZone = (int) Math.floor(lng * GRID_SCALE) + LNG_OFFSET;
        return String.format("Z%06d%06d", latZone, lngZone);
    }
}

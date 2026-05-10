package com.cab.matching.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingService {

    private final RedisTemplate<String, String> redisTemplate;

    // Key lưu trữ vị trí tài xế trên Redis (sử dụng cấu trúc dữ liệu GEO)
    private static final String DRIVER_LOCATION_KEY = "driver_locations";

    /**
     * Tìm kiếm các tài xế gần nhất dựa trên tọa độ đón khách.
     * Sử dụng lệnh GEOSEARCH của Redis để tối ưu hóa truy vấn không gian.
     *
     * @param pickupLat Vĩ độ điểm đón
     * @param pickupLng Kinh độ điểm đón
     * @return Danh sách ID các tài xế tìm thấy
     */
    public List<String> findNearestDrivers(Double pickupLat, Double pickupLng) {
        log.info("Bắt đầu tìm kiếm tài xế gần nhất cho tọa độ: [Lat: {}, Lng: {}]", pickupLat, pickupLng);

        // Tạo tham chiếu tọa độ đón khách (Lưu ý: Redis lưu tọa độ theo thứ tự Kinh độ trước - Vĩ độ sau)
        GeoReference<String> pickupLocation = GeoReference.fromCoordinate(pickupLng, pickupLat);

        // Định nghĩa bán kính tìm kiếm: 3 Kilometers
        Distance searchRadius = new Distance(3.0, Metrics.KILOMETERS);

        // Cấu hình tham số tìm kiếm: 
        // - includeDistance(): Trả về kèm khoảng cách
        // - sortAscending(): Sắp xếp từ gần đến xa
        // - limit(10): Lấy tối đa 10 tài xế
        RedisGeoCommands.GeoSearchCommandArgs args = RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs()
                .includeDistance()
                .sortAscending()
                .limit(10);

        List<String> nearestDriverIds = new ArrayList<>();

        try {
            // Thực hiện gọi hàm search() trên Redis để tìm tài xế
            GeoResults<RedisGeoCommands.GeoLocation<String>> results = redisTemplate.opsForGeo()
                    .search(DRIVER_LOCATION_KEY, pickupLocation, searchRadius, args);

            if (results != null && !results.getContent().isEmpty()) {
                for (GeoResult<RedisGeoCommands.GeoLocation<String>> result : results.getContent()) {
                    String driverId = result.getContent().getName();
                    Distance distance = result.getDistance();
                    
                    // Log ra khoảng cách của từng tài xế tìm được theo yêu cầu
                    log.info("Tìm thấy tài xế: ID = {}, Khoảng cách = {} {}", 
                            driverId, distance.getValue(), distance.getMetric().getAbbreviation());
                            
                    nearestDriverIds.add(driverId);
                }
            } else {
                log.info("Không tìm thấy tài xế nào khả dụng trong bán kính 3km.");
            }
        } catch (Exception e) {
            log.error("Có lỗi xảy ra khi truy vấn vị trí tài xế trên Redis: {}", e.getMessage(), e);
        }

        return nearestDriverIds;
    }
}

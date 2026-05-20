-- One-time data cleanup before/after switching Booking.vehicleType to the BIKE/CAR4/CAR7 enum.
-- Run only if existing booking rows contain legacy vehicle_type values.

UPDATE bookings
SET vehicle_type = 'BIKE'
WHERE UPPER(vehicle_type) IN ('MOTORBIKE', 'MOTORCYCLE');

UPDATE bookings
SET vehicle_type = 'CAR4'
WHERE UPPER(vehicle_type) IN ('CAR', 'CAR_4', 'SEDAN', 'ECONOMY');

UPDATE bookings
SET vehicle_type = 'CAR7'
WHERE UPPER(vehicle_type) IN ('CAR_7', 'SUV');

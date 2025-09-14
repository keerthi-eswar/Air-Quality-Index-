package com.example.aqi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import org.json.JSONArray;
import org.json.JSONObject;

@SpringBootApplication
@RestController
@RequestMapping("/api/aqi")
public class AqiApplication {

    @Value("${openweathermap.api.key}")
    private String apiKey; // Put your API key in application.properties

    private final RestTemplate restTemplate = new RestTemplate();

    public static void main(String[] args) {
        SpringApplication.run(AqiApplication.class, args);
    }

    // ---------------- Get AQI by City ----------------
    @GetMapping("/city/{city}")
    public ResponseEntity<?> getAqiByCity(@PathVariable String city) {
        try {
            // Step 1: Convert city -> lat/lon
            String geoUrl = "https://api.openweathermap.org/geo/1.0/direct?q=" + city + "&limit=1&appid=" + apiKey;
            String geoResponse = restTemplate.getForObject(geoUrl, String.class);

            JSONArray arr = new JSONArray(geoResponse);
            if (arr.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "City not found"));
            }
            JSONObject geo = arr.getJSONObject(0);
            double lat = geo.getDouble("lat");
            double lon = geo.getDouble("lon");
            String place = geo.getString("name") + ", " + geo.getString("country");

            return fetchData(lat, lon, place);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ---------------- Get AQI by Coordinates ----------------
    @GetMapping("/coords")
    public ResponseEntity<?> getAqiByCoords(@RequestParam double lat, @RequestParam double lon) {
        try {
            return fetchData(lat, lon, "Your Location");
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ---------------- Helper: Fetch AQI + Weather ----------------
    private ResponseEntity<?> fetchData(double lat, double lon, String place) {
        // AQI
        String aqiUrl = "https://api.openweathermap.org/data/2.5/air_pollution?lat=" + lat + "&lon=" + lon + "&appid=" + apiKey;
        String aqiResponse = restTemplate.getForObject(aqiUrl, String.class);
        JSONObject aqiJson = new JSONObject(aqiResponse).getJSONArray("list").getJSONObject(0);

        int aqi = aqiJson.getJSONObject("main").getInt("aqi");
        JSONObject comps = aqiJson.getJSONObject("components");
        long updated = aqiJson.getLong("dt") * 1000;

        // Weather
        String weatherUrl = "https://api.openweathermap.org/data/2.5/weather?lat=" + lat + "&lon=" + lon + "&appid=" + apiKey + "&units=metric";
        String weatherResponse = restTemplate.getForObject(weatherUrl, String.class);
        JSONObject weatherJson = new JSONObject(weatherResponse);
        double temp = weatherJson.getJSONObject("main").getDouble("temp");
        String desc = weatherJson.getJSONArray("weather").getJSONObject(0).getString("description");
        String icon = weatherJson.getJSONArray("weather").getJSONObject(0).getString("icon");

        // Interpret AQI
        Map<Integer, Map<String, String>> levels = Map.of(
                1, Map.of("text", "Good 😊", "color", "#4CAF50", "advice", "Air quality is excellent. Enjoy outdoor activities."),
                2, Map.of("text", "Fair 🙂", "color", "#8BC34A", "advice", "Air quality is acceptable for most people."),
                3, Map.of("text", "Moderate 😐", "color", "#FFC107", "advice", "Sensitive individuals should limit outdoor exertion."),
                4, Map.of("text", "Poor 😷", "color", "#FF5722", "advice", "Unhealthy for sensitive groups. Reduce outdoor activity."),
                5, Map.of("text", "Very Poor ☠️", "color", "#B71C1C", "advice", "Hazardous! Stay indoors and avoid outdoor activities.")
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("place", place);
        result.put("aqi", aqi);
        result.put("level", levels.getOrDefault(aqi, Map.of("text", "Unknown", "color", "#999", "advice", "No data")));
        result.put("updated", new Date(updated).toString());

        // Pollutants
        Map<String, Object> pollutants = new LinkedHashMap<>();
        for (String key : comps.keySet()) {
            pollutants.put(key, comps.getDouble(key));
        }
        result.put("pollutants", pollutants);

        // Weather
        result.put("weather", Map.of(
                "temperature", temp,
                "description", desc,
                "icon", icon
        ));

        return ResponseEntity.ok(result);
    }
}

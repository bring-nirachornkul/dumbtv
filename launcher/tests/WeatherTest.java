package tv.dumbtv.home;

import java.util.Arrays;

/** Host-side checks for Options › Weather, without a device or test framework. */
public final class WeatherTest {
    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
    }

    public static void main(String[] args) {
        long now = 10 * Weather.STALE_MS;
        check(Weather.fresh(72, now - 60_000L, now), "a minute-old reading shows");
        check(!Weather.fresh(72, now - Weather.STALE_MS, now), "a three-hour-old reading hides");
        check(!Weather.fresh(null, now, now), "no reading hides");
        check(Weather.RETRY_MS < Weather.REFRESH_MS, "a failed request retries sooner than a refresh");

        check(Weather.key(" Charlotte ", "f").equals(Weather.key("charlotte", "f")), "case and spaces do not matter");
        check(!Weather.key("Charlotte", "f").equals(Weather.key("Charlotte", "c")), "another unit starts over");
        check("fahrenheit".equals(Weather.unitParam("f")), "°F asks for fahrenheit");
        check("celsius".equals(Weather.unitParam("c")), "°C asks for celsius");

        check("off".equals(Config.WEATHER.fallback), "hidden unless turned on: the only network use");
        check(Arrays.asList(Config.CHOICES).contains(Config.WEATHER), "Weather shows in Options");
        System.out.println("WeatherTest passed");
    }
}

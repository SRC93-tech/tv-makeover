package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/** Plain JVM tests for the sunset palette: anchors, interpolation and clamping. */
public class SkyTest {

    @Test
    public void nightLooksTheSameAtBothEndsOfTheDay() {
        Sky.Palette first = Sky.at(0);
        Sky.Palette last = Sky.at(1439);
        assertEquals(first.top, last.top);
        assertEquals(first.mid, last.mid);
        assertEquals(first.bottom, last.bottom);
        assertEquals(0, last.sun);
    }

    @Test
    public void glowPeaksAtSunriseAndSunset() {
        assertTrue(Sky.at(450).sun > Sky.at(780).sun);       // 07:30 sunrise vs 13:00 day
        assertTrue(Sky.at(1140).sun > Sky.at(1260).sun);     // 19:00 sunset vs 21:00 dusk
        assertTrue(Sky.at(1140).sun > Sky.at(450).sun);      // the evening is the warmest moment
        assertEquals(0, Sky.at(60).sun);
        assertEquals(0, Sky.at(1400).sun);
    }

    @Test
    public void theGlowTracksTheSunFromLeftToRight() {
        assertTrue(Sky.at(390).sunX < Sky.at(780).sunX);
        assertTrue(Sky.at(780).sunX < Sky.at(1140).sunX);
        assertTrue(Sky.at(780).sunX > 0.4f && Sky.at(780).sunX < 0.6f);   // midday is near the centre
    }

    @Test
    public void coloursInterpolateBetweenAnchors() {
        Sky.Palette dawn = Sky.at(390);
        Sky.Palette sunrise = Sky.at(450);
        Sky.Palette half = Sky.at(420);
        assertEquals(Sky.lerpColor(dawn.top, sunrise.top, 0.5f), half.top);
        assertNotEquals(dawn.top, half.top);
        assertNotEquals(sunrise.top, half.top);
    }

    @Test
    public void timeIsClampedAndAnsweredEveryMinuteOfTheDay() {
        assertEquals(Sky.at(0).top, Sky.at(-30).top);
        assertEquals(Sky.at(1440).top, Sky.at(4000).top);
        for (int minute = 0; minute < 1440; minute += 5) {
            Sky.Palette p = Sky.at(minute);
            double top = luma(p.top);
            double mid = luma(p.mid);
            // The header and the clock sit on the top two thirds; they stay on dark ground all day.
            assertTrue("top too bright at " + minute, top < 0.4);
            assertTrue("mid too bright at " + minute, mid < 0.4);
        }
        assertEquals(1439, Sky.minuteOfDay(23, 59));
        assertEquals(0, Sky.minuteOfDay(0, 0));
    }

    /** Rough relative brightness of an opaque ARGB colour, 0 to 1. */
    private static double luma(int color) {
        return (0.2126 * ((color >> 16) & 0xFF) + 0.7152 * ((color >> 8) & 0xFF) + 0.0722 * (color & 0xFF)) / 255.0;
    }

    @Test
    public void solarIntensityTransitionsAroundSunriseAndSunset() {
        int rise = 6 * 60 + 20;  // 06:20
        int set = 18 * 60 + 10;  // 18:10
        assertEquals(0, Sky.solarIntensity(12 * 60, rise, set, "auto"));
        assertEquals(78, Sky.solarIntensity(23 * 60, rise, set, "auto"));
        assertEquals(78, Sky.solarIntensity(2 * 60, rise, set, "auto"));
        int midSunset = Sky.solarIntensity(set, rise, set, "auto");
        assertTrue("expected partial sunset intensity, got " + midSunset, midSunset > 0 && midSunset < 78);
        assertEquals(82, Sky.solarIntensity(12 * 60, rise, set, "night"));
        assertEquals(0, Sky.solarIntensity(23 * 60, rise, set, "off"));
    }

    @Test
    public void weatherIsoAndTemperatureFormattingWorkOnJvm() {
        assertEquals(6 * 60 + 24, Weather.parseIsoMinute("2026-11-12T06:24"));
        assertEquals(18 * 60 + 5, Weather.parseIsoMinute("2026-11-12T18:05"));
        assertEquals(-1, Weather.parseIsoMinute("bad"));
        assertEquals("25°C", Weather.formatTemp(24.7, "C"));
        assertEquals("77°F", Weather.formatTemp(25.0, "F"));
        assertEquals(R.drawable.ic_weather_sun, Weather.iconRes(0, true));
        assertEquals(R.drawable.ic_weather_moon, Weather.iconRes(0, false));
        assertEquals(R.drawable.ic_weather_partly_cloudy_day, Weather.iconRes(2, true));
        assertEquals(R.drawable.ic_weather_partly_cloudy_night, Weather.iconRes(2, false));
        assertEquals(R.drawable.ic_weather_cloudy, Weather.iconRes(3, true));
        assertEquals(R.drawable.ic_weather_fog, Weather.iconRes(45, true));
        assertEquals(R.drawable.ic_weather_rain, Weather.iconRes(61, true));
        assertEquals(R.drawable.ic_weather_snow, Weather.iconRes(71, true));
        assertEquals(R.drawable.ic_weather_storm, Weather.iconRes(95, true));
    }
}

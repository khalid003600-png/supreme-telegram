package com.wolfox.gps.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Pure parser data cases: no short-link expansion and no network access. */
public class MapLinkParserRegressionTest {
    @Test
    public void parsesArabicDigitsAndPunctuationAsCoordinates() {
        MapLinkParser.Parsed parsed = MapLinkParser.parse("٢٤٫٧١٣٦، ٤٦٫٦٧٥٣");

        assertNotNull(parsed);
        assertTrue(parsed.hasCoordinates());
        assertEquals(24.7136d, parsed.getLatitude(), 0.000001d);
        assertEquals(46.6753d, parsed.getLongitude(), 0.000001d);
        assertFalse(parsed.isMapLink());
    }

    @Test
    public void extractsGoogleCoordinatesFromTextAroundALink() {
        MapLinkParser.Parsed parsed = MapLinkParser.parse(
                "افتح https://www.google.com/maps/search/?api=1&query=24.7136%2C46.6753.");

        assertNotNull(parsed);
        assertTrue(parsed.isMapLink());
        assertEquals("Google Maps", parsed.getProvider());
        assertTrue(parsed.hasCoordinates());
        assertEquals(24.7136d, parsed.getLatitude(), 0.000001d);
        assertEquals(46.6753d, parsed.getLongitude(), 0.000001d);
    }

    @Test
    public void invalidCoordinateBoundsRemainANonCoordinateQuery() {
        MapLinkParser.Parsed plain = MapLinkParser.parse("91, 181");
        MapLinkParser.Parsed geo = MapLinkParser.parse("geo:0,0?q=91,181");

        assertNotNull(plain);
        assertFalse(plain.hasCoordinates());
        assertEquals("91, 181", plain.getQuery());
        assertNotNull(geo);
        assertTrue(geo.isMapLink());
        assertFalse(geo.hasCoordinates());
    }

    @Test
    public void rejectsOversizedInputBeforeAnyParsingWork() {
        StringBuilder oversized = new StringBuilder();
        for (int i = 0; i < 8193; i++) oversized.append('x');

        assertNull(MapLinkParser.parse(oversized.toString()));
    }
}

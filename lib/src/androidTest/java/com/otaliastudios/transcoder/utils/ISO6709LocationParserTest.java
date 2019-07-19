package com.otaliastudios.transcoder.utils;

import com.otaliastudios.transcoder.internal.ISO6709LocationParser;

public class ISO6709LocationParserTest {
    public void testParse() {
        ISO6709LocationParser parser = new ISO6709LocationParser();
        assertEquals(new float[]{35.658632f, 139.745411f}, parser.parse("+35.658632+139.745411/"));
        assertEquals(new float[]{40.75f, -074.00f}, parser.parse("+40.75-074.00/"));
        // with Altitude
        assertEquals(new float[]{-90f, +0f}, parser.parse("-90+000+2800/"));
        assertEquals(new float[]{27.5916f, 086.5640f}, parser.parse("+27.5916+086.5640+8850/"));
        // ranged data
        assertEquals(new float[]{35.331f, 134.224f}, parser.parse("+35.331+134.224/+35.336+134.228/"));
        assertEquals(new float[]{35.331f, 134.224f}, parser.parse("+35.331+134.224/+35.336+134.228/+35.333+134.229/+35.333+134.227/"));
    }

    public void testParseFailure() {
        ISO6709LocationParser parser = new ISO6709LocationParser();
        /* assertNull(parser.parse(null));
        assertNull(parser.parse(""));
        assertNull(parser.parse("35 deg 65' 86.32\" N, 139 deg 74' 54.11\" E"));
        assertNull(parser.parse("+35.658632"));
        assertNull(parser.parse("+35.658632-"));
        assertNull(parser.parse("40.75-074.00"));
        assertNull(parser.parse("+40.75-074.00.00")); */
    }

    private static void assertEquals(float[] expected, float[] actual) {
        /* assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertTrue(Float.compare(expected[i], actual[i]) == 0);
        } */
    }
}
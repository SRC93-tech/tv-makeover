package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EyeGazeMathTest {

    @Test
    public void centerTargetGivesZeroOffset() {
        float[] out = new float[2];
        EyeGazeMath.computeOffset(850f, 420f, 850f, 420f, 16f, 6f, 650f, out);
        assertEquals(0f, out[0], 0.0001f);
        assertEquals(0f, out[1], 0.0001f);
    }

    @Test
    public void directionalTrackingIsBounded() {
        float[] out = new float[2];
        EyeGazeMath.computeOffset(900f, 420f, 100f, 950f, 15f, 5f, 600f, out);
        assertTrue("looking left gives negative dx", out[0] < 0f && out[0] >= -15f);
        assertTrue("looking down gives positive dy", out[1] > 0f && out[1] <= 5f);

        EyeGazeMath.computeOffset(900f, 420f, 1850f, 40f, 15f, 5f, 600f, out);
        assertTrue("looking right gives positive dx", out[0] > 0f && out[0] <= 15f);
        assertTrue("looking up gives negative dy", out[1] < 0f && out[1] >= -5f);
    }

    @Test
    public void blinkCurvePeaksSmoothlyInMiddle() {
        assertEquals(0f, EyeGazeMath.blinkClosure(0f), 0.001f);
        assertEquals(1f, EyeGazeMath.blinkClosure(0.45f), 0.01f);
        assertEquals(0f, EyeGazeMath.blinkClosure(1f), 0.001f);
    }

    @Test
    public void wrapDegreesNormalizesRange() {
        assertEquals(0f, EyeGazeMath.wrapDegrees(720f), 0.001f);
        assertEquals(90f, EyeGazeMath.wrapDegrees(450f), 0.001f);
        assertEquals(270f, EyeGazeMath.wrapDegrees(-90f), 0.001f);
    }
}


package com.routeplanner.util;

import java.awt.BasicStroke;

/** Round-capped strokes, made once per width instead of on every frame. */
public final class Strokes {

    private static final BasicStroke[] ROUND = new BasicStroke[33];

    private Strokes() {}

    /** A solid stroke with round caps and joins, as the route lines use. */
    public static BasicStroke round(int width) {
        if (width < 0 || width >= ROUND.length) {
            return new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        }
        BasicStroke s = ROUND[width];
        if (s == null) {
            s = new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
            ROUND[width] = s;
        }
        return s;
    }
}

package com.gathin.tvhome;

/** Physical left/right boundaries for a grid or single horizontal bar whose first tile follows reading direction. */
final class GridNavigation {
    private GridNavigation() { }

    static boolean atHorizontalEdge(int position, int count, int columns, boolean rtl, boolean left) {
        boolean towardStart = left != rtl;
        if (towardStart) return position % columns == 0;
        return position % columns == columns - 1 || position == count - 1;
    }

    /**
     * Next item position for Left/Right navigation in a single horizontal bar, or -1 at either end.
     */
    static int nextHorizontalPosition(int position, int count, boolean rtl, boolean left) {
        if (count <= 0 || position < 0 || position >= count) return -1;
        boolean forward = left == rtl;
        int next = forward ? position + 1 : position - 1;
        return next >= 0 && next < count ? next : -1;
    }
}

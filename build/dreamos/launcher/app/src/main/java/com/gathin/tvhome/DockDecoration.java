package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Draws the band behind the first (dock) row and adds a gap below it. The default band is DreamOS glass
 * (GlassDrawable) with 35px corners, 40px / 41.5px padding left and right of the tiles and 28px / 29px above
 * and below them on the 1080p design.
 */
public class DockDecoration extends RecyclerView.ItemDecoration {

    private final int dockSize;
    private final float density;
    private final boolean translucent;
    private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final GlassDrawable glass;
    private final Rect glassBounds = new Rect();

    /** @param translucent the cinematic shelf's band: the artwork and the glow show through it. */
    DockDecoration(int dockSize, float density, boolean translucent) {
        this.dockSize = dockSize;
        this.density = density;
        this.translucent = translucent;
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(density);
        band.setColor(0x8C121014);
        edge.setColor(0x26FFFFFF);
        glass = translucent ? null : new GlassDrawable(density, 17.5f * density);
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
                               @NonNull RecyclerView.State state) {
        int pos = parent.getChildAdapterPosition(view);
        if (pos >= 0 && pos < dockSize) {
            outRect.top = (int) (6 * density);
            outRect.bottom = (int) (12 * density);
        }
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        boolean any = false;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            int pos = parent.getChildAdapterPosition(child);
            if (pos < 0 || pos >= dockSize) continue;
            RecyclerView.ViewHolder vh = parent.getChildViewHolder(child);
            View card = vh instanceof AppAdapter.Holder ? ((AppAdapter.Holder) vh).card : child.findViewById(R.id.card);
            if (card == null) continue;
            float l = child.getLeft() + card.getLeft();
            float t = child.getTop() + card.getTop() + child.getTranslationY();
            float r = l + card.getWidth();
            float b = t + card.getHeight();
            if (!any) {
                rect.set(l, t, r, b);
                any = true;
            } else {
                rect.union(l, t, r, b);
            }
        }
        if (!any) return;
        if (translucent) {
            rect.inset(-14 * density, -14 * density);
            float radius = 26 * density;
            c.drawRoundRect(rect, radius, radius, band);
            c.drawRoundRect(rect, radius, radius, edge);
            return;
        }
        glassBounds.set(Math.round(rect.left - 20 * density), Math.round(rect.top - 14 * density),
                Math.round(rect.right + 20.75f * density), Math.round(rect.bottom + 14.5f * density));
        glass.setBounds(glassBounds);
        glass.draw(c);
    }
}

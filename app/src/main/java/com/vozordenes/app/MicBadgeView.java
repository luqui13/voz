package com.vozordenes.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Testigo redondo con un micrófono dibujado; cambia de color según el estado. No es pulsable. */
public class MicBadgeView extends View {
    public static final int LISTENING = 1, OK = 2, FAIL = 3;

    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private int state = LISTENING;

    public MicBadgeView(Context c) {
        super(c);
        fg.setColor(0xFFFFFFFF);
    }

    public void setState(int s) {
        state = s;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        int color;
        switch (state) {
            case OK: color = 0xFF2E7D32; break;
            case FAIL: color = 0xFFEF6C00; break;
            default: color = 0xFFD32F2F;
        }
        bg.setColor(color);
        float w = getWidth(), h = getHeight();
        float rad = Math.min(w, h) / 2f;
        c.drawCircle(w / 2, h / 2, rad - 2, bg);
        float u = rad / 10f;
        fg.setStyle(Paint.Style.FILL);
        r.set(w / 2 - 2 * u, h / 2 - 5.5f * u, w / 2 + 2 * u, h / 2 + 1.5f * u);
        c.drawRoundRect(r, 2 * u, 2 * u, fg);
        fg.setStyle(Paint.Style.STROKE);
        fg.setStrokeWidth(u * 0.9f);
        r.set(w / 2 - 3.6f * u, h / 2 - 3.5f * u, w / 2 + 3.6f * u, h / 2 + 3.5f * u);
        c.drawArc(r, 0, 180, false, fg);
        c.drawLine(w / 2, h / 2 + 3.5f * u, w / 2, h / 2 + 6 * u, fg);
        c.drawLine(w / 2 - 2 * u, h / 2 + 6 * u, w / 2 + 2 * u, h / 2 + 6 * u, fg);
    }
}

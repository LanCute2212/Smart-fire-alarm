package com.example.firealarmapp.views;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple Floor Map view — draws rooms and sensor dots.
 * Red pulsing dot = DANGER, Green = SAFE, Yellow = WARNING.
 */
public class FloorMapView extends View {

    public static class SensorPin {
        public String deviceId;
        public String label;
        public float xRatio; // 0.0 - 1.0 relative to view width
        public float yRatio; // 0.0 - 1.0 relative to view height
        public int status; // 0=SAFE, 1=WARNING, 2=DANGER

        public SensorPin(String deviceId, String label, float xRatio, float yRatio, int status) {
            this.deviceId = deviceId;
            this.label = label;
            this.xRatio = xRatio;
            this.yRatio = yRatio;
            this.status = status;
        }
    }

    private final Paint wallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint roomLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint safePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint warnPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dangerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pulsePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final List<SensorPin> pins = new ArrayList<>();
    private float pulseAlpha = 1f;
    private ValueAnimator pulseAnimator;

    public FloorMapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FloorMapView(Context context) {
        super(context);
        init();
    }

    private void init() {
        wallPaint.setColor(Color.parseColor("#334155"));
        wallPaint.setStyle(Paint.Style.STROKE);
        wallPaint.setStrokeWidth(3f);

        roomLabelPaint.setColor(Color.parseColor("#64748b"));
        roomLabelPaint.setTextSize(28f);
        roomLabelPaint.setTextAlign(Paint.Align.CENTER);

        safePaint.setColor(Color.parseColor("#10b981"));
        safePaint.setStyle(Paint.Style.FILL);

        warnPaint.setColor(Color.parseColor("#f59e0b"));
        warnPaint.setStyle(Paint.Style.FILL);

        dangerPaint.setColor(Color.parseColor("#ef4444"));
        dangerPaint.setStyle(Paint.Style.FILL);

        pulsePaint.setColor(Color.parseColor("#ef4444"));
        pulsePaint.setStyle(Paint.Style.FILL);

        labelPaint.setColor(Color.WHITE);
        labelPaint.setTextSize(26f);
        labelPaint.setTextAlign(Paint.Align.CENTER);

        // Pulse animation
        pulseAnimator = ValueAnimator.ofFloat(0.1f, 0.6f);
        pulseAnimator.setDuration(800);
        pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
        pulseAnimator.setInterpolator(new LinearInterpolator());
        pulseAnimator.addUpdateListener(anim -> {
            pulseAlpha = (float) anim.getAnimatedValue();
            invalidate();
        });
        pulseAnimator.start();
    }

    /** Set pins from outside (DashboardActivity) */
    public void setSensorPins(List<SensorPin> pins) {
        this.pins.clear();
        this.pins.addAll(pins);
        invalidate();
    }

    /** Convenience to update a single pin's status */
    public void updatePinStatus(String deviceId, int status) {
        for (SensorPin p : pins) {
            if (p.deviceId.equals(deviceId)) {
                p.status = status;
                invalidate();
                return;
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();

        // Draw floor outline + simple room dividers
        // Outer wall
        canvas.drawRect(20, 20, w - 20, h - 20, wallPaint);
        // Vertical divider (center)
        canvas.drawLine(w / 2f, 20, w / 2f, h - 20, wallPaint);
        // Horizontal divider
        canvas.drawLine(20, h * 0.55f, w - 20, h * 0.55f, wallPaint);

        // Room labels
        canvas.drawText("Living Room", w * 0.25f, h * 0.28f, roomLabelPaint);
        canvas.drawText("Bedroom", w * 0.75f, h * 0.28f, roomLabelPaint);
        canvas.drawText("Kitchen", w * 0.25f, h * 0.78f, roomLabelPaint);
        canvas.drawText("Bathroom", w * 0.75f, h * 0.78f, roomLabelPaint);

        // Draw sensor pins
        for (SensorPin pin : pins) {
            float cx = pin.xRatio * w;
            float cy = pin.yRatio * h;
            float radius = 18f;

            // Pulse ring for DANGER
            if (pin.status == 2) {
                pulsePaint.setAlpha((int) (pulseAlpha * 255));
                canvas.drawCircle(cx, cy, radius + 14f, pulsePaint);
            }

            // Dot color
            Paint dotPaint = pin.status == 2 ? dangerPaint : (pin.status == 1 ? warnPaint : safePaint);
            canvas.drawCircle(cx, cy, radius, dotPaint);

            // Label below
            canvas.drawText(pin.label, cx, cy + radius + 26f, labelPaint);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        pulseAnimator.cancel();
    }
}

package app.pivo.android.prosdkdemo.camera;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;

/**
 * @author murodjon
 * Graphic instance for rendering object position, orientation
 * graphic overlay view.
 */
public class ActionGraphic extends GraphicOverlay.Graphic {

    private Paint trackPaint;

    private volatile Rect objRegion;

    public ActionGraphic(GraphicOverlay overlay, Rect objBox) {
        super(overlay);

        this.objRegion = new Rect((int)(objBox.left*getWidthScaleFactor()), (int)(objBox.top*getHeightScaleFactor()), (int)(objBox.right*getWidthScaleFactor()), (int)(objBox.bottom*getHeightScaleFactor()));

        trackPaint = new Paint();
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setColor(Color.RED);
        trackPaint.setStrokeWidth(5);
    }

    /**
     * Draws the object for position on the supplied canvas.
     */
    @Override
    public void draw(Canvas canvas) {
        Rect region = objRegion;
        if (region == null) {
            return;
        }

        canvas.drawRect(region, trackPaint);

    }
}

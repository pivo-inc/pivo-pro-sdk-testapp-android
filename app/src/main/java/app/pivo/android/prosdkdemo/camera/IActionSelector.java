package app.pivo.android.prosdkdemo.camera;

import android.graphics.Rect;

public interface IActionSelector {
    void onReset();
    void onSelect(Rect region);
}

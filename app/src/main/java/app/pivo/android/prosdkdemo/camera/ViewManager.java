package app.pivo.android.prosdkdemo.camera;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.provider.Settings;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.view.animation.TranslateAnimation;

/**
 * Created by Murodjon on 2/26/19.
 *
 * This manager class is responsible for modifying
 * UI views' properties programmatically
 */

public class ViewManager {

    public static void slideShow(Activity activity, View... views) {
        for (int i=0;i<views.length;i++){
            int viewIndex = i;
            TranslateAnimation viewTranstion = new TranslateAnimation(views[i].getWidth()*(i+1), 0, 0, 0);
            viewTranstion.setDuration(10*(viewIndex+1));
            viewTranstion.setFillAfter(true);
            viewTranstion.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                    views[viewIndex].setVisibility(View.VISIBLE);
                    views[viewIndex].setAlpha(0.0f);
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    views[viewIndex].setAlpha(1.0f);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }
            });
            views[i].startAnimation(viewTranstion);
        }
    }

    public static void slideHide(boolean portrait, View... views) {
        for (int i=views.length-1;i>=0;i--){
            int viewIndex = i;
            TranslateAnimation viewTransition = new TranslateAnimation(0, views[i].getWidth()*(i+1), 0, 0);
            viewTransition.setDuration(10*(i+1));
            if (portrait){
                viewTransition = new TranslateAnimation(0, views[i].getWidth()*(i+1), 0, 0);
                viewTransition.setDuration(10*(views.length-i));
            }

            viewTransition.setFillAfter(true);
            viewTransition.setAnimationListener(new Animation.AnimationListener() {

                @Override
                public void onAnimationStart(Animation animation) {
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    views[viewIndex].setVisibility(View.GONE);
                    views[viewIndex].setAlpha(0.0f);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }
            });

            views[i].startAnimation(viewTransition);
        }
    }

    public static void slideHide(Activity activity, View... views) {
        for (int i=views.length-1;i>=0;i--){
            int viewIndex = i;
            TranslateAnimation viewTransition = new TranslateAnimation(0, 0, 0, views[i].getWidth()*(i+1));
            viewTransition.setDuration(10*(i+1));
            if (ViewManager.isPortrait(activity)){
                viewTransition = new TranslateAnimation(0, views[i].getWidth()*(i+1), 0, 0);
                viewTransition.setDuration(10*(views.length-i));
            }

            viewTransition.setFillAfter(true);
            viewTransition.setAnimationListener(new Animation.AnimationListener() {

                @Override
                public void onAnimationStart(Animation animation) {
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    views[viewIndex].setVisibility(View.GONE);
                    views[viewIndex].setAlpha(0.0f);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }
            });

            views[i].startAnimation(viewTransition);
        }
    }

    public static boolean isOrientationLocked(Context context){
        return Settings.System.getInt(context.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0) != 1;
    }

    public static boolean isPortrait(Context context){
        int currentOrientation = context.getResources().getConfiguration().orientation;
        return currentOrientation != Configuration.ORIENTATION_LANDSCAPE;
    }

    public static void lockOrientaion(Activity activity, boolean lock){
        int currentOrientation = activity.getResources().getConfiguration().orientation;
        if (lock){
            if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            }
            else {
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
            }
        }else {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
    }

    public static void rotateViewContinuously(View view){
        RotateAnimation rotateAnimation = new RotateAnimation(0, 360f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f);

        rotateAnimation.setInterpolator(new LinearInterpolator());
        rotateAnimation.setDuration(2300);
        rotateAnimation.setRepeatCount(Animation.INFINITE);

        view.startAnimation(rotateAnimation);
    }

    public static void rotateUIViews(int angle, View... views){
        for (View view:views){
            view.setRotation(angle);
        }
    }

    public static void hideViews(View... views){
        for (View view:views){
            view.setVisibility(View.INVISIBLE);
        }
    }

    public static void hideGoneViews(View... views){
        for (View view:views){
            view.setVisibility(View.GONE);
        }
    }

    public static void showViews(View... views){
        for (View view:views){
            view.setVisibility(View.VISIBLE);
        }
    }
}

package com.wolfox.gps.compat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/** Regression tests for successive gallery selections and the image shown in the local preview. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class FeatureIntegrationImagePreviewTest {
    private static final int RESULT_OK = Activity.RESULT_OK;
    private static final String PREVIEW_DESCRIPTION = "معاينة الصورة المحلية المختارة";

    private Activity activity;
    private AlertDialog dialog;
    private ImageView preview;
    private final List<File> temporaryImages = new ArrayList<>();

    @Before
    public void setUp() throws Exception {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        FeatureIntegration.showCamera(activity);
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        dialog = (AlertDialog) ShadowDialog.getLatestDialog();
        assertNotNull("Camera and photos dialog should open", dialog);
        preview = findPreview(dialog.getWindow().getDecorView());
        assertNotNull("Local image preview should exist", preview);

        Button clear = findButton(dialog.getWindow().getDecorView(), "مسح المعاينة");
        assertNotNull("Preview reset action should exist", clear);
        clear.performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    @After
    public void tearDown() {
        if (dialog != null && dialog.isShowing()) dialog.dismiss();
        if (activity != null) activity.finish();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        ShadowDialog.reset();
        for (File image : temporaryImages) image.delete();
    }

    @Test
    public void selectingGalleryImage_displaysThatImageInLocalPreview() throws Exception {
        Uri selected = createSolidImage("selected-blue", Color.BLUE);

        deliverGalleryResult(selected);
        awaitPreviewColor(Color.BLUE);

        assertEquals("Preview pixels should come from the selected gallery image",
                Color.BLUE, previewBitmap().getPixel(16, 16));
    }

    @Test
    public void selectingImagesInSuccession_keepsTheLatestImageInPreview() throws Exception {
        Uri firstSelection = createSolidImage("first-red", Color.RED);
        Uri secondSelection = createSolidImage("second-green", Color.GREEN);

        // Deliver both results before draining the main looper. A late decode of the first image
        // must not replace the image selected second.
        deliverGalleryResult(firstSelection);
        deliverGalleryResult(secondSelection);
        awaitPreviewColor(Color.GREEN);

        // Allow any already-queued decode callbacks to run, then assert the preview is still current.
        Thread.sleep(80);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals("The second gallery choice must remain the visible preview",
                Color.GREEN, previewBitmap().getPixel(16, 16));
    }

    private void deliverGalleryResult(Uri uri) throws Exception {
        Intent result = new Intent().setData(uri);
        FeatureIntegration.onImageSelected(imagePickRequestCode(), RESULT_OK, result);
    }

    private int imagePickRequestCode() throws Exception {
        Field field = FeatureIntegration.class.getDeclaredField("IMAGE_PICK_REQUEST");
        field.setAccessible(true);
        return field.getInt(null);
    }

    private Uri createSolidImage(String prefix, int color) throws Exception {
        File file = File.createTempFile(prefix, ".png", activity.getCacheDir());
        temporaryImages.add(file);
        Bitmap bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
        try (FileOutputStream output = new FileOutputStream(file)) {
            assertTrue("Test image should be written", bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        } finally {
            bitmap.recycle();
        }
        return Uri.fromFile(file);
    }

    private void awaitPreviewColor(int expectedColor) throws InterruptedException {
        long deadline = System.nanoTime() + 3_000_000_000L;
        int lastColor = Color.TRANSPARENT;
        while (System.nanoTime() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            Bitmap bitmap = previewBitmapOrNull();
            if (bitmap != null) {
                lastColor = bitmap.getPixel(16, 16);
                if (lastColor == expectedColor) return;
            }
            Thread.sleep(10);
        }
        assertEquals("Preview did not settle on the selected image", expectedColor, lastColor);
    }

    private Bitmap previewBitmap() {
        Bitmap bitmap = previewBitmapOrNull();
        assertNotNull("Selected image should be rendered as a bitmap", bitmap);
        return bitmap;
    }

    private Bitmap previewBitmapOrNull() {
        if (preview.getDrawable() instanceof android.graphics.drawable.BitmapDrawable) {
            return ((android.graphics.drawable.BitmapDrawable) preview.getDrawable()).getBitmap();
        }
        return null;
    }

    private ImageView findPreview(View view) {
        if (view instanceof ImageView
                && PREVIEW_DESCRIPTION.equals(view.getContentDescription())) {
            return (ImageView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                ImageView found = findPreview(group.getChildAt(index));
                if (found != null) return found;
            }
        }
        return null;
    }

    private Button findButton(View view, String text) {
        if (view instanceof Button && text.contentEquals(((Button) view).getText())) {
            return (Button) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                Button found = findButton(group.getChildAt(index), text);
                if (found != null) return found;
            }
        }
        return null;
    }
}

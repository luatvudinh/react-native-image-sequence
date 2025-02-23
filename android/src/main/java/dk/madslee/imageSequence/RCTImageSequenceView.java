package dk.madslee.imageSequence;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.util.Log;
import android.os.AsyncTask;
import android.widget.ImageView;
import android.provider.MediaStore;
import android.net.Uri;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.uimanager.events.RCTEventEmitter;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.RejectedExecutionException;


public class RCTImageSequenceView extends ImageView {
    private Integer framesPerSecond = 24;
    private Integer loadedPos = 0;
    private Boolean loop = true;
    private Boolean addingQueue = false;
    private ArrayList<String> uris;
    private ArrayList<AsyncTask> activeTasks;
    private HashMap<Integer, Bitmap> bitmaps;
    private RCTResourceDrawableIdHelper resourceDrawableIdHelper;

    public RCTImageSequenceView(Context context) {
        super(context);

        resourceDrawableIdHelper = new RCTResourceDrawableIdHelper();
    }

    private class DownloadImageTask extends AsyncTask<String, Void, Bitmap> {
        private final Integer index;
        private final String uri;
        private final Context context;

        public DownloadImageTask(Integer index, String uri, Context context) {
            this.index = index;
            this.uri = uri;
            this.context = context;
        }

        @Override
        protected Bitmap doInBackground(String... params) {
            if (this.uri.startsWith("http")) {
                return this.loadBitmapByExternalURL(this.uri);
            }

            return this.loadBitmapByLocalResource(this.uri);
        }


        private Bitmap loadBitmapByLocalResource(String uri) {
            try {
//                 return BitmapFactory.decodeStream(context.getContentResolver().openInputStream(Uri.parse(uri)));
                return MediaStore.Images.Media.getBitmap(context.getContentResolver() , Uri.parse(uri));
            } catch (IOException e) {
                Log.e("react-native-image-sequence", "loadBitmapByLocalResource failed" + e.getMessage());
                return null;
            }
//            return BitmapFactory.decodeResource(this.context.getResources(), resourceDrawableIdHelper.getResourceDrawableId(this.context, uri));
        }

        private Bitmap loadBitmapByExternalURL(String uri) {
            Bitmap bitmap = null;

            try {
                InputStream in = new URL(uri).openStream();
                bitmap = BitmapFactory.decodeStream(in);
            } catch (IOException e) {
                e.printStackTrace();
            }

            return bitmap;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (!isCancelled()) {
                onTaskCompleted(this, index, bitmap);
            }
        }
    }

    private void onTaskCompleted(DownloadImageTask downloadImageTask, Integer index, Bitmap bitmap) {
        bitmaps.put(index, bitmap);
        activeTasks.remove(downloadImageTask);

        if (activeTasks.isEmpty()) {
            if (loadedPos >= uris.size() - 1) {
                setupAnimationDrawable();
            } else {
                loadMoreImages();
            }
        }
    }

    public void setImages(ArrayList<String> uris) {
        this.uris = uris;
        if (isLoading()) {
            // cancel ongoing tasks (if still loading previous images)
            for (int index = 0; index < activeTasks.size(); index++) {
                activeTasks.get(index).cancel(true);
            }
        }

        activeTasks = new ArrayList<>(uris.size());
        bitmaps = new HashMap<>(uris.size());
        loadedPos = 0;

        loadMoreImages();
    }

    private void loadMoreImages() {
        if (addingQueue || loadedPos >= uris.size() - 1) {
            return;
        }

        addingQueue = true;
        for (int index = loadedPos + 1; index < uris.size(); index++) {
            String uri = uris.get(index);
            DownloadImageTask task = new DownloadImageTask(index, uri, getContext());
            activeTasks.add(task);

            try {
                task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            } catch (RejectedExecutionException e){
                activeTasks.remove(task);
                Log.e("react-native-image-sequence", "DownloadImageTask failed" + e.getMessage());
                addingQueue = false;
                return;
            }
            loadedPos += 1;
        }
        addingQueue = false;
    }

    public void setFramesPerSecond(Integer framesPerSecond) {
        this.framesPerSecond = framesPerSecond;

        // updating frames per second, results in building a new AnimationDrawable (because we cant alter frame duration)
        if (isLoaded()) {
            setupAnimationDrawable();
        }
    }

    public void setLoop(Boolean loop) {
        this.loop = loop;

        // updating looping, results in building a new AnimationDrawable
        if (isLoaded()) {
            setupAnimationDrawable();
        }
    }

    private boolean isLoaded() {
        return !isLoading() && bitmaps != null && !bitmaps.isEmpty();
    }

    private boolean isLoading() {
        return activeTasks != null && !activeTasks.isEmpty();
    }

    private void setupAnimationDrawable() {
        AnimationDrawable animationDrawable = new AnimationDrawable();
        for (int index = 0; index < bitmaps.size(); index++) {
            BitmapDrawable drawable = new BitmapDrawable(this.getResources(), bitmaps.get(index));
            animationDrawable.addFrame(drawable, 1000 / framesPerSecond);
        }

        animationDrawable.setOneShot(!this.loop);

        this.setImageDrawable(animationDrawable);

        WritableMap event = Arguments.createMap();
        event.putBoolean("loadCompleted", true);
        ReactContext reactContext = (ReactContext) getContext();
        reactContext.getJSModule(RCTEventEmitter.class).receiveEvent(
                getId(),
                "onLoadImageCompleted",
                event);

        animationDrawable.start();

        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                WritableMap event = Arguments.createMap();
                event.putBoolean("animationFinished", true);
                ReactContext reactContext = (ReactContext) getContext();
                reactContext.getJSModule(RCTEventEmitter.class).receiveEvent(
                        getId(),
                        "onAnimationFinished",
                        event);
            }
        }, bitmaps.size() * 1000 / framesPerSecond);
    }
}

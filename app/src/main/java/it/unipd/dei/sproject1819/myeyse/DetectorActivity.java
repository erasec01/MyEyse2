package it.unipd.dei.sproject1819.myeyse;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.media.ImageReader;
import android.os.SystemClock;
import android.util.Size;

import java.io.IOException;
import java.net.ContentHandler;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import it.unipd.dei.sproject1819.myeyse.tracking.MultiBoxTracker;
import it.unipd.dei.sproject1819.myeyse.tracking.TrackedRecognition;

public class DetectorActivity extends CameraActivity implements ImageReader.OnImageAvailableListener
{
    //private static final Logger LOGGER = new Logger();

    private static final int TF_OD_API_INPUT_SIZE = 300;
    private static final boolean TF_OD_API_IS_QUANTIZED = true;
    private static final String TF_OD_API_MODEL_FILE = "detect.tflite";
    private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/labelmap.txt";

    // Minimum detection confidence to track a detection.
    private static final float MINIMUM_CONFIDENCE_OBJECT_RECOGNIZED = 0.7f;

    private TFLiteObjectDetectionAPIModel detector = null;

    private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);

    private byte[] luminanceCopy;

    //private long lastProcessingTimeMs;
    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;
    private Bitmap cropCopyBitmap = null;

    private boolean computingDetection = false;

    private long timestamp = 0;

    //Matrices used to perform the resize
    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;
    private Mode newMode = Mode.UNDEFINED;
    private MultiBoxTracker tracker;


    @Override
    protected void onPreviewSizeChosen(final Size size, final int rotation, final float focalLength)
    {
        try
        {
            detector = TFLiteObjectDetectionAPIModel.create(
                    getAssets(),
                    TF_OD_API_MODEL_FILE,
                    TF_OD_API_LABELS_FILE,
                    TF_OD_API_INPUT_SIZE,
                    TF_OD_API_IS_QUANTIZED);

        }
        catch (final IOException e)
        {
        }

        sensorOrientation = rotation - getScreenOrientation();

        //We create an empty bitmap and then, using the setPixels method, add a color to each pixel.
        //The vector whit the colors is give by method getRgbBytes() which convert the byte frame from
        // YU420SP to ARGB8888
        rgbFrameBitmap = Bitmap.createBitmap(mRealFrameWidth, mRealFrameHeight,
                Bitmap.Config.ARGB_8888);

        //We create an empty bitmap and then store the clipped frame
        croppedBitmap = Bitmap.createBitmap(TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE,
                Bitmap.Config.ARGB_8888);

        //We get the matrix needed to do the scaling from the frame to a 300x300 resolution
        frameToCropTransform =
                ImageUtils.getTransformationMatrix(
                        mRealFrameWidth, mRealFrameHeight,
                        TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE,
                        sensorOrientation, false);

        cropToFrameTransform = new Matrix();

        frameToCropTransform.invert(cropToFrameTransform);

        pm = new PanoramicMode(this, mRealFrameWidth, mRealFrameHeight, sensorOrientation, t1, focalLength);
    }

    @Override
    protected void processReturnTour()
    {
        Context context = this;
        if (computingDetection == true)
            readyForNextImage();

        if ((newMode != Mode.UNDEFINED) && (computingDetection == false))
        {
            mode = Mode.MODE_OBJECT_DETECTION;
            newMode = Mode.UNDEFINED;
            isModeChange = false;
            if (pm.isActive())
                pm.close(false);

            t1.interruptSpeech();
            t1.speechMessage("Object detection mode activated!");
            tracker = new MultiBoxTracker(context, mode);
            return;
        }

        computingDetection = true;
        /**
         * Mi interessa fare il confronto solo se l'utente è nell'intorno del punto iniziale
         */
        runInBackground(new Runnable()
        {
            @Override
            public void run()
            {
                getRgbBytes();
                readyForNextImage();
                if (pm.stopUser(rgbBytes))
                {
                    pm.close(false);
                    t1.speechMessage("Arrive to start position!");
                    setNewMode(Mode.MODE_OBJECT_DETECTION);
                }
                computingDetection = false;
            }
        });
    }

    /**
     * Ba
     */
    @Override
    protected void processImage()
    {
        final Context cont = this;

        ++timestamp;

        final long currTimestamp = timestamp;

        final byte[] originalLuminance = getLuminance();

        /**
         * Inizializza
         */
        if ((newMode != Mode.UNDEFINED) && (computingDetection == false))
        {
            if (newMode == Mode.MODE_OBJECT_DETECTION)
            {
                mode = Mode.MODE_OBJECT_DETECTION;
                if (pm.isActive())
                    pm.close(false);

                t1.speechMessage("Object detection mode activated!");
                tracker = new MultiBoxTracker(cont, mode);
            }
            else if (newMode == Mode.MODE_PANORAMIC)
            {
                mode = Mode.MODE_PANORAMIC;
                t1.speechMessage("Panoramic mode activated");
                tracker = new MultiBoxTracker(cont, mode, pm);
                newMode = Mode.UNDEFINED;
                isModeChange = false;
                readyForNextImage();
                return;
            }
            newMode = Mode.UNDEFINED;
            isModeChange = false;
        }
        else if (newMode != Mode.UNDEFINED)
        {
            readyForNextImage();
            return;
        }

        /**
         * We first update the tracking information regard tracking object(this used only current
         * frame.
         */
        if (!pm.isReturnMode())
        {
            tracker.onFrame(
                    mRealFrameWidth,
                    mRealFrameHeight,
                    getLuminanceStride(),
                    sensorOrientation,
                    originalLuminance,
                    timestamp);
        }
        /**
         *  If the ssd
         *  No mutex needed as this method is not reentrant.
         */
        if (computingDetection)
        {
            readyForNextImage();
            return;
        }

        //We use a so-called "worker thread" to perform frame analysis
        runInBackground(new Runnable()
        {
            @Override
            public void run()
            {
                computingDetection = true;
                //LOGGER.i("Preparing image " + currTimestamp + " for detection in bg thread.");

                int[] rgb = getRgbBytes();

                if (mode == Mode.MODE_PANORAMIC)
                {
                    /**
                     * If the mode is panoramic, we may have to use the rgb vector just
                     * calculate
                     */
                    if (Mode.MODE_PANORAMIC == mode)
                    {
                        /**
                         * In the case in which the user has arrived in a neighborhood of
                         * the initial position, we calculate the frame histogram.
                         * If current frame is considered similar at the
                         * starting frame, we can conclude that the panoramic mode is
                         * complete
                         *
                         * It is possible to note that, with this code organization,
                         * whatever the strategy adopted to estimate the similarity between
                         * frames, the code here is independent to it
                         *
                         * However, if the neighborhood of the initial position is exceeded
                         * without the recognition of a frame similar to the stop one,
                         * however we block the user.
                         *
                         */
                        if (pm.stopUser(rgbBytes))
                        {
                            mode = Mode.UNDEFINED;
                            endModePanoramic();
                            computingDetection = false;
                            readyForNextImage();
                            return;
                        }
                    }
                }

                rgbFrameBitmap.setPixels(rgb, 0, mRealFrameWidth, 0, 0, mRealFrameWidth, mRealFrameHeight);

                if (luminanceCopy == null)
                {
                    luminanceCopy = new byte[originalLuminance.length];
                }
                System.arraycopy(originalLuminance, 0, luminanceCopy, 0,
                        originalLuminance.length);

                readyForNextImage();

                Canvas canvas = new Canvas(croppedBitmap);
                canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);

                //
                List<InfoSpeech> s = procImage(currTimestamp);

                if ((s != null) && (s.size() > 0) && mode == Mode.MODE_OBJECT_DETECTION)
                {
                    for (int i = 0; i < s.size(); i++)
                        t1.speechMessage(s.get(i).getmMessToSpeech());
                }

                //Processing of the current frame is finished, sdd can inference a new frame
                computingDetection = false;
            }

        });
    }

    /**
     * @param currTimestamp Current time stamp
     * @return List
     */
    private List<InfoSpeech> procImage(long currTimestamp)
    {
        //LOGGER.i("Running detection on image " + currTimestamp);
        //final long startTime = SystemClock.uptimeMillis();
        final long startTime = SystemClock.uptimeMillis();
        /**
         * SSD model makes the inference on the current clipped frame and returns a list
         * of recognized objects
         */
        final List<Recognition> results = detector.recognizeImage(croppedBitmap);
        final long lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;

        // lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;
        cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);

        /**
         * Store only object with confidence a >= MINIMUM_CONFIDENCE_TF_OD_API
         * and localized
         */
        final List<Recognition> mappedRecognitions = new LinkedList<Recognition>();

        for (final Recognition result : results)
        {
            /**
             * Get reference of the four coordinate of the rectangle that surrounds
             * the (current) object recognized
             */
            final RectF location = result.getLocation();

            /**
             * Check if location is defined and if have confidence greater than
             * the minimum threshold
             */
            if (location != null && result.getConfidence() >= MINIMUM_CONFIDENCE_OBJECT_RECOGNIZED)
            {
                /**
                 * Position coordinates are relative to the clipped frame, so we must
                 * change them for the original frame
                 */
                cropToFrameTransform.mapRect(location);

                //Set coordinate modify
                result.setLocation(location);

                //Add object recognize to list
                mappedRecognitions.add(result);
            }
        }

        /**
         * Pass all the objects that we think are likely to be present in the photo, current frame
         * and the timestamp
         */
        List<InfoSpeech> s = tracker.trackResults(mappedRecognitions, luminanceCopy, currTimestamp);
        //computingDetection = false;
        return s;
    }

    /**
     * This method provides, in the form of audio messages, information on the position of objects
     * recognized by the app when the user return to the start position of tour
     */
    @Override
    protected void endModePanoramic()
    {
        String res = "";
        t1.setSpeechRate(PanoramicMode.VELOCITY_TO_SPEECH_RESULTS);

        List<String> result = pm.getResultsTour();

        for (int i = 0; i < result.size(); i++)
            res += result.get(i) + '\n';

        t1.speechMessage(res, "endModPreview");
        t1.resetSpeechRate();
    }

    @Override
    protected void inizializedObjectDetectionMode()
    {
        Context cont = this;
        t1.speechMessage("Object detection mode activated!");
        tracker = new MultiBoxTracker(cont, mode);

    }

    @Override
    protected void setNewMode(Mode newMode)
    {
        this.newMode = newMode;
        if (newMode != Mode.MODE_RETURN)
            isModeChange = true;
    }
}

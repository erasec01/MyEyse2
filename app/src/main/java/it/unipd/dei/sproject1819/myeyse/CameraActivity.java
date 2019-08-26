package it.unipd.dei.sproject1819.myeyse;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Trace;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.UtteranceProgressListener;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.WindowManager;
import android.widget.Toast;

import java.nio.ByteBuffer;
import java.util.List;

public abstract class CameraActivity extends AppCompatActivity
        implements ImageReader.OnImageAvailableListener, GestureDetector.OnDoubleTapListener,
        GestureDetector.OnGestureListener
{
    private static final String TAG = "CameraActivity";

    private static final int PERMISSIONS_REQUEST = 1;
    private static final String PERMISSION_CAMERA = Manifest.permission.CAMERA;
    private static final String PERMISSION_RECORD = Manifest.permission.RECORD_AUDIO;

    protected boolean isModeChange = false;

    public enum Mode
    {
        //Normal mode where the app identify objects that are in front of the user
        MODE_OBJECT_DETECTION,

        /**
         * Mode in which the user performs a 360° tour on itself and, ar the end of it,
         * the app indicates the location of all recognized objects.
         */
        MODE_PANORAMIC,

        MODE_RETURN,
        /**
         * Modalità in cui ci troviamo quando:
         * -The user is about to pronounce YES / NO if the application asks him if he wants to
         * return to the starting point
         * -The user switches from portrait to landscape mode
         * <p>
         * This mode is completely transparent to the end user.
         */
        UNDEFINED
    }

    private static final Size DESIRED_CAPTURE_SIZE = new Size(1920, 1080);

    /**
     * The communication of the app with the user can only take place by sending audio messages.
     * This is possible using this reference initialized in the method ..
     */
    protected TextToSpeechImp t1;

    /**
     * When this variable is true, the new frame NF isn't analyzed. It is discarded.
     * Unlike what you might think, this variable is a true only for the time between a frame NF is
     * get, converted from color space YUV to color space RGB and analyzed by the tracking algorithm.
     * Then, if ssd is analyzing a frame, NF is discarded and this variable set to true,
     * otherwise it is passed to the network.
     * <p>
     * The advantage of doing this is that however, even if it is not analyzed by the network,
     * the tracking algorithm obtains information from the current frame.
     */
    protected static boolean isProcessingFrame = false;

    protected Integer sensorOrientation;
    /**
     * The camera preview size will be chosen to be the smallest frame by pixel size capable of
     * containing a DESIRED_SIZE x DESIRED_SIZE square.
     */
    private static final int MINIMUM_CAPTURE_SIZE = 320;


    //
    private GestureDetectorCompat mDetector;

    //Reference to fragment. Inside it is define the resolution of the frame..
    private Camera2Fragment camera2Fragment;

    protected int mRealFrameWidth = 0;
    protected int mRealFrameHeight = 0;

    protected Handler handler;

    protected HandlerThread handlerThread;

    Handler mHandler;

    private byte[][] yuvBytes = new byte[3][];

    protected int[] rgbBytes = null;

    private int yRowStride;

    private Runnable postInferenceCallback;

    private Runnable imageConverter;

    PanoramicMode pm;

    private SpeechRecognizer mSpeechRecognizer;

    boolean mWaiting_for_user_responde = false;

    //Variable that define current mode.

    protected Mode mode = Mode.MODE_OBJECT_DETECTION;

    Intent recognizerIntent = null;


    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(null);
        setContentView(R.layout.activity_main);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        //We create a new SpeechRecognizer.
        mSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);

        //Sets the listener that will receive all the callbacks.
        mSpeechRecognizer.setRecognitionListener(new RecognitionListener()
        {
            @Override
            public void onReadyForSpeech(Bundle bundle)
            {
                Log.d(TAG, "Called when the endpointer is ready for the user to start speaking.");
            }

            @Override
            public void onBeginningOfSpeech()
            {
                Log.d(TAG, "The user has started to speak");
            }

            @Override
            public void onRmsChanged(float v)
            {
                Log.d(TAG, "The sound level in the audio stream has changed");
            }

            @Override
            public void onBufferReceived(byte[] bytes)
            {
                Log.d(TAG, "More sound has been received");
            }

            @Override
            public void onEndOfSpeech()
            {
                Log.d(TAG, "User stops speaking.");
            }

            @Override
            public void onError(int errorCode)
            {
                switch (errorCode)
                {
                    case SpeechRecognizer.ERROR_NO_MATCH:
                    case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                        if (mWaiting_for_user_responde)
                        {
                            setNewMode(Mode.MODE_OBJECT_DETECTION);
                            mWaiting_for_user_responde = false;
                        }
                        break;
                }
            }

            @Override
            public void onResults(Bundle bundle)
            {
                //Recognized words
                List<String> result =
                        bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

                for (int i = 0; i < result.size(); i++)
                {
                    if (result.get(i).contains("yes"))
                    {
                        t1.setSpeechRate(0.9f);
                        t1.speechMessage(pm.goStartingPoint());
                        t1.resetSpeechRate();
                        mode = Mode.MODE_PANORAMIC;
                        mWaiting_for_user_responde = false;
                        break;
                    }
                    else
                    {
                        setNewMode(Mode.MODE_OBJECT_DETECTION);
                        mSpeechRecognizer.cancel();
                        mWaiting_for_user_responde = false;
                        break;
                    }
                }
            }

            @Override
            public void onPartialResults(Bundle bundle)
            {

            }

            @Override
            public void onEvent(int i, Bundle bundle)
            {

            }
        });

        UtteranceProgressListener utteranceId = new UtteranceProgressListener()
        {
            @Override
            public void onStart(String utteranceId)
            {
            }

            @Override
            public void onDone(String utteranceId)
            {
                if (utteranceId.equals("GoVoiceSynthesizer") && (mWaiting_for_user_responde))
                {
                    recognizerIntent =
                            new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                    //Maximum number of results to return
                    recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,
                            1);
                    recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                    //Represents the current language preference
                    recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                            "en");

                    //Starts listening for speech.
                    mHandler.post(new Runnable()
                    {
                        public void run()
                        {
                            mSpeechRecognizer.startListening(recognizerIntent);
                        }
                    });
                }
                else if (utteranceId.equals("endModPreview"))
                {
                    pm.setIsSpeechResult();
                    setNewMode(Mode.MODE_OBJECT_DETECTION);
                    isModeChange = true;
                }
            }

            @Override
            public void onError(String utteranceId)
            {

            }
        };

        t1 = new TextToSpeechImp(this, utteranceId);

        // Instantiate the gesture detector with the
        // application context and an implementation of
        // GestureDetector.OnGestureListener
        mDetector = new GestureDetectorCompat(this, this);

        // Set the gesture detector as the double tap
        // listener.
        mDetector.setOnDoubleTapListener(this);

        if (hasPermission())
        {
            setFragment();
        }
        else
        {
            requestPermission();
        }
    }

    /**
     * Callback for Camera2 API
     */
    @Override
    public void onImageAvailable(final ImageReader reader)
    {
        //We need wait until we have some size of capture frame
        if (camera2Fragment.getRealCaptureSize().getWidth() == 0 ||
                camera2Fragment.getRealCaptureSize().getHeight() == 0)
        {
            return;
        }
        try
        {
            /**
             * We do some obvious initializations the first time a frame is processed.
             * Then we start object detection mode
             */
            if (rgbBytes == null)
            {
                rgbBytes = new int[mRealFrameWidth * mRealFrameHeight];
                inizializedObjectDetectionMode();
            }
            //We acquire the current frame
            final Image image = reader.acquireLatestImage();

            if (image == null)
                return;

            if (isProcessingFrame == false && isModeChange == true)
            {

            }
            else
            {
                if ((isProcessingFrame) || (mode == Mode.UNDEFINED) ||
                        (mWaiting_for_user_responde == true))
                {
                    image.close();
                    return;
                }
            }
            /**
             * If the mode is the panoramic, it is necessary to execute the
             * following checks before sending the frame to the model.
             */
            if ((mode == Mode.MODE_PANORAMIC) && (isModeChange == false))
            {
                if (pm.getMode() == PanoramicMode.DefineState.MODE_NORMAL)
                {
                    /**
                     * Activating the sensors doesn't mean having the position value immediately.
                     * If it isn't available we don't consider the frame.
                     * Alternatively, the sensors could have been registered permanently, and not
                     * only when the user goes into panoramic mode, but this would have caused an
                     * increase in battery consumption
                     */
                    if (pm.isSensorReady())
                    {
                        pm.storeCurrentPosition();
                    }
                    else
                    {
                        image.close();
                        return;
                    }

                    /**
                     * First we have to determine whether the rotation is to be considered clockwise or
                     * counterclockwise. In the case where the direction of rotation hasn't yet been
                     * established, then it doesn't continue with the processing of the frame.
                     */
                    if ((pm.isEstablishedOrientation() == false))
                    {
                        image.close();
                        return;
                    }

                    /**
                     * Orientation is established. Check that the user is not performing an incorrect
                     * maneuver. In that case, We block it and ask him if he wants to return to the
                     * starting point.
                     */
                    ManageRotation.StateRotation s = pm.isWrongMove();

                    if (s != ManageRotation.StateRotation.FOR_NOW_CORRECT_ROTATION)
                    {
                        mode = Mode.UNDEFINED;

                        t1.setSpeechRate(0.95f);
                        if (s == ManageRotation.StateRotation.SEND_WARNING_TO_USER)
                        {
                            mode = Mode.MODE_PANORAMIC;
                            t1.speechMessage(getString(R.string.panoramic_mode_speed_warning));
                        }
                        else
                        {
                            if (s == ManageRotation.StateRotation.MAXIMUM_ANGULAR_SPEED_EXCEEDED)
                                t1.speechMessage(getString(R.string.panoramic_mode_max_speed_exceeded));

                            else //Necessarily val == manageRotation.INCORRECT_ROTATION
                                t1.speechMessage(getString(R.string.panoramic_mode_inverse_rotation));

                            t1.speechMessage(getString(R.string.panoramic_mode_return_starting_point),
                                    "GoVoiceSynthesizer");
                            mWaiting_for_user_responde = true;
                        }
                        t1.resetSpeechRate();
                        image.close();
                        return;
                    }
                }
                else
                {
                    /**
                     * In this mode I simply have to check that the user does not turn in the wrong
                     * direction compared to the calculated one.
                     *
                     * It makes no sense to evaluate the speed since, when returning to the starting point,
                     * object tracking is not performed.
                     *
                     * However, if the user proceeds slowly in return, it is more likely to be arrested as a
                     * frame deemed similar to the starting one is detected. This, in general, guarantees a
                     * more precise stop
                     */

                    ManageRotation.StateRotation s = pm.isWrongMove();
                    if (s == ManageRotation.StateRotation.INCORRECT_ROTATION)
                    {
                        /**
                         * If the user does not make the turn in the recommended direction, we activate
                         * object detection mode
                         */
                        t1.speechMessage("Inverse rotation");

                        /**
                         * We disable the sensors and we bring the state of the object to the default
                         * value
                         */
                        //Go to object detection mode
                        setNewMode(Mode.MODE_OBJECT_DETECTION);
                    }
                    //In effetti non ho bisogno di elaborare il frame nel caso in cui
                    if (pm.isUserNearStartPosition() == false)
                    {
                        image.close();
                        return;
                    }
                }
            }

            //Later, other frames are discarded until this variable is true
            isProcessingFrame = true;

            Trace.beginSection("imageAvailable");

            final Image.Plane[] planes = image.getPlanes();
            fillBytes(planes, yuvBytes);
            yRowStride = planes[0].getRowStride();
            final int uvRowStride = planes[1].getRowStride();
            final int uvPixelStride = planes[1].getPixelStride();

            imageConverter =
                    new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            ImageUtils.convertYUV420ToARGB8888(
                                    yuvBytes[0],
                                    yuvBytes[1],
                                    yuvBytes[2],
                                    mRealFrameWidth,
                                    mRealFrameHeight,
                                    yRowStride,
                                    uvRowStride,
                                    uvPixelStride,
                                    rgbBytes);
                        }
                    };


            postInferenceCallback =
                    new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            image.close();
                            isProcessingFrame = false;
                        }
                    };

            if (pm.isReturnMode())
                processReturnTour();
            else
                processImage();
        }
        catch (final Exception e)
        {
            Log.d(TAG, "Exception");
            Trace.endSection();
            return;
        }
    }

    protected synchronized void runInBackground(final Runnable r)
    {
        if (handler != null)
        {
            handler.post(r);
        }
    }


    /**
     * @return
     */
    protected int[] getRgbBytes()
    {
        imageConverter.run();
        return rgbBytes;
    }

    protected int getLuminanceStride()
    {
        return yRowStride;
    }

    /**
     * @return
     */
    protected byte[] getLuminance()
    {
        return yuvBytes[0];
    }

    protected void setFragment()
    {
        camera2Fragment = Camera2Fragment.newInstance(this,
                new Camera2Fragment.ConnectionCallback()
                {
                    @Override
                    public void onPreviewSizeChosen(final Size size, final int rotation, final float focalLength)
                    {
                        mRealFrameHeight = size.getHeight();
                        mRealFrameWidth = size.getWidth();
                        CameraActivity.this.onPreviewSizeChosen(size, rotation, focalLength);
                    }
                });

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, camera2Fragment)
                .commit();
    }

    protected void fillBytes(final Image.Plane[] planes, final byte[][] yuvBytes)
    {
        // Because of the variable row stride it's not possible to know in
        // advance the actual necessary dimensions of the yuv planes.
        for (int i = 0; i < planes.length; ++i)
        {
            final ByteBuffer buffer = planes[i].getBuffer();
            if (yuvBytes[i] == null)
            {
                //LOGGER.d("Initializing buffer %d at size %d", i, buffer.capacity());
                yuvBytes[i] = new byte[buffer.capacity()];
            }
            buffer.get(yuvBytes[i]);
        }
    }

    protected void readyForNextImage()
    {
        if (postInferenceCallback != null)
        {
            postInferenceCallback.run();
        }
    }

    protected int getScreenOrientation()
    {
        switch (getWindowManager().getDefaultDisplay().getRotation())
        {
            case Surface.ROTATION_270:
                return 270;
            case Surface.ROTATION_180:
                return 180;
            case Surface.ROTATION_90:
                return 90;
            default:
                return 0;
        }
    }

    @Override
    public synchronized void onResume()
    {
        super.onResume();
        handlerThread = new HandlerThread("inference");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
        mHandler = new Handler();
    }

    @Override
    public synchronized void onPause()
    {
        super.onPause();
        if (mSpeechRecognizer != null)
            mSpeechRecognizer.destroy();

        if ((pm != null) && (pm.isActive()))
            pm.close(false);
    }

    @Override
    public synchronized void onStop()
    {
        Log.d(TAG, "onStop");
        super.onStop();
    }

    @Override
    public synchronized void onDestroy()
    {
        Log.d(TAG, "onDestroy");
        super.onDestroy();
    }


    @Override
    public boolean onTouchEvent(MotionEvent event)
    {
        if (this.mDetector.onTouchEvent(event))
        {
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent motionEvent)
    {
        return false;
    }

    @Override
    public boolean onDoubleTap(MotionEvent motionEvent)
    {
        if (mode == Mode.MODE_OBJECT_DETECTION)
        {
            /**
             * As per the project, we do not allow the user to switch to panoramic mode if the phone
             * is in landscape
             */
            if (camera2Fragment.isLandscape())
            {
                t1.speechMessage("Please, put the phone vertically and try again");
            }
            else
            {
                /**
                 * Eventual messages in the queue are not reproduced and eventual current message
                 * interrupted
                 */
                t1.interruptSpeech();

                /**
                 * This condition, at this point, it should always be true because in the end in the
                 * panoramic mode we de-register the sensors
                 */
                if (pm.isInactive())
                    pm.active();

                //Change the operating mode
                setNewMode(Mode.MODE_PANORAMIC);
            }
        }
        /**
         *
         */
        else if ((mode == Mode.MODE_PANORAMIC) || (mode == Mode.UNDEFINED))
        {
            if (pm.isIsSpeechResult())
            {
                pm.setIsSpeechResult();
                t1.interruptSpeech();
                setNewMode(Mode.MODE_OBJECT_DETECTION);
            }
            if (mWaiting_for_user_responde == true)
            {
                t1.interruptSpeech();
                mSpeechRecognizer.stopListening();
                mWaiting_for_user_responde = false;
                mSpeechRecognizer.cancel();
            }
            setNewMode(Mode.MODE_OBJECT_DETECTION);
            isModeChange = true;
        }
        else if (mode == Mode.MODE_RETURN)
        {
            setNewMode(Mode.MODE_OBJECT_DETECTION);
        }
        return true;
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent motionEvent)
    {
        return false;
    }

    @Override
    public boolean onDown(MotionEvent motionEvent)
    {
        return true;
    }

    @Override
    public void onShowPress(MotionEvent motionEvent)
    {
    }

    @Override
    public boolean onSingleTapUp(MotionEvent motionEvent)
    {
        return false;
    }

    @Override
    public boolean onScroll(MotionEvent motionEvent, MotionEvent motionEvent1, float v, float v1)
    {
        return false;
    }

    @Override
    public void onLongPress(MotionEvent motionEvent)
    {

    }

    @Override
    public boolean onFling(MotionEvent motionEvent, MotionEvent motionEvent1, float v, float v1)
    {
        return false;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);
        /**
         * In any case, if the user goes from portrait to landscape or vice versa, the operating
         * mode is the object detection
         */
        mode = Mode.UNDEFINED;
        setFragment();
        setNewMode(Mode.MODE_OBJECT_DETECTION);
    }

    private boolean hasPermission()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        {
            return checkSelfPermission(PERMISSION_CAMERA) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(PERMISSION_RECORD) == PackageManager.PERMISSION_GRANTED;
        }
        else
        {
            return true;
        }
    }

    @Override
    public void onRequestPermissionsResult(
            final int requestCode, final String[] permissions, final int[] grantResults)
    {
        if (requestCode == PERMISSIONS_REQUEST)
        {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED
                    && grantResults[1] == PackageManager.PERMISSION_GRANTED)
            {
                setFragment();
            }
            else
            {
                requestPermission();
            }
        }
    }

    private void requestPermission()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        {
            if (shouldShowRequestPermissionRationale(PERMISSION_CAMERA) ||
                    shouldShowRequestPermissionRationale(PERMISSION_RECORD))
            {
                Toast.makeText(CameraActivity.this,
                        "Camera AND storage permission are required for this demo", Toast.LENGTH_LONG).show();
            }
            requestPermissions(new String[]{PERMISSION_CAMERA, PERMISSION_RECORD}, PERMISSIONS_REQUEST);
        }
    }

    protected abstract void onPreviewSizeChosen(final Size size, final int rotation,
                                                final float focalLength);

    protected abstract void processImage();

    protected abstract void endModePanoramic();

    protected abstract void processReturnTour();

    protected abstract void setNewMode(Mode newMode);

    protected abstract void inizializedObjectDetectionMode();
}
package it.unipd.dei.sproject1819.myeyse;

import android.content.Context;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import it.unipd.dei.sproject1819.myeyse.tracking.TrackedRecognition;

public class PanoramicMode
{
    private Context mContext;

    boolean mIsSpeechResult = false;

    private float mFocalLength;

    //Solo per debug
    TextToSpeechImp t1;

    public final static float VELOCITY_TO_SPEECH_RESULTS = 0.9f;

    public enum DefineState
    {
        MODE_NORMAL,
        /**
         * Mode in which the user wants to return to the starting point
         * This mode is completely transparent to the end user
         */
        MODE_RETURN_STARTING_POINT,
        MODE_NO_ACTIVE
    }

    //On the i-th iteration, it contains all the objects identified by the iteration [0, i-1]
    private HashMap<Integer, TrackedRecognition> mMotionVector;

    private DefineState mode;

    /**
     * Reference to a class, designed entirely by us, to support the panoramic mode. In particular,
     * inside it the sensors are registered and the calculation of position to be attributed to a
     * generic frame executed.
     * <p>
     * It is important to note that the position returned is already subtracted from the starting
     * position of the lap.
     */
    private ManageRotation mManageRotation;

    //Statistical information on the number of frames analyzed during the tour in panoramic mode
    private int mNumberAnalyzedFrames;

    private float mCurrentPosition;

    private ImageSimilarity imageSimilarity;

    private boolean mIsUserNearStartPosition = false;

    //Constructor
    public PanoramicMode(final Context context, final int realFrameWidth, final int realFrameHeight,
                         final int orientation, TextToSpeechImp t1, final float focalLength)
    {
        this.t1 = t1;
        mContext = context;
        mFocalLength = focalLength;
        mMotionVector = new HashMap<>();
        mManageRotation = new ManageRotation(context);
        imageSimilarity = new ImageSimilarity(realFrameWidth, realFrameHeight, orientation);
        mode = DefineState.MODE_NO_ACTIVE;
    }

    /**
     * @return
     */
    public boolean isUserNearStartPosition()
    {
        return mIsUserNearStartPosition;
    }

    /**
     *
     */
    public void active()
    {
        mode = DefineState.MODE_NORMAL;
        mManageRotation.active();
    }

    /**
     * Method getter relative of motion vector containing object before tracked and then lost
     * (since they are no longer present on the screen) during the tour
     *
     * @return
     */
    public HashMap<Integer, TrackedRecognition> getMotionVector()
    {
        return mMotionVector;
    }

    /**
     * @return
     */
    public DefineState getMode()
    {
        return mode;
    }

    public boolean isInactive()
    {
        return mManageRotation.isInactive();
    }

    public boolean isActive()
    {
        return !isInactive();
    }

    public boolean isReturnMode()
    {
        return (mode == DefineState.MODE_RETURN_STARTING_POINT);
    }

    public ManageRotation.StateRotation isWrongMove()
    {
        ManageRotation.StateRotation s = null;

        if (isReturnMode())
            s = mManageRotation.isUserMakeWrongMove(true);
        else
            s = mManageRotation.isUserMakeWrongMove(false);

        if (s == ManageRotation.StateRotation.FOR_NOW_CORRECT_ROTATION)
        {
            /**
             * In this case I have to create and memorize the histogram of
             * the current frame to then quantify the similarity with the
             * frames coming at the end of the round
             */
            if ((mIsUserNearStartPosition == false) &&
                    mManageRotation.UserInsideNearInitialPosition())
            {
                t1.speechMessage("Inside neighborhood");
                mIsUserNearStartPosition = true;
            }
        }
        return s;
    }

    public boolean isSensorReady()
    {
        return mManageRotation.isSensorReady();
    }

    /**
     * @param rgbBytes
     * @return
     */
    public boolean stopUser(int[] rgbBytes)
    {
        if (mode == DefineState.MODE_NORMAL)
            ++mNumberAnalyzedFrames;
        /**
         * In this case I have to create and memorize the histogram of
         * the current frame to then quantify the similarity with the
         * frames coming at the end of the round
         */
        if ((mNumberAnalyzedFrames == 1) && (mode == DefineState.MODE_NORMAL))
        {
            imageSimilarity.setFrameReference(rgbBytes);
            return false;
        }

        if ((mNumberAnalyzedFrames > 1) &&
                (imageSimilarity.HaveRealHistogramReference() == false))
        {
            imageSimilarity.IsDissimilarWhitReferenceFrame(rgbBytes);
            return false;
        }

        if (mIsUserNearStartPosition == false)
            return false;
        else
        {
            if (imageSimilarity.IsSimilarWhitReferenceFrame(rgbBytes) ||
                    (mManageRotation.StopUser()))
                return true;
            else
                return false;
        }
    }

    public String goStartingPoint()
    {
        mode = DefineState.MODE_RETURN_STARTING_POINT;
        mIsUserNearStartPosition = false;
        InfoBack infoBack = mManageRotation.approximatelyDistanceInDegrees();
        return mContext.getString(R.string.turn) + infoBack.getDegrees()
                + infoBack.getOrientation();
    }

    public float getCurrentPositionFrame()
    {
        return mCurrentPosition;
    }

    public void storeCurrentPosition()
    {
        mCurrentPosition = mManageRotation.getAzimuthValue();
    }

    public boolean isEstablishedOrientation()
    {
        return mManageRotation.isEstablishedOrientation();
    }


    /**
     * Method that produce, in the form of a string, the sequence of audio messages, one for each
     * recognized object, which will be produced by the speaker
     *
     * @return List of strings containing the messages to be communicated to the user
     */
    public List<String> getResultsTour()
    {
        //List of string return by method
        List<String> results = new ArrayList<>();

        HashMap<Integer, ArrayList<ObjectRecognized>> orderResult = new HashMap<>();

        //Depending on the number of objects identified, message is different
        if (mMotionVector.size() == 0)
        {
            results.add(mContext.getString(R.string.panoramic_mode_obj_not_found));
            close(true);
            return results;
        }
        else
        {
            if (mMotionVector.size() > 1)
                results.add(mContext.getString(R.string.panoramic_mode_found_more_obj) +
                        mMotionVector.size() + "object");
            else
                results.add(mContext.getString(R.string.panoramic_mode_found_1_obj));
        }

        /**
         * Struct used to order clockwise recognized objects and count objects that appear two or
         * more times at the same hour
         */

        /**
         * Each identified object is assigned a unique id that starts from 1. So the n-th object
         * tracked have id n.
         */
        for (int i = 1; i <= mMotionVector.size(); i++)
        {
            //This control should be always true..
            if (mMotionVector.containsKey(i))
            {
                /**
                 * Since we stop for a while after the actual start of the tour so that all the
                 * objects have roughly the same number of consecutive frames in which they appear,
                 * at all positions of each object we calculate the current stopping position.
                 */
                float realPosition = objectPositionProcessing(i);

                int dir = mManageRotation.mapPositionToDirection(realPosition);
                //Ottengo la lista di tutti gli oggetti che si trovano in direzzione dir
                ArrayList<ObjectRecognized> objectDir = orderResult.get(dir);
                //Sicuramente se la lista è a null significa che precedentemente non si
                //sono rilevati oggetti in quella direzzione
                if (objectDir == null)
                {
                    objectDir = new ArrayList<>();
                    objectDir.add(new ObjectRecognized(mMotionVector.get(i).getTitle()));
                    orderResult.put(dir, objectDir);
                }
                else
                {
                    //Se diversa da null, verifico se all'interno degli oggetti identificati in
                    //quella direzzione, è presente per caso uno con lo stesso nome di quello
                    //attuale
                    boolean found = false;

                    for (int j = 0; j < objectDir.size(); j++)
                    {
                        if (objectDir.get(j).name == mMotionVector.get(i).getTitle())
                        {
                            found = true;
                            objectDir.get(j).num++;
                            break;
                        }
                    }
                    if (found == false)
                        objectDir.add(new ObjectRecognized(mMotionVector.get(i).getTitle()));
                }
            }
        }

        //Ciclo per tutte le ore in cerca degli oggetti
        for (int i = 1; i <= 12; i++)
        {
            //We check if at least one object is present in the i-th direction
            if (orderResult.containsKey(i))
            {
                //For each object identified at the i-th we now create a string
                List<ObjectRecognized> or = orderResult.get(i);
                for (int k = 0; k < or.size(); k++)
                {
                    results.add("I found " + or.get(k).num + " " + or.get(k).name + " " +
                            mManageRotation.mapDirectionToString(i));
                }
            }
        }
        close(true);
        return results;
    }

    /**
     * Method that calculates, for each object, the position of each object taking into account the
     * actual stopping position, the position in which this object was recognized the first time and
     * the position, inside frame, occupied.
     */
    private float objectPositionProcessing(int i)
    {
        float offset = calculateOffset(mMotionVector.get(i).getLocation());
        return ManageRotation.increment(mMotionVector.get(i).getFirstPosition(),
                offset + mCurrentPosition);
    }

    //Questo rect suppongono sia proporzionato per 320x320
    private float calculateOffset(RectF rec)
    {
        //float curx = rec.centerX();
        //1080:FOV = (1080/2-curx) :x
        return 0;
    }

    public boolean isIsSpeechResult()
    {
        return mIsSpeechResult;
    }
    public void setIsSpeechResult()
    {
        mIsSpeechResult = false;
    }

    public void close(boolean isSpeechResult)
    {
        if (isSpeechResult == true)
            mIsSpeechResult = true;

        List<Float> c = ManageRotation.accValue;
        int total = mNumberAnalyzedFrames;
        List<Float> l = mManageRotation.f;
        float f = mManageRotation.storeCriticVal;

        //Basically, deregister the sensor
        if (mManageRotation.isActive())
            mManageRotation.close();

        //Set state variable to default value
        mIsUserNearStartPosition = false;
        imageSimilarity.close();
        mNumberAnalyzedFrames = 0;
        mode = DefineState.MODE_NO_ACTIVE;
        mMotionVector.clear();
        l.clear();
        c.clear();
    }

    class ObjectRecognized
    {
        int num;
        String name;

        private ObjectRecognized(String name)
        {
            this.name = name;
            num = 1;
        }
    }

    public void resetDebug()
    {
        mManageRotation.f.clear();
        ManageRotation.accValue.clear();
    }
}

package com.otaliastudios.transcoder.transcode.internal;

import androidx.annotation.NonNull;

import com.otaliastudios.transcoder.internal.Logger;

/**
 * Drops input frames to respect the output frame rate.
 */
public abstract class VideoFrameDropper {

    private final static String TAG = VideoFrameDropper.class.getSimpleName();
    private final static Logger LOG = new Logger(TAG);

    private VideoFrameDropper() {}

    public abstract boolean shouldRenderFrame(long presentationTimeUs);

    @NonNull
    public static VideoFrameDropper newDropper(int inputFrameRate, int outputFrameRate) {
        return new Dropper1(inputFrameRate, outputFrameRate);
    }

    /**
     * A simple and more elegant dropper.
     * Reference: https://stackoverflow.com/questions/4223766/dropping-video-frames
     */
    private static class Dropper1 extends VideoFrameDropper {

        private final double mInFrameRateReciprocal;
        private final double mOutFrameRateReciprocal;
        private double mFrameRateReciprocalSum;
        private int mFrameCount;

        private Dropper1(int inputFrameRate, int outputFrameRate) {
            mInFrameRateReciprocal = 1.0d / inputFrameRate;
            mOutFrameRateReciprocal = 1.0d / outputFrameRate;
            LOG.i("inFrameRateReciprocal:" + mInFrameRateReciprocal + " outFrameRateReciprocal:" + mOutFrameRateReciprocal);
        }

        @Override
        public boolean shouldRenderFrame(long presentationTimeUs) {
            mFrameRateReciprocalSum += mInFrameRateReciprocal;
            if (mFrameCount++ == 0) {
                LOG.v("RENDERING (first frame) - frameRateReciprocalSum:" + mFrameRateReciprocalSum);
                return true;
            } else if (mFrameRateReciprocalSum > mOutFrameRateReciprocal) {
                mFrameRateReciprocalSum -= mOutFrameRateReciprocal;
                LOG.v("RENDERING - frameRateReciprocalSum:" + mFrameRateReciprocalSum);
                return true;
            } else {
                LOG.v("DROPPING - frameRateReciprocalSum:" + mFrameRateReciprocalSum);
                return false;
            }
        }
    }

    /**
     * The old dropper, keeping here just in case.
     * Will test {@link Dropper1} and remove this soon.
     */
    @SuppressWarnings("unused")
    private static class Dropper2 extends VideoFrameDropper {

        // A step is defined as the microseconds between two frame.
        // The average step is basically 1 / frame rate.
        private float mAvgStep = 0;
        private float mTargetAvgStep;
        private int mRenderedSteps = -1; // frames - 1
        private long mLastRenderedUs;
        private long mLastStep;

        private Dropper2(int outputFrameRate) {
            mTargetAvgStep = (1F / outputFrameRate) * 1000 * 1000;

        }

        /**
         * TODO improve this. as it is now, rendering a frame after dropping many,
         * will not decrease avgStep but rather increase it (for this single frame; then it starts decreasing).
         * This has the effect that, when a frame is rendered, the following frame is always rendered,
         * because the conditions are worse then before. After this second frame things go back to normal,
         * but this is terrible logic.
         */
        @Override
        public boolean shouldRenderFrame(long presentationTimeUs) {
            if (mRenderedSteps > 0 && mAvgStep < mTargetAvgStep) {
                // We are rendering too much. Drop this frame.
                // Always render first 2 frames, we need them to compute the avg.
                LOG.v("DROPPING - avg:" + mAvgStep + " target:" + mTargetAvgStep);
                long newLastStep = presentationTimeUs - mLastRenderedUs;
                float allSteps = (mAvgStep * mRenderedSteps) - mLastStep + newLastStep;
                mAvgStep = allSteps / mRenderedSteps; // we didn't add a step, just increased the last
                mLastStep = newLastStep;
                return false;
            } else {
                // Render this frame, since our average step is too long or exact.
                LOG.v("RENDERING - avg:" + mAvgStep + " target:" + mTargetAvgStep + " newStepCount:" + (mRenderedSteps + 1));
                if (mRenderedSteps >= 0) {
                    // Update the average value, since now we have mLastRenderedUs.
                    long step = presentationTimeUs - mLastRenderedUs;
                    float allSteps = (mAvgStep * mRenderedSteps) + step;
                    mAvgStep = allSteps / (mRenderedSteps + 1); // we added a step, so +1
                    mLastStep = step;
                }
                // Increment both
                mRenderedSteps++;
                mLastRenderedUs = presentationTimeUs;
                return true;
            }
        }
    }



}

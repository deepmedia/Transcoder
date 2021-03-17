package com.otaliastudios.transcoder.internal.video;


import android.graphics.SurfaceTexture;
import android.opengl.Matrix;
import android.view.Surface;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;

import com.otaliastudios.opengl.draw.GlRect;
import com.otaliastudios.opengl.program.GlTextureProgram;
import com.otaliastudios.opengl.texture.GlTexture;
import com.otaliastudios.transcoder.internal.utils.Logger;

/**
 * The purpose of this class is to create a {@link Surface} associated to a certain GL texture.
 *
 * The Surface is exposed through {@link #getSurface()} and we expect someone to draw there.
 * Typically this will be a {@link android.media.MediaCodec} instance, using this surface as output.
 *
 * When {@link #drawFrame()} is called, this class will wait for a new frame from MediaCodec,
 * and draw it on the current EGL surface. The class itself does no GL initialization, and will
 * draw on whatever surface is current.
 *
 * NOTE: By default, the Surface will be using a BufferQueue in asynchronous mode, so we
 * can potentially drop frames.
 */
class FrameDrawer {
    private static final Logger LOG = new Logger("FrameDrawer");

    private static final long NEW_IMAGE_TIMEOUT_MILLIS = 10000;

    private SurfaceTexture mSurfaceTexture;
    private Surface mSurface;

    private GlTextureProgram mProgram;
    private GlRect mDrawable;

    private float mScaleX = 1F;
    private float mScaleY = 1F;
    private int mRotation = 0;

    @GuardedBy("mFrameAvailableLock")
    private boolean mFrameAvailable;
    private final Object mFrameAvailableLock = new Object();

    /**
     * Creates an VideoDecoderOutput using the current EGL context (rather than establishing a
     * new one). Creates a Surface that can be passed to MediaCodec.configure().
     */
    public FrameDrawer() {
        GlTexture texture = new GlTexture();
        mProgram = new GlTextureProgram();
        mProgram.setTexture(texture);
        mDrawable = new GlRect();

        // Even if we don't access the SurfaceTexture after the constructor returns, we
        // still need to keep a reference to it.  The Surface doesn't retain a reference
        // at the Java level, so if we don't either then the object can get GCed, which
        // causes the native finalizer to run.
        mSurfaceTexture = new SurfaceTexture(texture.getId());
        mSurfaceTexture.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
            @Override
            public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                LOG.v("New frame available");
                synchronized (mFrameAvailableLock) {
                    if (mFrameAvailable) {
                        throw new RuntimeException("mFrameAvailable already set, frame could be dropped");
                    }
                    mFrameAvailable = true;
                    mFrameAvailableLock.notifyAll();
                }
            }
        });
        mSurface = new Surface(mSurfaceTexture);
    }

    /**
     * Sets the frame scale along the two axes.
     * @param scaleX x scale
     * @param scaleY y scale
     */
    public void setScale(float scaleX, float scaleY) {
        mScaleX = scaleX;
        mScaleY = scaleY;
    }

    /**
     * Sets the desired frame rotation with respect
     * to its natural orientation.
     * @param rotation rotation
     */
    public void setRotation(int rotation) {
        mRotation = rotation;
    }

    /**
     * Returns a Surface to draw onto.
     * @return the output surface
     */
    @NonNull
    public Surface getSurface() {
        return mSurface;
    }

    /**
     * Discard all resources held by this class, notably the EGL context.
     */
    public void release() {
        mProgram.release();
        mSurface.release();
        // this causes a bunch of warnings that appear harmless but might confuse someone:
        // W BufferQueue: [unnamed-3997-2] cancelBuffer: BufferQueue has been abandoned!
        // mSurfaceTexture.release();
        mSurface = null;
        mSurfaceTexture = null;
        mDrawable = null;
        mProgram = null;
    }

    /**
     * Waits for a new frame drawn into our surface (see {@link #getSurface()}),
     * then draws it using OpenGL.
     */
    public void drawFrame() {
        awaitNewFrame();
        drawNewFrame();
    }

    /**
     * Latches the next buffer into the texture.  Must be called from the thread that created
     * the VideoDecoderOutput object, after the onFrameAvailable callback has signaled that new
     * data is available.
     */
    private void awaitNewFrame() {
        synchronized (mFrameAvailableLock) {
            while (!mFrameAvailable) {
                try {
                    // Wait for onFrameAvailable() to signal us. Use a timeout to avoid
                    // stalling the test if it doesn't arrive.
                    mFrameAvailableLock.wait(NEW_IMAGE_TIMEOUT_MILLIS);
                    if (!mFrameAvailable) {
                        // TODO: if "spurious wakeup", continue while loop
                        // TODO: what does this mean? ^
                        throw new RuntimeException("Surface frame wait timed out");
                    }
                } catch (InterruptedException ie) {
                    throw new RuntimeException(ie);
                }
            }
            mFrameAvailable = false;
        }
        // Latch the data.
        mSurfaceTexture.updateTexImage();
    }

    /**
     * Draws the data from SurfaceTexture onto the current EGL surface.
     */
    private void drawNewFrame() {
        mSurfaceTexture.getTransformMatrix(mProgram.getTextureTransform());
        // Invert the scale.
        float glScaleX = 1F / mScaleX;
        float glScaleY = 1F / mScaleY;
        // Compensate before scaling.
        float glTranslX = (1F - glScaleX) / 2F;
        float glTranslY = (1F - glScaleY) / 2F;
        Matrix.translateM(mProgram.getTextureTransform(), 0, glTranslX, glTranslY, 0);
        // Scale.
        Matrix.scaleM(mProgram.getTextureTransform(), 0, glScaleX, glScaleY, 1);
        // Apply rotation.
        Matrix.translateM(mProgram.getTextureTransform(), 0, 0.5F, 0.5F, 0);
        Matrix.rotateM(mProgram.getTextureTransform(), 0, mRotation, 0, 0, 1);
        Matrix.translateM(mProgram.getTextureTransform(), 0, -0.5F, -0.5F, 0);
        // Draw.
        mProgram.draw(mDrawable);
    }
}

package org.sil.storyproducer.media.pipe;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;

import org.sil.storyproducer.media.MediaHelper;

import java.nio.ByteBuffer;

/**
 * <p>This abstract media pipeline component provides a base for components which encode or decode
 * media streams. This class primarily encapsulates a {@link MediaCodec}.</p>
 * <p>Note: This class is spawns a child thread which keeps churning input while other calling code
 * pulls output.</p>
 */
public abstract class PipedMediaCodec implements PipedMediaByteBufferSource {
    private static final String TAG = "PipedMediaCodec";

    Thread mThread;

    protected volatile PipedMediaSource.State mComponentState = State.UNINITIALIZED;

    @Deprecated
    protected abstract String getComponentName();

    protected MediaCodec mCodec;
    protected ByteBuffer[] mInputBuffers;
    private ByteBuffer[] mOutputBuffers;
    private MediaFormat mOutputFormat = null;

    private boolean mIsDone = false;
    private long mPresentationTimeUsLast = 0;

    private MediaCodec.BufferInfo mInfo = new MediaCodec.BufferInfo();

    @Override
    public MediaFormat getOutputFormat() {
        if(mOutputFormat == null) {
            spinOutput(mInfo, true);
            if(mOutputFormat == null) {
                throw new RuntimeException("format was not retrieved from loop");
            }
        }
        return mOutputFormat;
    }

    @Override
    public boolean isDone() {
        return mIsDone;
    }

    @Override
    public void fillBuffer(ByteBuffer buffer, MediaCodec.BufferInfo info) {
        ByteBuffer outputBuffer = spinOutput(info, false);
        buffer.clear();
        buffer.put(outputBuffer);
        releaseBuffer(outputBuffer);
    }

    @Override
    public ByteBuffer getBuffer(MediaCodec.BufferInfo info) {
        return spinOutput(info, false);
    }

    @Override
    public void releaseBuffer(ByteBuffer buffer) throws InvalidBufferException {
        for(int i = 0; i < mOutputBuffers.length; i++) {
            if(mOutputBuffers[i] == buffer) {
                mCodec.releaseOutputBuffer(i, false);
                return;
            }
        }
        throw new InvalidBufferException("I don't own that buffer!");
    }

    protected void start() {
        mCodec.start();
        mInputBuffers = mCodec.getInputBuffers();
        mOutputBuffers = mCodec.getOutputBuffers();

        mThread = new Thread(new Runnable() {
            @Override
            public void run() {
                spinInput();
            }
        });
        mComponentState = State.RUNNING;
        mThread.start();
    }

    private ByteBuffer spinOutput(MediaCodec.BufferInfo info, boolean stopWithFormat) {
        if(mIsDone) {
            throw new RuntimeException("spinOutput called after depleted");
        }

        while (!mIsDone) {
            int pollCode = mCodec.dequeueOutputBuffer(
                        info, MediaHelper.TIMEOUT_USEC);
            if (pollCode == MediaCodec.INFO_TRY_AGAIN_LATER) {
//                if (MediaHelper.VERBOSE) Log.d(TAG, getComponentName() + ": no output buffer");
                //Do nothing.
            }
            else if (pollCode == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                if (MediaHelper.VERBOSE) Log.d(TAG, getComponentName() + ": output buffers changed");
                mOutputBuffers = mCodec.getOutputBuffers();
            }
            else if (pollCode == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (MediaHelper.VERBOSE) Log.d(TAG, getComponentName() + ": output format changed");
                if (mOutputFormat != null) {
                    throw new RuntimeException("changed output format again?");
                }
                mOutputFormat = mCodec.getOutputFormat();
                if(stopWithFormat) {
                    return null;
                }
            }
            else if((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0){
                if (MediaHelper.VERBOSE) Log.d(TAG, getComponentName() + ": codec config buffer");
                //TODO: make sure this is ok
                // Simply ignore codec config buffers.
                mCodec.releaseOutputBuffer(pollCode, false);
            }
            else {
                if (MediaHelper.VERBOSE) {
                    Log.d(TAG, getComponentName() + ": returned output buffer: " + pollCode + " of size " + info.size + " for time " + info.presentationTimeUs);
                }

                ByteBuffer buffer = mOutputBuffers[pollCode];

                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        != 0) {
                    if (MediaHelper.VERBOSE) Log.d(TAG, getComponentName() + ": EOS");
                    mIsDone = true;
                }
                else {
                    correctTime(info);
                    buffer.position(info.offset);
                    buffer.limit(info.offset + info.size);
                }
                return buffer;
            }
        }

        return null;
    }

    protected void correctTime(MediaCodec.BufferInfo info) {
        if (mPresentationTimeUsLast > info.presentationTimeUs) {
            throw new RuntimeException("buffer presentation time out of order!");
        }
        mPresentationTimeUsLast = info.presentationTimeUs;
    }

    /**
     * <p>Gather input from source, feeding it into mCodec, until source is depleted.</p>
     * <p>Note: This method <b>must return after mComponentState becomes CLOSED</b>.</p>
     */
    protected abstract void spinInput();

    @Override
    public void close() {
        //Shutdown child thread
        mComponentState = State.CLOSED;
        if(mThread != null) {
            try {
                mThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if(mCodec != null) {
            try {
                mCodec.stop();
            }
            catch(IllegalStateException e) {
                if(MediaHelper.VERBOSE) {
                    e.printStackTrace();
                }
            }
            finally {
                mCodec.release();
            }
        }
    }
}

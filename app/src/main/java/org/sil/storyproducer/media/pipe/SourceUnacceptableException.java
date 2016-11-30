package org.sil.storyproducer.media.pipe;

/**
 * This class provides an exception for media components to throw when they receive a source
 * which is incompatible with their requirements. A common use case of this exception is
 * {@link PipedMediaByteBufferDest#addSource(PipedMediaByteBufferSource)}.
 */
public class SourceUnacceptableException extends Exception {
    public SourceUnacceptableException(String msg) {
        super(msg);
    }
}

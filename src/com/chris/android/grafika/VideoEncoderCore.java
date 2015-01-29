/*
 * Copyright 2014 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chris.android.grafika;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.util.Log;
import android.view.Surface;



/**
 * This class wraps up the core components used for surface-input video encoding.
 * <p>
 * Once created, frames are fed to the input surface.  Remember to provide the presentation
 * time stamp, and always call drainEncoder() before swapBuffers() to ensure that the
 * producer side doesn't get backed up.
 * <p>
 * This class is not thread-safe, with one exception: it is valid to use the input surface
 * on one thread, and drain the output on a different thread.
 */
public class VideoEncoderCore {
    private static final String TAG = MainActivity.TAG;
    private static final boolean VERBOSE = false;

    // TODO: these ought to be configurable as well
    private static final String VIDEO_MIME_TYPE = "video/avc";    // H.264 Advanced Video Coding
    private static final String AUDIO_MIME_TYPE = "audio/mp4a-latm";    // H.264 Advanced Video Coding
    private static final int FRAME_RATE = 30;               // 30fps
    private static final int IFRAME_INTERVAL = 5;           // 5 seconds between I-frames

    private Surface mInputSurface;
    private MediaMuxer mMuxer;
    private MediaCodec mVideoEncoder;
    private MediaCodec mAudioEncoder;
    private MediaCodec.BufferInfo mVideoBufferInfo;
    private MediaCodec.BufferInfo mAudioBufferInfo;
    private int mVideoTrackIndex;
    private int mAudioTrackIndex;
    private boolean mMuxerStarted;
    private MediaFormat mVideoFormat;
    
    private MediaFormat mAudioFormat;
    
    // recording state
//    private int leadingChunk = 1;
    long startWhen;
    int frameCount = 0;
    boolean eosSentToAudioEncoder = false;
    boolean audioEosRequested = false;
    boolean eosSentToVideoEncoder = false;
    boolean fullStopReceived = false;
    boolean fullStopPerformed = false;
    
    final int TOTAL_NUM_TRACKS = 2;
    boolean started = false;
    int chunk;
    AtomicInteger numTracksAdded = new AtomicInteger(0);
    AtomicInteger numTracksFinished = new AtomicInteger(0);
    
    Object sync = new Object();
    
    // Audio
    public static final int SAMPLE_RATE = 44100;
    public static final int SAMPLES_PER_FRAME = 1024; // AAC
    public static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    public static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private AudioRecord audioRecord;
    private long lastEncodedAudioTimeStamp = 0;
    
    volatile boolean firstFrameReady = false;
    boolean eosReceived = false;


    /**
     * Configures encoder and muxer state, and prepares the input Surface.
     */
    public VideoEncoderCore(int width, int height, int bitRate, File outputFile)
            throws IOException {
    	
        mVideoBufferInfo = new MediaCodec.BufferInfo();

        mVideoFormat = MediaFormat.createVideoFormat(VIDEO_MIME_TYPE, width, height);
        

        // Set some properties.  Failing to specify some of these can cause the MediaCodec
        // configure() call to throw an unhelpful exception.
        mVideoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        mVideoFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        mVideoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        mVideoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
        if (VERBOSE) Log.d(TAG, "format: " + mVideoFormat);

        // Create a MediaCodec encoder, and configure it with our format.  Get a Surface
        // we can use for input and wrap it with a class that handles the EGL work.
        mVideoEncoder = MediaCodec.createEncoderByType(VIDEO_MIME_TYPE);
        mVideoEncoder.configure(mVideoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mInputSurface = mVideoEncoder.createInputSurface();
        mVideoEncoder.start();
        
        
        mAudioBufferInfo = new MediaCodec.BufferInfo();
//        mAudioTrackInfo = new TrackInfo();

        mAudioFormat = new MediaFormat();
        mAudioFormat.setString(MediaFormat.KEY_MIME, AUDIO_MIME_TYPE);
        mAudioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        mAudioFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, 44100);
        mAudioFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
        mAudioFormat.setInteger(MediaFormat.KEY_BIT_RATE, 128000);
        mAudioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);

        mAudioEncoder = MediaCodec.createEncoderByType(AUDIO_MIME_TYPE);
        mAudioEncoder.configure(mAudioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mAudioEncoder.start();

        // Create a MediaMuxer.  We can't add the video track and start() the muxer here,
        // because our MediaFormat doesn't have the Magic Goodies.  These can only be
        // obtained from the encoder after it has started processing data.
        //
        // We're not actually interested in multiplexing audio.  We just want to convert
        // the raw H.264 elementary stream we get from MediaCodec into a .mp4 file.
        mMuxer = new MediaMuxer(outputFile.toString(),
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        Log.e("Chris","-------------- mMuxer inited outputFile = " + outputFile.toString());

        mAudioTrackIndex = -1;
        mVideoTrackIndex = -1;
        mMuxerStarted = false;
        setupAudioRecord();
    }

    /**
     * Returns the encoder's input surface.
     */
    public Surface getInputSurface() {
        return mInputSurface;
    }

    /**
     * Releases encoder resources.
     */
    public void release() {
        if (VERBOSE) Log.d(TAG, "releasing encoder objects");
        if (mVideoEncoder != null) {
            mVideoEncoder.stop();
            mVideoEncoder.release();
            mVideoEncoder = null;
        }
//        if (mMuxer != null) {
//            // TODO: stop() throws an exception if you haven't fed it any data.  Keep track
//            //       of frames submitted, and don't call stop() if we haven't written anything.
//            mMuxer.stop();
//            mMuxer.release();
//            mMuxer = null;
//        }
    }
    
    public void sendAudioToEncoder(boolean endOfStream) {
        // send current frame data to encoder
        try {
        	if (mAudioEncoder != null) {
	            ByteBuffer[] inputBuffers = mAudioEncoder.getInputBuffers();
	            int inputBufferIndex = mAudioEncoder.dequeueInputBuffer(-1);
	            if (inputBufferIndex >= 0) {
	                ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
	                inputBuffer.clear();
	                long presentationTimeNs = System.nanoTime();
	                int inputLength =  audioRecord.read(inputBuffer, SAMPLES_PER_FRAME );
	                presentationTimeNs -= (inputLength / SAMPLE_RATE ) / 1000000000;
	                if(inputLength == AudioRecord.ERROR_INVALID_OPERATION)
	                    Log.e(TAG, "Audio read error");
	
	                //long presentationTimeUs = (presentationTimeNs - startWhen) / 1000;
	                long presentationTimeUs = (presentationTimeNs - startWhen) / 1000;
	                if (VERBOSE) Log.i(TAG, "queueing " + inputLength + " audio bytes with pts " + presentationTimeUs);
	                if (endOfStream) {
	                    Log.i(TAG, "EOS received in sendAudioToEncoder");
	                    mAudioEncoder.queueInputBuffer(inputBufferIndex, 0, inputLength, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
	                    eosSentToAudioEncoder = true;
	                } else {
	                    mAudioEncoder.queueInputBuffer(inputBufferIndex, 0, inputLength, presentationTimeUs, 0);
	                }
	            }
        	}
        } catch (Throwable t) {
            Log.e(TAG, "_offerAudioEncoder exception");
            t.printStackTrace();
        }
    }
    
    private void setupAudioRecord(){
        int min_buffer_size = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        int buffer_size = SAMPLES_PER_FRAME * 10;
        if (buffer_size < min_buffer_size)
            buffer_size = ((min_buffer_size / SAMPLES_PER_FRAME) + 1) * SAMPLES_PER_FRAME * 2;

        audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,       // source
                SAMPLE_RATE,                         // sample rate, hz
                CHANNEL_CONFIG,                      // channels
                AUDIO_FORMAT,                        // audio format
                buffer_size);                        // buffer size (bytes)
    }

    public void startAudioRecord(){
        if(audioRecord != null){

            new Thread(new Runnable(){

                @Override
                public void run() {
                    audioRecord.startRecording();
                    boolean audioEosRequestedCopy = false;
                    while(true){

                        if(!firstFrameReady)
                            continue;
                        audioEosRequestedCopy = audioEosRequested; // make sure audioEosRequested doesn't change value mid loop
                        if (audioEosRequestedCopy || fullStopReceived){ // TODO post eosReceived message with Handler?
                            Log.i(TAG, "Audio loop caught audioEosRequested / fullStopReceived " + audioEosRequestedCopy + " " + fullStopReceived);
//                            if (TRACE) Trace.beginSection("sendAudio");
                            sendAudioToEncoder(true);
//                            if (TRACE) Trace.endSection();
                        }
                        if (fullStopReceived){
                            Log.i(TAG, "Stopping AudioRecord");
                            audioRecord.stop();
                        }

                        synchronized (sync){
//                            if (TRACE) Trace.beginSection("drainAudio");
                            drainAudioEncoder(audioEosRequestedCopy || fullStopReceived);
//                            if (TRACE) Trace.endSection();
                        }

                        if (audioEosRequestedCopy) audioEosRequested = false;

                        if (!fullStopReceived){
//                            if (TRACE) Trace.beginSection("sendAudio");
                            sendAudioToEncoder(false);
//                            if (TRACE) Trace.endSection();
                        }else{
                            break;
                        }
                    } // end while
                }
            }).start();

        }
        
        startWhen = System.nanoTime();

    }

    /**
     * Extracts all pending data from the encoder and forwards it to the muxer.
     * <p>
     * If endOfStream is not set, this returns when there is no more data to drain.  If it
     * is set, we send EOS to the encoder, and then iterate until we see EOS on the output.
     * Calling this with endOfStream set should be done once, right before stopping the muxer.
     * <p>
     * We're just using the muxer to get a .mp4 file (instead of a raw H.264 stream).  We're
     * not recording audio.
     */
    public void drainEncoder(boolean endOfStream) {
        final int TIMEOUT_USEC = 10000;
        if (VERBOSE) Log.d(TAG, "drainEncoder(" + endOfStream + ")");

        if (endOfStream) {
            if (VERBOSE) Log.d(TAG, "sending EOS to encoder");
            mVideoEncoder.signalEndOfInputStream();
        }

        ByteBuffer[] encoderOutputBuffers = mVideoEncoder.getOutputBuffers();
        while (true) {
            int encoderStatus = mVideoEncoder.dequeueOutputBuffer(mVideoBufferInfo, TIMEOUT_USEC);
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // no output available yet
                if (!endOfStream) {
                    break;      // out of while
                } else {
                    if (VERBOSE) Log.d(TAG, "no output available, spinning to await EOS");
                }
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                // not expected for an encoder
                encoderOutputBuffers = mVideoEncoder.getOutputBuffers();
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // should happen before receiving buffers, and should only happen once
                if (mMuxerStarted) {
                    throw new RuntimeException("format changed twice");
                }
                MediaFormat newFormat = mVideoEncoder.getOutputFormat();
                Log.d(TAG, "encoder output format changed: " + newFormat);

                Log.e("Chris","--------------add video track");
                // now that we have the Magic Goodies, start the muxer
                mVideoTrackIndex = mMuxer.addTrack(newFormat);
//                mMuxer.start();
                
                numTracksAdded.incrementAndGet();
                if(numTracksAdded.get() == TOTAL_NUM_TRACKS){
//                    if (VERBOSE) Log.i(TAG, "All tracks added, starting " + ((this == mMuxerWrapper) ? "muxer1" : "muxer2") +"!");
                    mMuxer.start();
                    Log.e("Chris","--------------muxer started");
                    started = true;
                }
                
                mMuxerStarted = true;
//                if (!firstFrameReady) startTime = System.nanoTime();
//                firstFrameReady = true;
            } else if (encoderStatus < 0) {
                Log.w(TAG, "unexpected result from encoder.dequeueOutputBuffer: " +
                        encoderStatus);
                // let's ignore it
            } else {
                ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                if (encodedData == null) {
                    throw new RuntimeException("encoderOutputBuffer " + encoderStatus +
                            " was null");
                }

                if ((mVideoBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    // The codec config data was pulled out and fed to the muxer when we got
                    // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                    if (VERBOSE) Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
                    mVideoBufferInfo.size = 0;
                }

                if (mVideoBufferInfo.size != 0) {
                	
                    if (!started) {
//                        Log.e(TAG, "Muxer not started. dropping " + ((encoder == mVideoEncoder) ? " video" : " audio") + " frames");
                        //throw new RuntimeException("muxer hasn't started");
                    } else{
	                    	
	                    if (!mMuxerStarted) {
	                        throw new RuntimeException("muxer hasn't started");
	                    }
	
	                    // adjust the ByteBuffer values to match BufferInfo (not needed?)
	                    encodedData.position(mVideoBufferInfo.offset);
	                    encodedData.limit(mVideoBufferInfo.offset + mVideoBufferInfo.size);
	
	                    mMuxer.writeSampleData(mVideoTrackIndex, encodedData, mVideoBufferInfo);
                        Log.e("Chris","---------------write video data");
	                    if (VERBOSE) {
	                        Log.d(TAG, "sent " + mVideoBufferInfo.size + " bytes to muxer, ts=" +
	                                mVideoBufferInfo.presentationTimeUs);
                    }
                    }
                }

                mVideoEncoder.releaseOutputBuffer(encoderStatus, false);

                if ((mVideoBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    if (!endOfStream) {
                        Log.w(TAG, "reached end of stream unexpectedly");
                    } else {
                    	Log.e("Chris","------------ stop from audio");
                    	finishTrack();
                        if (VERBOSE) Log.d(TAG, "end of stream reached");
                    }
                    break;      // out of while
                }
            }
        }
    }
    
    public void setFrameAvailable() {
    	firstFrameReady = true;
    }
    
    /**
     * Extracts all pending data from the encoder and forwards it to the muxer.
     * <p/>
     * If endOfStream is not set, this returns when there is no more data to drain.  If it
     * is set, we send EOS to the encoder, and then iterate until we see EOS on the output.
     * Calling this with endOfStream set should be done once, right before stopping the muxer.
     * <p/>
     * We're just using the muxer to get a .mp4 file (instead of a raw H.264 stream).  We're
     * not recording audio.
     */
    private void drainAudioEncoder(//MediaCodec encoder, MediaCodec.BufferInfo bufferInfo, TrackInfo trackInfo, 
    		boolean endOfStream) {
        final int TIMEOUT_USEC = 100;

        //TODO: Get Muxer from trackInfo
//        MediaMuxerWrapper muxerWrapper = trackInfo.muxerWrapper;
//
//        if (VERBOSE) Log.d(TAG, "drain" + ((encoder == mVideoEncoder) ? "Video" : "Audio") + "Encoder(" + endOfStream + ")");
//        if (endOfStream && encoder == mVideoEncoder) {
//            if (VERBOSE) Log.d(TAG, "sending EOS to " + ((encoder == mVideoEncoder) ? "video" : "audio") + " encoder");
//            encoder.signalEndOfInputStream();
//            eosSentToVideoEncoder = true;
//        }
        //TODO: stop the thread when received a stop message
        if (mAudioEncoder == null) {
        	return;
        }
        //testing
        ByteBuffer[] encoderOutputBuffers = mAudioEncoder.getOutputBuffers();

        while (true) {
            int encoderStatus = mAudioEncoder.dequeueOutputBuffer(mAudioBufferInfo, TIMEOUT_USEC);
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // no output available yet
                if (!endOfStream) {
                    if (VERBOSE) Log.d(TAG, "no output available. aborting drain");
                    break;      // out of while
                } else {
                    if (VERBOSE) Log.d(TAG, "no output available, spinning to await EOS");
                }
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                // not expected for an encoder
                encoderOutputBuffers = mAudioEncoder.getOutputBuffers();
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // should happen before receiving buffers, and should only happen once
                if (started) {
                    //Log.e(TAG, "format changed after muxer start! Can we ignore?");
                    //throw new RuntimeException("format changed after muxer start");
                }else{
                    MediaFormat newFormat = mAudioEncoder.getOutputFormat();
//                    if(encoder == mVideoEncoder)
//                        mVideoOutputFormat = newFormat;
//                    else if(encoder == mAudioEncoder)
//                        mAudioOutputFormat = newFormat;

                    Log.e("Chris","--------------add audio track");
                    // now that we have the Magic Goodies, start the muxer
                    mAudioTrackIndex = mMuxer.addTrack(newFormat);
                    numTracksAdded.incrementAndGet();
                    if(numTracksAdded.get() == TOTAL_NUM_TRACKS){
//                        if (VERBOSE) Log.i(TAG, "All tracks added, starting " + ((this == mMuxerWrapper) ? "muxer1" : "muxer2") +"!");
                        mMuxer.start();
                        started = true;
                    }
//                    if(!(numTracksAdded == TOTAL_NUM_TRACKS))
//                        break;  // Allow both encoders to send output format changed before attempting to write samples
                }

            } else if (encoderStatus < 0) {
                Log.w(TAG, "unexpected result from encoder.dequeueOutputBuffer: " +
                        encoderStatus);
                // let's ignore it
            } else {
                ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                if (encodedData == null) {
                    throw new RuntimeException("encoderOutputBuffer " + encoderStatus +
                            " was null");
                }

                if ((mAudioBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    // The codec config data was pulled out and fed to the muxer when we got
                    // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                    if (VERBOSE) Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
                    mAudioBufferInfo.size = 0;
                }


                if (mAudioBufferInfo.size != 0) {
                    if (!started) {
//                        Log.e(TAG, "Muxer not started. dropping " + ((encoder == mVideoEncoder) ? " video" : " audio") + " frames");
                        //throw new RuntimeException("muxer hasn't started");
                    } else{
                        // adjust the ByteBuffer values to match BufferInfo (not needed?)
                        encodedData.position(mAudioBufferInfo.offset);
                        encodedData.limit(mAudioBufferInfo.offset + mAudioBufferInfo.size);
//                        if(encoder == mAudioEncoder){
                            if(mAudioBufferInfo.presentationTimeUs < lastEncodedAudioTimeStamp)
                            	mAudioBufferInfo.presentationTimeUs = lastEncodedAudioTimeStamp += 23219; // Magical AAC encoded frame time
                            lastEncodedAudioTimeStamp = mAudioBufferInfo.presentationTimeUs;
//                        }
                        if(mAudioBufferInfo.presentationTimeUs < 0){
                        	mAudioBufferInfo.presentationTimeUs = 0;
                        }
                        mMuxer.writeSampleData(mAudioTrackIndex, encodedData, mAudioBufferInfo);
                        Log.e("Chris","---------------write audio data");

//                        if (VERBOSE)
//                            Log.d(TAG, "sent " + bufferInfo.size + ((encoder == mVideoEncoder) ? " video" : " audio") + " bytes to muxer with pts " + bufferInfo.presentationTimeUs);

                    }
                }

                mAudioEncoder.releaseOutputBuffer(encoderStatus, false);

                if ((mAudioBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    if (!endOfStream) {
                        Log.w(TAG, "reached end of stream unexpectedly");
                    } else {
                    	Log.e("Chris","------------ stop from audio");
                        finishTrack();
//                        if (VERBOSE) Log.d(TAG, "end of " + ((encoder == mVideoEncoder) ? " video" : " audio") + " stream reached. ");
                        if(!fullStopReceived){
//                            if(encoder == mVideoEncoder){
//                                Log.i(TAG, "Chunking video encoder");
////                                if (TRACE) Trace.beginSection("chunkVideoEncoder");
//                                chunkVideoEncoder();
////                                if (TRACE) Trace.endSection();
//                            }else if(encoder == mAudioEncoder){
                                Log.i(TAG, "Chunking audio encoder");
//                                if (TRACE) Trace.beginSection("chunkAudioEncoder");
                                chunkAudioEncoder();
//                                if (TRACE) Trace.endSection();
//                            }else
                                Log.e(TAG, "Unknown encoder passed to drainEncoder!");
                        }else{

//                            if(encoder == mVideoEncoder){
//                                Log.i(TAG, "Stopping and releasing video encoder");
//                                stopAndReleaseVideoEncoder();
//                            } else if(encoder == mAudioEncoder){
                                Log.i(TAG, "Stopping and releasing audio encoder");
                                stopAndReleaseAudioEncoder();
//                            }
                            //stopAndReleaseEncoders();
                        }
                    }
                    break;      // out of while
                }
            }
        }
        long endTime = System.nanoTime();
    }
    
    /**
     * This can be called within drainEncoder, when the end of stream is reached
     */
    private void chunkAudioEncoder(){
        stopAndReleaseAudioEncoder();

//        // Start Encoder
//        mAudioBufferInfo = new MediaCodec.BufferInfo();
//        //mVideoTrackInfo = new TrackInfo();
//        advanceAudioMediaMuxer();
//        mAudioEncoder = MediaCodec.createEncoderByType(AUDIO_MIME_TYPE);
//        mAudioEncoder.configure(mAudioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
//        mAudioEncoder.start();
    }
    
    private void stopAndReleaseAudioEncoder(){
        lastEncodedAudioTimeStamp = 0;
        eosSentToAudioEncoder = false;

        if (mAudioEncoder != null) {
            mAudioEncoder.stop();
            mAudioEncoder.release();
            mAudioEncoder = null;
        }
    }
    
    public void finishTrack(){
        numTracksFinished.getAndIncrement();
        if(numTracksFinished.get() == TOTAL_NUM_TRACKS){
//            if (VERBOSE) Log.i(TAG, "All tracks finished, stopping " + ((this == mMuxerWrapper) ? "muxer1" : "muxer2") + "!");
            Log.e("Chris","------------finishTrack");
        	stopMuxer();
        }

    }
    
    public void stopRecording(){
        Log.i(TAG, "stopRecording");
        fullStopReceived = true;
//        if (useMediaRecorder) mMediaRecorderWrapper.stopRecording();
//        double recordingDurationSec = (System.nanoTime() - startTime) / 1000000000.0;
//        Log.i(TAG, "Recorded " + recordingDurationSec + " s. Expected " + (FRAME_RATE * recordingDurationSec) + " frames. Got " + totalFrameCount + " for " + (totalFrameCount / recordingDurationSec) + " fps");
    }
    
    public void stopMuxer(){
        if(mMuxer != null){
            if(!allTracksFinished()) Log.e(TAG, "Stopping Muxer before all tracks added!");
            if(!started) Log.e(TAG, "Stopping Muxer before it was started");
            mMuxer.stop();
            mMuxer.release();
            mMuxer = null;
            started = false;
            chunk = 0;
            numTracksAdded.set(0);
            numTracksFinished.set(0);
        }
    }
    
    public boolean allTracksFinished(){
        return (numTracksFinished.get() == TOTAL_NUM_TRACKS);
    }
}

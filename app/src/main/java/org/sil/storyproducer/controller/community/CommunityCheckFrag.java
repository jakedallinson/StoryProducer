package org.sil.storyproducer.controller.community;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import org.sil.storyproducer.R;
import org.sil.storyproducer.model.StoryState;
import org.sil.storyproducer.tools.AudioPlayer;
import org.sil.storyproducer.tools.BitmapScaler;
import org.sil.storyproducer.tools.FileSystem;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Fragment for the community check view. The purpose of this phase is for the community to make
 * sure the draft is okay and leave any comments should they feel the need
 */
public class CommunityCheckFrag extends Fragment {
    public static final String SLIDE_NUM = "CURRENT_SLIDE_NUM_OF_FRAG";
    private final static String LOGTAG = "communityCheck";
    private int slidePosition;
    private static AudioPlayer draftPlayer;
    private static AudioPlayer commentPlayer;
    private MediaRecorder commentRecorder;
    private View rootView;
    private String[] comments;
    private boolean isRecording;

    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        Bundle passedArgs = this.getArguments();
        slidePosition = passedArgs.getInt(SLIDE_NUM);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        rootView = inflater.inflate(R.layout.fragment_com_check, container, false);

        updateCommentList();
        setUiColors();
        setPic((ImageView)rootView.findViewById(R.id.fragment_commcheck_image_view), slidePosition);
        setDraftPlaybackButton((ImageButton)rootView.findViewById(R.id.fragment_draft_playback_button));
        setRecordCommentButton((ImageButton)rootView.findViewById(R.id.fragment_commcheck_add_comment_button));

        return rootView;
    }

    /**
     * This function serves to handle draft page changes and stops the audio streams from
     * continuing.
     * @param isVisibleToUser
     */
    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);

        // Make sure that we are currently visible
        if (this.isVisible()) {
            // If we are becoming invisible, then...
            if (!isVisibleToUser) {
                stopAllMedia();
            }
        }
    }

    /**
     * This function serves to stop the audio streams from continuing after the draft has been
     * put on pause.
     */
    @Override
    public void onPause() {
        super.onPause();
        stopAllMedia();
    }

    /**
     * This function serves to stop the audio streams from continuing after the draft has been
     * put on stop.
     */
    @Override
    public void onStop() {
        super.onStop();
        stopAllMedia();
        if(draftPlayer != null){
            draftPlayer.releaseAudio();
        }
        if(commentPlayer != null){
            commentPlayer.releaseAudio();
        }
    }

    /**
     * Updates the list of comments at beginning of fragment creation and after any list change
     */
    public void updateCommentList() {
        ListView listView = (ListView)rootView.findViewById(R.id.audio_comment_list_view);
        comments = FileSystem.getCommentTitles(StoryState.getStoryName(), slidePosition);
        ListAdapter adapter = new CommentListAdapter(getContext(), comments, slidePosition, this);
        listView.setAdapter(adapter);
    }

    /**
     * This function sets the first slide of each story to the blue color in order to prevent
     * clashing of the grey starting picture.
     */
    private void setUiColors(){
        if(slidePosition == 0){
            RelativeLayout rl =  (RelativeLayout)rootView.findViewById(R.id.fragment_commcheck_Relative_Layout);
            rl.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.primaryDark));
        }
    }

    /**
     * This function allows the picture to scale with the phone's screen size.
     *
     * @param slideImage    The ImageView that will contain the picture.
     * @param slideNum The slide number to grab the picture from the files.
     */
    private void setPic(ImageView slideImage, int slideNum) {
        Bitmap slidePicture = FileSystem.getImage(StoryState.getStoryName(), slideNum);

        if(slidePicture == null){
            Snackbar.make(rootView, "Could Not Find Picture...", Snackbar.LENGTH_SHORT).show();
        }

        //Get the height of the phone.
        DisplayMetrics phoneProperties = getContext().getResources().getDisplayMetrics();
        int height = phoneProperties.heightPixels;
        double scalingFactor = 0.4;
        height = (int)(height * scalingFactor);

        //scale bitmap
        slidePicture = BitmapScaler.scaleToFitHeight(slidePicture, height);

        //Set the height of the image view
        slideImage.getLayoutParams().height = height;
        slideImage.requestLayout();

        slideImage.setImageBitmap(slidePicture);
    }

    /**
     * This function sets the draft playback to the correct audio file. Also, the narration
     * button will have a listener added to it in order to detect playback when pressed.
     * @param button the ImageButton view handler to set the onclicklistener to
     */
    private void setDraftPlaybackButton(ImageButton button) {

        final File draftFile = FileSystem.getTranslationAudio(StoryState.getStoryName(), slidePosition);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            //stop other playback streams.
            stopAllMedia();
            if (draftFile.exists()) {
                draftPlayer = new AudioPlayer();
                draftPlayer.playWithPath(draftFile.getPath());
                Toast.makeText(getContext(), "Playing Draft Audio...", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), "No Draft Audio Found...", Toast.LENGTH_SHORT).show();
            }
            }
        });
    }

    /**
     * Plays the audio comment designated by the title
     * @param commentTitle the title of the comment to play
     */
    public void playComment(String commentTitle) {
        final File commentFile = FileSystem.getAudioComment(StoryState.getStoryName(), slidePosition, commentTitle);
        stopAllMedia();
        if (commentFile.exists()) {
            commentPlayer = new AudioPlayer();
            commentPlayer.playWithPath(commentFile.getPath());
            Toast.makeText(getContext(), "Playing Comment...", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getContext(), "No Comment Found...", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * This function sets the recording button with its functionality
     */
    private void setRecordCommentButton(ImageButton recordButton){
        recordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Comment index for user starts at 1 so we increment 1 from the 0 based index
                int nextCommentIndex = comments.length + 1;
                String recordFilePath = FileSystem.getAudioComment(StoryState.getStoryName(), slidePosition,
                        "Comment " + nextCommentIndex).getPath();
                while (FileSystem.doesFileExist(recordFilePath)) {
                    nextCommentIndex++;
                    recordFilePath = FileSystem.getAudioComment(StoryState.getStoryName(), slidePosition,
                            "Comment " + nextCommentIndex).getPath();
                }
                //stop all playback streams.
                if(draftPlayer != null && draftPlayer.isAudioPlaying()){
                    draftPlayer.stopAudio();
                }
                if(commentPlayer != null && commentPlayer.isAudioPlaying()){
                    commentPlayer.stopAudio();
                }
                if(isRecording){
                    stopAudioRecorder();
                    updateCommentList();
                }else{
                    startAudioRecorder(recordFilePath);
                }
            }
        });
    }

    /**
     * The function that aids in starting an audio recorder.
     */
    private void startAudioRecorder(String recordFilePath){
        setVoiceRecorder(recordFilePath);
        try {
            commentRecorder.prepare();
            commentRecorder.start();
            isRecording = true;
            Toast.makeText(getContext(), "Recording comment!", Toast.LENGTH_SHORT).show();
        } catch (IllegalStateException | IOException e){
            Log.e(LOGTAG, "Error recording comment");
            Toast.makeText(getContext(), "Error recording comment", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * The function that aids in stopping an audio recorder.
     * According to documentation, it should have been enough to reset the comment recorder
     * but it was not working, so we go ahead and release it and make another for any subsequent
     * comments
     */
    private void stopAudioRecorder(){
        try{
            commentRecorder.stop();
            isRecording = false;
            Toast.makeText(getContext(), "Stopped recording!", Toast.LENGTH_SHORT).show();
        }catch(RuntimeException stopException){
            Toast.makeText(getContext(), "Please record again!", Toast.LENGTH_SHORT).show();
        }
        commentRecorder.release();
        commentRecorder = null;
    }


    /**
     * This function sets the voice recorder with either a new voicerecorder or reuses the
     * voicerecorder.
     * @param fileName The file to output the voice recordings.
     */
    private void setVoiceRecorder(String fileName){

        commentRecorder = new MediaRecorder();


        if (ContextCompat.checkSelfPermission(getActivity(),
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(getActivity(),
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    1);
        }

        // The encoding and sampling rates are standards for the AAC encoder
        commentRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        commentRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        commentRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        commentRecorder.setAudioEncodingBitRate(16);
        commentRecorder.setAudioSamplingRate(44100);
        commentRecorder.setOutputFile(fileName);
    }

    /**
     * Stops all media including audio playbacks and active audio recordings
     */
    private void stopAllMedia() {
        if(draftPlayer != null && draftPlayer.isAudioPlaying()){
            draftPlayer.stopAudio();
        }
        if(commentPlayer != null && commentPlayer.isAudioPlaying()){
            commentPlayer.stopAudio();
        }
        if(commentRecorder != null) {
            stopAudioRecorder();
            updateCommentList();
        }

    }
}
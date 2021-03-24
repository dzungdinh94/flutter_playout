package tv.mta.flutter_playout.audio;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.view.KeyEvent;
import java.util.concurrent.ExecutionException;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import tv.mta.flutter_playout.FlutterAVPlayer;
import tv.mta.flutter_playout.PlayerNotificationUtil;
import tv.mta.flutter_playout.PlayerState;
import tv.mta.flutter_playout.R;
import tv.mta.flutter_playout.Utils;

public class AudioServiceBinder extends Binder implements FlutterAVPlayer, MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener {

    public static AudioServiceBinder currentService;

    /**
     * Whether the {@link MediaPlayer} broadcasted an error.
     */
    private boolean mReceivedError;

    /**
     * Playback Rate for the MediaPlayer is always 1.0.
     */
    private static final float PLAYBACK_RATE = 1.0f;

    /**
     * The notification channel id we'll send notifications too
     */
    public static final String mNotificationChannelId = "NotificationBarController";

    /**
     * The notification id.
     */
    private static final int NOTIFICATION_ID = 0;

    private String audioFileUrl = "";

    private String title;

    private String subtitle;

    private String largeImageUrl;

    private boolean isLoadingMode = false;

    private boolean streamAudio = false;

    private MediaPlayer audioPlayer = null;

    private int startPositionInMills = 0;

    // This Handler object is a reference to the caller activity's Handler.
    // In the caller activity's handler, it will update the audio play progress.
    private Handler audioProgressUpdateHandler;

    // This is the message signal that inform audio progress updater to update audio progress.
    public final int UPDATE_AUDIO_PROGRESS_BAR = 1;

    public final int UPDATE_PLAYER_STATE_TO_PAUSE = 2;

    public final int UPDATE_PLAYER_STATE_TO_PLAY = 3;

    public final int UPDATE_PLAYER_STATE_TO_COMPLETE = 4;

    public final int UPDATE_AUDIO_DURATION = 5;

    public final int AUDIO_LOADING_COMPLETED = 6;

    boolean isBound = true;

    boolean isMediaChanging = false;

    /**
     * The underlying {@link MediaSessionCompat}.
     */
    private MediaSessionCompat mMediaSessionCompat;

    private Context context;

    private Activity activity;

    public MediaPlayer getAudioPlayer() {
        return audioPlayer;
    }

    public String getAudioFileUrl() {
        return audioFileUrl;
    }

    public void setAudioFileUrl(String audioFileUrl) {
        this.audioFileUrl = audioFileUrl;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public void setLargeImageUrl(String largeImageUrl) {
        this.largeImageUrl = largeImageUrl;
    }

    public void setIsLoadingMode(Boolean isLoadingMode) {
        this.isLoadingMode = isLoadingMode;
    }

    public boolean getIsLoadingMode() {
        return this.isLoadingMode;
    }


    public boolean isStreamAudio() {
        return streamAudio;
    }

    public void setStreamAudio(boolean streamAudio) {
        this.streamAudio = streamAudio;
    }

    public Handler getAudioProgressUpdateHandler() {
        return audioProgressUpdateHandler;
    }

    public Context getContext() {
        return context;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    public Activity getActivity() {
        return activity;
    }

    public void setActivity(Activity activity) {
        this.activity = activity;
    }

    public boolean isMediaChanging() {
        return isMediaChanging;
    }

    public void setMediaChanging(boolean mediaChanging) {
        isMediaChanging = mediaChanging;
    }

    public void setAudioProgressUpdateHandler(Handler audioProgressUpdateHandler) {
        this.audioProgressUpdateHandler = audioProgressUpdateHandler;
    }

    public void setAudioMetadata() {
        Utils utils = new Utils();
        MediaMetadataCompat metadata = null;
        try {
            metadata = new MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, title)
                    .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, subtitle)
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, utils.execute(largeImageUrl).get())
                    .build();
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        mMediaSessionCompat.setMetadata(metadata);
    }

    public void startAudio(int startPositionInMills, boolean isLoadingMode) {

        this.isLoadingMode = isLoadingMode;

        this.startPositionInMills = startPositionInMills;

        initAudioPlayer();

        if (audioPlayer != null && mMediaSessionCompat != null && mMediaSessionCompat.isActive()) {

            if(isLoadingMode) {
                updatePlaybackState(PlayerState.PAUSED);
            } else {
                updatePlaybackState(PlayerState.PLAYING);
            }

            // Create update audio player state message.
            Message updateAudioProgressMsg = new Message();

            updateAudioProgressMsg.what = UPDATE_PLAYER_STATE_TO_PLAY;

            // Send the message to caller activity's update audio Handler object.
            audioProgressUpdateHandler.sendMessage(updateAudioProgressMsg);
        }

        currentService = this;
    }

    public void seekAudio(int position) {

        audioPlayer.seekTo(position * 1000);
    }

    public void pauseAudio() {

        if (audioPlayer != null) {

            if (audioPlayer.isPlaying()) {

                audioPlayer.pause();
            }

            updatePlaybackState(PlayerState.PAUSED);

            // Create update audio player state message.
            Message updateAudioProgressMsg = new Message();

            updateAudioProgressMsg.what = UPDATE_PLAYER_STATE_TO_PAUSE;

            // Send the message to caller activity's update audio Handler object.
            audioProgressUpdateHandler.sendMessage(updateAudioProgressMsg);
        }
    }

    public void stopAudio() {

        if (audioPlayer != null) {

            if (audioPlayer.isPlaying()) {

                audioPlayer.stop();
            }

            updatePlaybackState(PlayerState.COMPLETE);
        }

        onDestroy();
    }

    private void cleanPlayerNotification() {
        NotificationManager notificationManager = (NotificationManager)
                getContext().getSystemService(Context.NOTIFICATION_SERVICE);

        notificationManager.cancel(NOTIFICATION_ID);
    }

    private void initAudioPlayer() {

        try {

            if (audioPlayer == null) {

                audioPlayer = new MediaPlayer();

                if (!TextUtils.isEmpty(getAudioFileUrl())) {

                    if (isStreamAudio()) {

                        audioPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                    }

                    audioPlayer.setDataSource(getAudioFileUrl());
                }

                audioPlayer.setOnPreparedListener(this);

                audioPlayer.setOnCompletionListener(this);

                audioPlayer.prepareAsync();

            } else {

                audioPlayer.start();
                if(startPositionInMills > 0) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        audioPlayer.seekTo(startPositionInMills, MediaPlayer.SEEK_CLOSEST);
                    }
                    else {
                        audioPlayer.seekTo(startPositionInMills);
                    }
                }
            }

        } catch (IOException ex) { mReceivedError = true; }
    }

    public void resetPlayer() {

        try {

            cleanPlayerNotification();

            if (audioPlayer != null) {

                if (audioPlayer.isPlaying()) {

                    audioPlayer.stop();
                }

                audioPlayer.reset();

                audioPlayer.release();

                audioPlayer = null;
            }

        } catch (Exception e) { /* ignore */ }
    }

    @Override
    public void onDestroy() {

        isBound = false;

        resetPlayer();
    }

    public int getCurrentAudioPosition() {
        int ret = 0;

        if (audioPlayer != null) {

            ret = audioPlayer.getCurrentPosition();
        }

        return ret;
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        if (audioPlayer == null) {
            return;
        }

        setMediaChanging(false);

        if (startPositionInMills > 0) {
            mp.seekTo(startPositionInMills);
        }

        if(!isLoadingMode) {
            mp.start();
        }

        ComponentName receiver = new ComponentName(context.getPackageName(),
                RemoteReceiver.class.getName());

        /* Create a new MediaSession */
        mMediaSessionCompat = new MediaSessionCompat(context,
                AudioServiceBinder.class.getSimpleName(), receiver, null);

        mMediaSessionCompat.setFlags(MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
                | MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS);

        mMediaSessionCompat.setCallback(new MediaSessionCallback(audioPlayer));

        mMediaSessionCompat.setActive(true);

        setAudioMetadata();

        Message updateAudioDurationMsg = new Message();

        updateAudioDurationMsg.what = AUDIO_LOADING_COMPLETED;

        audioProgressUpdateHandler.sendMessage(updateAudioDurationMsg);

        if(isLoadingMode) {
            updatePlaybackState(PlayerState.PAUSED);
        } else {
            setIsLoadingMode(false);
            updatePlaybackState(PlayerState.PLAYING);
        }

        /* This thread object will send update audio progress message to caller activity every 1 second */
        Thread updateAudioProgressThread = new Thread() {

            @Override
            public void run() {

                while (isBound) {

                    try {
                        if (audioPlayer != null && audioPlayer.isPlaying()) {

                            // Create update audio progress message.
                            Message updateAudioProgressMsg = new Message();

                            updateAudioProgressMsg.what = UPDATE_AUDIO_PROGRESS_BAR;

                            // Send the message to caller activity's update audio progressbar Handler object.
                            audioProgressUpdateHandler.sendMessage(updateAudioProgressMsg);

                            try {

                                Thread.sleep(1000);

                            } catch (InterruptedException ex) { /* ignore */ }

                        } else {

                            try {

                                Thread.sleep(100);

                            } catch (InterruptedException ex) { /* ignore */ }
                        }
                    } catch (Exception ex) {
                        if (audioPlayer == null) break;
                    }

                    // Create update audio duration message.
                    Message updateAudioDurationMsg = new Message();

                    updateAudioDurationMsg.what = UPDATE_AUDIO_DURATION;

                    // Send the message to caller activity's update audio progressbar Handler object.
                    audioProgressUpdateHandler.sendMessage(updateAudioDurationMsg);
                }
            }
        };

        updateAudioProgressThread.start();
    }

    @Override
    public void onCompletion(MediaPlayer mp) {

        if(audioPlayer != null) {

            audioPlayer.pause();

            updatePlaybackState(PlayerState.PAUSED);

            // Create update audio player state message.
            Message updateAudioProgressMsg = new Message();

            updateAudioProgressMsg.what = UPDATE_PLAYER_STATE_TO_COMPLETE;

            // Send the message to caller activity's update audio Handler object.
            audioProgressUpdateHandler.sendMessage(updateAudioProgressMsg);
        }
    }

    /**
     * A {@link android.support.v4.media.session.MediaSessionCompat.Callback} implementation for MediaPlayer.
     */
    private final class MediaSessionCallback extends MediaSessionCompat.Callback {

        public MediaSessionCallback(MediaPlayer player) {
            audioPlayer = player;
        }

        @Override
        public void onPause() {
            audioPlayer.pause();
        }

        @Override
        public void onPlay() {
            audioPlayer.start();
        }

        @Override
        public void onSeekTo(long pos) {
            audioPlayer.seekTo((int) pos);
        }

        @Override
        public void onStop() {
            audioPlayer.stop();
        }
    }

    private PlaybackStateCompat.Builder getPlaybackStateBuilder() {

        PlaybackStateCompat playbackState = mMediaSessionCompat.getController().getPlaybackState();

        return playbackState == null
                ? new PlaybackStateCompat.Builder()
                : new PlaybackStateCompat.Builder(playbackState);
    }

    private void updatePlaybackState(PlayerState playerState) {

        if (mMediaSessionCompat == null) return;

        PlaybackStateCompat.Builder newPlaybackState = getPlaybackStateBuilder();

        long capabilities = getCapabilities(playerState);

        newPlaybackState.setActions(capabilities);

        int playbackStateCompat = PlaybackStateCompat.STATE_NONE;

        switch (playerState) {
            case PLAYING:
                playbackStateCompat = PlaybackStateCompat.STATE_PLAYING;
                break;
            case PAUSED:
                playbackStateCompat = PlaybackStateCompat.STATE_PAUSED;
                break;
            case BUFFERING:
                playbackStateCompat = PlaybackStateCompat.STATE_BUFFERING;
                break;
            case IDLE:
                if (mReceivedError) {
                    playbackStateCompat = PlaybackStateCompat.STATE_ERROR;
                } else {
                    playbackStateCompat = PlaybackStateCompat.STATE_STOPPED;
                }
                break;
        }
        newPlaybackState.setState(playbackStateCompat, (long) audioPlayer.getCurrentPosition(), PLAYBACK_RATE);

        mMediaSessionCompat.setPlaybackState(newPlaybackState.build());

        updateNotification(capabilities);
    }

    private @PlaybackStateCompat.Actions long getCapabilities(PlayerState playerState) {
        long capabilities = 0;

        switch (playerState) {
            case PLAYING:
                capabilities |= PlaybackStateCompat.ACTION_PAUSE
                        | PlaybackStateCompat.ACTION_STOP;
                break;
            case PAUSED:
                capabilities |= PlaybackStateCompat.ACTION_PLAY
                        | PlaybackStateCompat.ACTION_STOP;
                break;
            case BUFFERING:
                capabilities |= PlaybackStateCompat.ACTION_PAUSE
                        | PlaybackStateCompat.ACTION_STOP;
                break;
            case IDLE:
                if (!mReceivedError) {
                    capabilities |= PlaybackStateCompat.ACTION_PLAY;
                }
                break;
        }

        return capabilities;
    }

    private void updateNotification(long capabilities) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            createNotificationChannel();
        }

        NotificationCompat.Builder notificationBuilder = PlayerNotificationUtil.from(
                activity, context, mMediaSessionCompat, mNotificationChannelId);

        notificationBuilder = addActions(notificationBuilder, capabilities);

        NotificationManager notificationManager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);

        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private void createNotificationChannel(){

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(context.NOTIFICATION_SERVICE);

        String id = mNotificationChannelId;

        CharSequence channelNameDisplayedToUser = "Notification Bar Controls";

        int importance = NotificationManager.IMPORTANCE_LOW;

        NotificationChannel newChannel = new NotificationChannel(id,channelNameDisplayedToUser,importance);

        newChannel.setDescription("All notifications");

        newChannel.setShowBadge(false);

        newChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

        notificationManager.createNotificationChannel(newChannel);
    }

    private NotificationCompat.Builder addActions(NotificationCompat.Builder notification,
                                                  long capabilities) {

        if ((capabilities & PlaybackStateCompat.ACTION_PAUSE) != 0) {
            notification.addAction(R.drawable.ic_pause, "Pause",
                    PlayerNotificationUtil.getActionIntent(context, KeyEvent.KEYCODE_MEDIA_PAUSE));
        }
        if ((capabilities & PlaybackStateCompat.ACTION_PLAY) != 0) {
            notification.addAction(R.drawable.ic_play, "Play",
                    PlayerNotificationUtil.getActionIntent(context, KeyEvent.KEYCODE_MEDIA_PLAY));
        }
        return notification;
    }
}

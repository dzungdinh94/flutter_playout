package tv.mta.flutter_playout;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.view.KeyEvent;

import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class PlayerNotificationUtil  {

    /**
     * Creates a new Notification builder from an existing media session.
     * @param context
     * @param mediaSession
     * @return
     */
    public static NotificationCompat.Builder from(Activity activity,
                                                  Context context,
                                                  MediaSessionCompat mediaSession,
                                                  String notificationChannelId) {

        MediaControllerCompat controller = mediaSession.getController();

        MediaMetadataCompat mediaMetadata = controller.getMetadata();

        MediaDescriptionCompat description = mediaMetadata.getDescription();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, notificationChannelId);

        int smallIcon = context.getResources().getIdentifier(
                "ic_launcher", "mipmap", context.getPackageName());
        Bitmap largeImage = BitmapFactory.decodeResource(context.getResources(), smallIcon);

        builder.setContentTitle(description.getTitle())
                .setContentText(description.getSubtitle())
                .setLargeIcon(getLargeIamgeBitmap(description.getIconBitmap(), context))
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken()))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setSmallIcon(smallIcon)
                .setOngoing(true)
                .setDeleteIntent(getActionIntent(context, KeyEvent.KEYCODE_MEDIA_STOP));

        Intent intent = new Intent(context, activity.getClass());

        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);

        builder.setContentIntent(pendingIntent);

        return builder;
    }

    public static PendingIntent getActionIntent(Context context, int mediaKeyEvent) {

        Intent intent = new Intent(Intent.ACTION_MEDIA_BUTTON);

        intent.setPackage(context.getPackageName());

        intent.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_DOWN, mediaKeyEvent));

        return PendingIntent.getBroadcast(context, mediaKeyEvent, intent, 0);
    }

    private static Bitmap getLargeIamgeBitmap(Bitmap fromDes, Context context){
        if(fromDes == null) {
            return BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_play_default);
        }
        return fromDes;
    }
}
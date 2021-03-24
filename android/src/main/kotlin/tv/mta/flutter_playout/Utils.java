package tv.mta.flutter_playout;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class Utils  extends AsyncTask<String, Void, Bitmap> {
    public Bitmap doInBackground(String... urls) {
        Bitmap bm = null;
        try {
            URL url = new URL(urls[0]);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            InputStream input = connection.getInputStream();
            Bitmap myBitmap = BitmapFactory.decodeStream(input);
            return myBitmap;
        } catch (IOException e) {
            Log.e("img", "Error getting bitmap", e);
        }
        return bm;
    }

    protected void onPostExecute(Bitmap bm) {
        //do something after execute


    }
}

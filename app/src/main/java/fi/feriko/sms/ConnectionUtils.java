package fi.feriko.sms;
import android.os.Environment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

class ConnectionUtils {

    public static String receiveResponse(HttpURLConnection conn) throws IOException {
        InputStream is = null;
        try {
            is = conn.getInputStream();

            BufferedReader in = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));

            int ch;
            StringBuffer sb = new StringBuffer();
            while ((ch = in.read()) != -1) {
                sb.append((char) ch);
            }
            return sb.toString();
        } catch (IOException e) {
            throw e;
        } finally {
            if (is != null) {
                is.close();
            }
        }
    }
}


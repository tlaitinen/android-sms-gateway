package fi.feriko.sms;

/**
 * Created by tero on 12/10/15.
 */

import java.io.File;
        import java.io.FileOutputStream;
        import java.io.IOException;
        import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.Thread.UncaughtExceptionHandler;
        import java.text.SimpleDateFormat;
        import java.util.Date;

        import android.app.Activity;
        import android.app.AlarmManager;
        import android.app.PendingIntent;
        import android.content.Context;
        import android.content.Intent;
        import android.os.Environment;
        import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * This custom class is used to handle exception.
 *
 * @author Chintan Rathod (http://www.chintanrathod.com)
 */
public class DefaultExceptionHandler implements UncaughtExceptionHandler {

    private UncaughtExceptionHandler defaultUEH;
    MainActivity activity;

    public DefaultExceptionHandler(MainActivity activity) {
        this.activity = activity;
    }

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {

        StringWriter stackTrace = new StringWriter();
        ex.printStackTrace(new PrintWriter(stackTrace));

        StringBuilder errorReport = new StringBuilder();
        errorReport.append(stackTrace.toString());

        Log.e("SMS", errorReport.toString());
        try {
            JSONObject o = new JSONObject();
            o.put("event", "UnhandledException");
            o.put("stackTrace", stackTrace.toString());
            activity.logUsage(o);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        Intent intent = new Intent(activity, MainActivity.class);

        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TASK
                | Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                activity.getApplicationContext(), 0, intent, 0);
        //Following code will restart your application after 2 seconds
        AlarmManager mgr = (AlarmManager) activity.getApplicationContext()
                .getSystemService(Context.ALARM_SERVICE);
        mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 1000,
                pendingIntent);

        //This will finish your activity manually
        activity.finish();

        //This will stop your application and take out from it.
        System.exit(2);

    }
}

package fi.feriko.sms;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicReference;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

public class MainActivity extends ActionBarActivity {

    private BackendThread backend;
    private SharedPreferences prefs;

    private final AtomicReference<Date> nextLoad = new AtomicReference<Date>(new Date());
    private final AtomicReference<Date> nextSend = new AtomicReference<Date>(new Date());
    private SimpleEula eula;
    private static TimeZone tz = TimeZone.getTimeZone("UTC");
    Map<Integer, JSONObject> pendingOutgoing = new HashMap<Integer, JSONObject>();
    List<Integer> accepted = new LinkedList<Integer>();
    Map<Integer, Pair<String, String> > pendingIncoming = new HashMap<Integer, Pair<String, String> >();
    SMSUtils smsUtils;



    private String toStackTrace(Exception e) {
        StringWriter stackTrace = new StringWriter();
        e.printStackTrace(new PrintWriter(stackTrace));
        return stackTrace.toString();
    }
    private JSONArray getSent() {
        try {
            return new JSONArray(prefs.getString("sent", "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }
    private void markAsSent(int id) {
        JSONArray sent = getSent();
        ArrayList<Integer> s = new ArrayList<Integer>();
        try {
            int start = (sent.length() >= 100) ? 1 : 0;
            for (int i = start; i < sent.length(); i++) {
                s.add(sent.getInt(i));
            }
            sent = new JSONArray();
            for (Integer i : s) {
                sent.put(i);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        sent.put(id);

        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("sent", sent.toString());
        editor.apply();
    }
    private boolean isSent(int id) {
        JSONArray sent = getSent();

        try {
            for (int i = 0; i < sent.length(); i++) {
                if (sent.getInt(i) == id)
                    return true;
            }
            return false;
        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }
    }
    private JSONArray getUsageLog() {
        try {
            return new JSONArray(prefs.getString("usageLog", "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }
    public void logUsage(JSONObject o) {
        try {

            JSONArray log = getUsageLog();
            DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
            df.setTimeZone(tz);
            o.put("time", df.format(new Date()));
            o.put("sender", "SMS");
            log.put(o);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("usageLog", log.toString());
            editor.apply();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
    private void clearUsageLog() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("usageLog", "[]");
        editor.apply();
    }


    private Handler smsHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (eula.hasBeenShown() &&  !prefs.getString("session", "").isEmpty()) {

                nextSend.set(new Date((new Date()).getTime() + 5000));
                int id = msg.arg1;
                int bwhat = BackendThread.MESSAGEFAIL;
                Resources r = getResources();


                String failReason = "";
                switch (msg.what) {
                    case SMSUtils.SENT:
                        statusLog(String.format(r.getString(R.string.message_sent), id));
                        bwhat = BackendThread.MESSAGESENT;
                        break;
                    case SMSUtils.NOT_SENT:
                        statusLog(String.format(r.getString(R.string.message_not_sent), id));
                        failReason = "NOT_SENT";
                        break;
                    case SMSUtils.NO_SERVICE:
                        statusLog(String.format(r.getString(R.string.message_no_service), id));
                        failReason = "NO_SERVICE";
                        break;
                    case SMSUtils.NULL_PDU:
                        statusLog(String.format(r.getString(R.string.message_null_pdu), id));
                        failReason = "NULL_PDU";
                        break;
                    case SMSUtils.RADIO_OFF:
                        statusLog(String.format(r.getString(R.string.message_radio_off), id));
                        failReason = "RADIO_OFF";
                        break;
                    case SMSUtils.UNKNOWN:
                        statusLog(String.format(r.getString(R.string.message_unknown), id));
                        failReason = "UNKNOWN";
                        break;
                    case SMSUtils.DELIVERY_OK:
                        statusLog(String.format(r.getString(R.string.message_delivery_ok), id));
                        bwhat = BackendThread.MESSAGEDELIVERED;
                        break;
                    case SMSUtils.DELIVERY_FAIL:
                        statusLog(String.format(r.getString(R.string.message_delivery_fail), id));

                        bwhat = -1;
                        break;
                    case SMSUtils.NO_SMS_FEATURE:
                        statusLog(String.format(r.getString(R.string.message_no_sms_feature), id));
                        failReason = "NO_SMS_FEATURE";

                        break;
                }
                if (bwhat > -1) {
                    Message bmsg = backend.handler.obtainMessage(bwhat);
                    bmsg.arg1 = id;
                    bmsg.obj = failReason;
                    backend.handler.sendMessage(bmsg);
                }
            }
        }
    };

    SMSReceiver smsReceiver;
    private Handler smsReceiverHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (eula.hasBeenShown()) {
                switch (msg.what) {
                    case SMSReceiver.SMS_RECEIVED:
                        Pair<String, String> m = (Pair<String, String>) msg.obj;
                        statusLog(String.format(getResources().getString(R.string.message_received), m.first, m.second));
                        Message bmsg = backend.handler.obtainMessage(BackendThread.MESSAGERECEIVED);
                        Integer msgId = new Integer((int) System.currentTimeMillis());
                        pendingIncoming.put(msgId, m);
                        bmsg.arg1 = msgId.intValue();
                        bmsg.obj = m;
                        backend.handler.sendMessage(bmsg);

                        break;
                }
            }
        }
    };

    private Handler timerHandler = new Handler();
    private Runnable timerRunnable = new Runnable() {

        @Override
        public void run() {

            if (eula.hasBeenShown()) {

                Date now = new Date();

                if (!prefs.getString("session", "").isEmpty()) {
                    if (now.after(nextLoad.get())) {
                        JSONArray usageLog = getUsageLog();
                        if (usageLog.length() > 0) {
                            Message msg = backend.handler.obtainMessage(BackendThread.SENDUSAGELOGS);
                            msg.obj = usageLog;
                            backend.handler.sendMessage(msg);
                        }

                        nextLoad.set(new Date(now.getTime() + 60000));
                        statusLog(getResources().getString(R.string.checking_for_messages));
                        backend.handler.sendEmptyMessage(BackendThread.LOADMESSAGES);
                        clearOldPendingMessages();
                        for (Map.Entry<Integer, Pair<String, String>> entry : pendingIncoming.entrySet()) {

                            Message msg = backend.handler.obtainMessage(BackendThread.MESSAGERECEIVED);
                            msg.arg1 = entry.getKey().intValue();
                            msg.obj = entry.getValue();
                            backend.handler.sendMessage(msg);
                        }
                    }
                    if (now.after(nextSend.get())) {
                        nextSend.set(new Date(now.getTime() + 1000));
                        if (accepted.isEmpty() == false) {
                            int id = accepted.remove(0).intValue();

                            nextSend.set(new Date(now.getTime() + 30000));
                            processMessage(id);

                            if (pendingOutgoing.isEmpty()) {
                                nextLoad.set(now);
                            }
                        }
                    }
                }
            }
            timerHandler.removeCallbacks(this);
            timerHandler.postDelayed(this, 1000);
        }
    };
    private long now() {
        return System.currentTimeMillis() / 1000L;
    }

    private boolean isNetworkConnected(){
        ConnectivityManager connectivityManager
                = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    public void clearOldPendingMessages() {
        long n = now();
        ArrayList<Integer> toRemove = new ArrayList<Integer>();
        for (Map.Entry<Integer, JSONObject> entry : pendingOutgoing.entrySet()) {
            try {
                long time = entry.getValue().getLong("time");
                if (n - time > 60) {
                    statusLog(String.format(getResources().getString(R.string.clearing_old_pending_message),
                            entry.getKey()));
                    toRemove.add(entry.getKey());
                    }
            } catch (JSONException e) {
                toRemove.add(entry.getKey());
                e.printStackTrace();
            }
        }
        for (Integer key : toRemove) {
            pendingOutgoing.remove(key);
        }

    }
    public MainActivity() {
        smsUtils = null;
        smsReceiver = null;

    }
    public static void hideSoftKeyboard(Activity activity) {
        InputMethodManager inputMethodManager = (InputMethodManager)  activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(activity.getCurrentFocus().getWindowToken(), 0);
    }

    public void login(View view) {

        if (!isNetworkConnected()) {
            statusLog(getResources().getString(R.string.not_connected));
            return;
        }
        EditText userName = (EditText) findViewById(R.id.username);
        EditText password = (EditText) findViewById(R.id.password);
        Message msg = backend.handler.obtainMessage();
        msg.what = BackendThread.LOGIN;
        String user = userName.getText().toString();
        String pwd = password.getText().toString();
        msg.obj = new Pair<String, String>(user, pwd);

        backend.handler.sendMessage(msg);

    }

    public void statusLog(String s) {
        TextView textView = (TextView) findViewById(R.id.messages);
        String text = textView.getText().toString();
        String[] lines = text.split("\n");
        if (lines.length > 100) {
            lines = Arrays.copyOfRange(lines, 1, lines.length);
        }
        text = "";
        for (String line : lines) {
            text += line + "\n";
        }
        DateFormat dateFormat = new SimpleDateFormat(getResources().getString(R.string.dateformat));
        Calendar cal = Calendar.getInstance();
        text += dateFormat.format(cal.getTime()) + " : " + s;
        textView.setText(text, TextView.BufferType.NORMAL);

        final ScrollView scrollView = ((ScrollView) findViewById(R.id.textAreaScroller));
        scrollView.post(new Runnable() {
            @Override
            public void run() {
                scrollView.fullScroll(View.FOCUS_DOWN);
            }
        });

    }


    public void setupUI(View view) {
        final Activity activity = this;

        //Set up touch listener for non-text box views to hide keyboard.
        if(!(view instanceof EditText)) {

            view.setOnTouchListener(new View.OnTouchListener() {

                public boolean onTouch(View v, MotionEvent event) {
                    hideSoftKeyboard(activity);
                    return false;
                }

            });
        }

        //If a layout container, iterate over children and seed recursion.
        if (view instanceof ViewGroup) {

            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {

                View innerView = ((ViewGroup) view).getChildAt(i);

                setupUI(innerView);
            }
        }
    }

    private void logException(Exception e) {
        try {
            JSONObject o = new JSONObject();
            o.put("event", "Exception");
            o.put("stackTrace", toStackTrace(e));
            logUsage(o);
        } catch (JSONException ee) {
            ee.printStackTrace();
        }
    }
    private void acceptMessages(JSONArray msgs) {

        Resources r = getResources();
        statusLog(String.format(r.getString(R.string.server_message_queue_status), msgs.length()));
        for (int i = 0; i < msgs.length(); i++) {
            if (pendingOutgoing.size() >= 5)
                break;
            try {
                JSONObject o = msgs.getJSONObject(i);
                int id = o.getInt("id");
                o.put("time", now());
                pendingOutgoing.put(new Integer(id), o);
                statusLog(String.format(r.getString(R.string.accepting_message), id));
                Message msg = backend.handler.obtainMessage(BackendThread.ACCEPTMESSAGE);
                msg.arg1 = o.getInt("id");
                backend.handler.sendMessage(msg);
            } catch (JSONException e) {
                logException(e);
                e.printStackTrace();
            }
        }

    }
    private JSONObject newLogEvent(String event) {
        try {
            JSONObject o = new JSONObject();
            o.put("event", event);
            return o;
        } catch (JSONException e) {
            return new JSONObject();
        }
    }
    private void simpleLog(String event, String field, int value) {
        try {
            JSONObject o = newLogEvent(event);
            o.put(field, value);
            logUsage(o);
        } catch (JSONException e) {}
    }
    private void processMessage(Integer id) {
        Resources r = getResources();
        statusLog(String.format(r.getString(R.string.message_accepted), id));
        JSONObject m = pendingOutgoing.get(id);

        if (m != null) {
            pendingOutgoing.remove(id);
            try {
                String text = m.getString("text");
                String phone = m.getString("phone");
                PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
                boolean validNumber = false;
                try {
                    Phonenumber.PhoneNumber numberProto = phoneUtil.parse(phone, "FI"); // TODO: user settings
                    validNumber = phoneUtil.isValidNumber(numberProto);
                } catch (NumberParseException e) {
                    logException(e);
                }
                if (validNumber) {

                    statusLog(String.format(r.getString(R.string.sending_message), phone, id, text));

                    boolean sentAlready = isSent(id.intValue());
                    if (!sentAlready) {
                        simpleLog("processMessage.send", "messageId", id);
                        smsUtils.sendSMS(getApplicationContext(), id.intValue(), phone, text);
                        markAsSent(id.intValue());
                    } else {
                        simpleLog("processMessage.alreadySent", "messageId", id);
                        Message bmsg = backend.handler.obtainMessage(BackendThread.MESSAGESENT);
                        bmsg.arg1 = id;
                        backend.handler.sendMessage(bmsg);
                        nextSend.set(new Date((new Date()).getTime()));


                    }
                } else {
                    statusLog(String.format(r.getString(R.string.invalid_phone_number), id, phone));

                    Message bmsg = backend.handler.obtainMessage(BackendThread.MESSAGEFAIL);
                    bmsg.arg1 = id;
                    bmsg.obj = "INVALID_PHONE_NUMBER";
                    backend.handler.sendMessage(bmsg);

                    nextSend.set(new Date((new Date()).getTime()));

                }
            } catch (Exception e) {
                logException(e);
                statusLog(String.format(r.getString(R.string.error_sending_message), e.toString()));
                Message msg = backend.handler.obtainMessage(BackendThread.MESSAGEFAIL);
                msg.arg1 = id;
                msg.obj = e.toString();
                backend.handler.sendMessage(msg);
                nextSend.set(new Date((new Date()).getTime() + 5000));

            }

        } else {
            simpleLog("processMessage.unknown", "messageId", id);

            statusLog(String.format(r.getString(R.string.unknown_message), id));
            nextSend.set(new Date());
        }

    }

    private void updateParams(JSONObject user) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("user", user.toString());
        editor.apply();

        View loginView = findViewById(R.id.loginWidget);
        loginView.setVisibility(View.GONE);

    }
    private void restoreLogin() {

        View loginView = findViewById(R.id.loginWidget);
        loginView.setVisibility(View.VISIBLE);

    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        prefs = getSharedPreferences("Prefs", 0);
        eula = new SimpleEula(this);
        eula.show();
        PackageInfo versionInfo = eula.getPackageInfo();
        simpleLog("onCreate", "version", versionInfo.versionCode);

        Thread.setDefaultUncaughtExceptionHandler(new DefaultExceptionHandler(this));
    }
    private void onLogin() {

        nextLoad.set(new Date());
        nextSend.set(new Date());

        if (smsUtils == null) {
            smsUtils = new SMSUtils(smsHandler, getApplicationContext());
            smsReceiver = new SMSReceiver(smsReceiverHandler);
            IntentFilter filter = new IntentFilter();
            filter.addAction("android.provider.Telephony.SMS_RECEIVED");
            registerReceiver(smsReceiver, filter);
        }

        timerHandler.removeCallbacks(timerRunnable);
        timerHandler.postDelayed(timerRunnable, 0);
    }

    public void onEulaAccepted() {

        setContentView(R.layout.activity_main);
        setupUI(findViewById(R.id.parent));

        TextView welcomeText = (TextView) findViewById(R.id.welcome);
        welcomeText.setMovementMethod(LinkMovementMethod.getInstance());

        String session = prefs.getString("session", "");
        if (session.isEmpty() == false) {

            try {
                updateParams(new JSONObject(prefs.getString("user", "{}")));

            } catch (JSONException e) {

            }
        }

        Handler backendHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                Resources r = getResources();
                switch(msg.what) {
                    case BackendThread.STARTED:
                        String session = prefs.getString("session", "");
                        if (session.isEmpty() == false) {
                            Message m = backend.handler.obtainMessage(BackendThread.SETSESSION);
                            m.obj = session;
                            backend.handler.sendMessage(m);

                            onLogin();

                        }


                        break;
                    case BackendThread.LOGINOK:
                        statusLog(r.getString(R.string.login_ok));
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putString("session", (String) msg.obj);
                        editor.apply();


                        nextLoad.set(new Date());
                        try {
                            updateParams(new JSONObject(prefs.getString("user", "{}")));

                        } catch (JSONException e) {

                        }
                        onLogin();


                        break;
                    case BackendThread.LOGINFAILED:
                        logUsage(newLogEvent("loginFailed"));
                        statusLog(r.getString(R.string.login_failed));
                        break;

                    case BackendThread.MESSAGES:
                        logUsage(newLogEvent("messages"));
                        acceptMessages((JSONArray) msg.obj);
                        break;
                    case BackendThread.ACCEPTOK:
                        simpleLog("acceptOk", "messageId", msg.arg1);
                        accepted.add(new Integer(msg.arg1));
                        break;
                    case BackendThread.ACCEPTFAILED:
                        simpleLog("acceptFailed", "messageId", msg.arg1);
                        statusLog(String.format(r.getString(R.string.accept_failed), msg.arg1));
                        break;
                    case BackendThread.SENTOK:
                        simpleLog("sentOk", "messageId", msg.arg1);
                        statusLog(String.format(r.getString(R.string.sent_ok), msg.arg1));
                        break;
                    case BackendThread.SENTFAILED:
                        simpleLog("sentFailed", "messageId", msg.arg1);
                        statusLog(String.format(r.getString(R.string.sent_failed), msg.arg1));
                        break;
                    case BackendThread.EXCEPTION:
                        logException((Exception) msg.obj);
                        statusLog(String.format(r.getString(R.string.exception), ((Exception) msg.obj).toString()));
                        break;
                    case BackendThread.FAILOK:
                        simpleLog("failOk", "messageId", msg.arg1);
                        statusLog(String.format(r.getString(R.string.fail_ok), msg.arg1));
                        break;
                    case BackendThread.FAILFAILED:
                        simpleLog("failFailed", "messageId", msg.arg1);
                        statusLog(String.format(r.getString(R.string.fail_failed), msg.arg1));
                        break;
                    case BackendThread.RECEIVEDOK:
                        simpleLog("receivedOk", "messageId", msg.arg1);
                        statusLog(String.format(r.getString(R.string.received_ok), msg.arg1));
                        pendingIncoming.remove(new Integer(msg.arg1));

                        break;
                    case BackendThread.RECEIVEDFAIL:
                        simpleLog("receivedFail", "messageId", msg.arg1);
                        statusLog(String.format(r.getString(R.string.received_failed), msg.arg1));
                        break;
                    case BackendThread.DELIVEREDOK:
                        simpleLog("deliveredOk", "messageId", msg.arg1);
                        statusLog(String.format(r.getString(R.string.delivered_ok), msg.arg1));
                        break;
                    case BackendThread.DELIVEREDFAILED:
                        simpleLog("deliveredFailed", "messageId", msg.arg1);
                        statusLog(String.format(r.getString(R.string.delivered_failed), msg.arg1));
                        break;
                    case BackendThread.USAGELOGSOK:
                        clearUsageLog();
                        break;
                    case BackendThread.USAGELOGSFAILED:
                        break;

                }
            }
        };
        backend = new BackendThread(backendHandler);
        backend.start();
        nextLoad.set(new Date());
        nextSend.set(new Date());


    }
    @Override
    public void onDestroy() {
        logUsage(newLogEvent("onDestroy"));
        timerHandler.removeCallbacks(timerRunnable);
        if (smsUtils != null) {
            smsUtils.unregister(getApplicationContext());
        }
        if (smsReceiver != null) {
            unregisterReceiver(smsReceiver);
        }
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        switch(item.getItemId()) {
            case R.id.logout:
                logUsage(newLogEvent("logout"));
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString("session", "");
                editor.apply();
                restoreLogin();
                backend.handler.sendEmptyMessage(BackendThread.LOGOUT);
                break;
        }

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}

package fi.feriko.sms2;

import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.util.Pair;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class BackendThread extends Thread {
    public Handler handler;
    public static final int LOGIN=1;
    public static final int LOGOUT=2;
    public static final int LOADMESSAGES=3;
    public static final int ACCEPTMESSAGE=4;
    public static final int MESSAGESENT=5;
    public static final int MESSAGEFAIL=6;
    public static final int MESSAGERECEIVED=7;
    public static final int MESSAGEDELIVERED=8;
    public static final int SETSESSION=9;
    public static final int SENDUSAGELOGS=10;

    public Handler ownerHandler;
    public static final int STARTED=0;
    public static final int LOGINOK=1;
    public static final int LOGINFAILED=2;
    public static final int LOGOUTOK=3;
    public static final int LOGOUTFAILED=4;
    public static final int MESSAGES=5;
    public static final int ACCEPTOK=6;
    public static final int ACCEPTFAILED=7;
    public static final int SENTOK=8;
    public static final int SENTFAILED=9;
    public static final int EXCEPTION=10;
    public static final int FAILOK=11;
    public static final int FAILFAILED=12;
    public static final int RECEIVEDOK=13;
    public static final int RECEIVEDFAIL=14;
    public static final int DELIVEREDOK=15;
    public static final int DELIVEREDFAILED=16;
    public static final int USAGELOGSOK=17;
    public static final int USAGELOGSFAILED=18;

    private static final String baseDomain = "sms.feriko.fi";
    private static final String baseURL = "https://" + baseDomain + "/";

    private CookieManager cookieManager = new CookieManager();
 //   private static final String baseDomain = "www.feriko.fi";
 //   private static final String baseURL = "https://" + baseDomain + "/dev/sms/";


    public BackendThread(Handler oHandler) {
        ownerHandler = oHandler;
        CookieHandler.setDefault(cookieManager);



    }
    private void setSession(String session) {
        CookieStore s = cookieManager.getCookieStore();
        HttpCookie cookie = new HttpCookie("_SESSION", session);
        cookie.setDomain(baseDomain);
        cookie.setMaxAge(1892159988);
        cookie.setPath("/");
        cookie.setVersion(0);
        cookie.setSecure(false);
        s.add(null, cookie);



    }

    private HttpURLConnection connect(String method, String route, ArrayList< Pair<String, String> > params, ArrayList<Pair<String, String> > headers, JSONObject json) throws IOException {

        URL url = null;
        String queryString = "";
        Uri.Builder builder = new Uri.Builder();
        if (params != null && params.isEmpty() == false) {
            for (Pair<String, String> param : params) {
                builder.appendQueryParameter(param.first, param.second);
            }
            queryString = builder.build().getEncodedQuery();

        }

        try {
            String extra = "";
            if (method == "GET")
                extra = "?" + queryString;

            url = new URL(baseURL + route + extra);

        } catch (MalformedURLException e) {
            Message msg = ownerHandler.obtainMessage(EXCEPTION);
            msg.obj = e;
            ownerHandler.sendMessage(msg);
            e.printStackTrace();
            return null;
        }
        HttpURLConnection conn = null;

        try {
            conn = (HttpURLConnection) url.openConnection();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        conn.setReadTimeout(10000);
        conn.setConnectTimeout(15000);
        if (headers != null && headers.isEmpty() == false) {
            for (Pair<String, String> header : headers) {
                conn.setRequestProperty(header.first, header.second);
            }
        }
        try {
            conn.setRequestMethod(method);
        } catch (ProtocolException e) {
            Message msg = ownerHandler.obtainMessage(EXCEPTION);
            msg.obj = e;
            ownerHandler.sendMessage(msg);
            e.printStackTrace();
            return null;
        }
        conn.setDoInput(true);
        if (method != "GET")
            conn.setDoOutput(true);
        if ((params != null || json != null) && method != "GET") {


            OutputStream os = conn.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));

            if (params != null)
                writer.write(queryString);
            else {
                writer.write(json.toString());
            }
            writer.flush();
            writer.close();
            os.close();
        }
        return conn;

    }
    private HttpURLConnection connect(String method, String route, ArrayList< Pair<String, String> > params, ArrayList< Pair <String, String> > headers) throws IOException {
        return connect(method, route, params, headers, null);
    }

    private HttpURLConnection connect(String method, String route, ArrayList< Pair<String, String> > params) throws IOException {
        return connect(method, route, params, null, null);
    }

    private HttpURLConnection connect(String method, String route) throws IOException {
        return connect(method, route, null, null, null);
    }
    public void login(String username, String password) {

        boolean ok = false;
        String session= "";
        try {
            ArrayList<Pair<String, String> > params = new ArrayList<Pair<String, String> >();
            params.add(new Pair<String, String>("username", username));
            params.add(new Pair<String,String>("password", password));

            ArrayList<Pair<String, String> > headers = new ArrayList<Pair<String, String> >();
            headers.add(new Pair<String, String>("Accept", "application/json"));
            HttpURLConnection conn = connect("POST", "backend/auth/page/hashdb/login", params, headers);
            int code = conn.getResponseCode();
            if (code == 200) {

                try {
                    List<HttpCookie> cookies = cookieManager.getCookieStore().get(new URI(baseURL));
                    for (HttpCookie cookie : cookies) {
                        if (cookie.getName().equals("_SESSION")) {
                            session = cookie.getValue();


                            ok = true;
                        }
                    }
                } catch (URISyntaxException e) {

                }

            }



        } catch (IOException e) {
            Message msg = ownerHandler.obtainMessage(EXCEPTION);
            msg.obj = e;
            ownerHandler.sendMessage(msg);
            e.printStackTrace();
        }


        Message msg = ownerHandler.obtainMessage(ok ? LOGINOK : LOGINFAILED);
        msg.obj = session;
        ownerHandler.sendMessage(msg);

    }
    public void logout() {
        try {
            ArrayList<Pair<String, String> > headers = new ArrayList<Pair<String, String> >();
            headers.add(new Pair<String, String>("Accept", "application/json"));
            HttpURLConnection conn = connect("POST", "backend/auth/logout", null, headers);
            int code = conn.getResponseCode();
            ownerHandler.sendEmptyMessage((code == 303) ? LOGOUTOK : LOGOUTFAILED);

        } catch (IOException e) {
            Message msg = ownerHandler.obtainMessage(EXCEPTION);
            msg.obj = e;
            ownerHandler.sendMessage(msg);
            e.printStackTrace();
        }
    }

    public void loadMessages() {
        try {
            HttpURLConnection conn = connect("GET", "backend/db/textmessagerecipientsqueue");

            JSONObject obj = new JSONObject(ConnectionUtils.receiveResponse(conn));
            JSONArray arr = obj.getJSONArray("result");
            Message msg = ownerHandler.obtainMessage(MESSAGES);
            msg.obj = arr;
            ownerHandler.sendMessage(msg);
        } catch (IOException e) {
            Message msg = ownerHandler.obtainMessage(EXCEPTION);
            msg.obj = e;
            ownerHandler.sendMessage(msg);
            e.printStackTrace();
        } catch (JSONException e) {

            Message msg = ownerHandler.obtainMessage(EXCEPTION);
            msg.obj = e;
            ownerHandler.sendMessage(msg);
            e.printStackTrace();
        }
    }
    public void acceptMessage(int id) {
        boolean ok = false;
        try {
            String url = "backend/db/textmessagerecipients/" + Integer.toString(id) + "/accept";
            HttpURLConnection conn = connect("POST", url);

            int code = conn.getResponseCode();
            if (code == 200)
                ok = true;



        } catch (IOException e) {
            Message msg = ownerHandler.obtainMessage(EXCEPTION);
            msg.obj = e;
            ownerHandler.sendMessage(msg);
            e.printStackTrace();
        }
        Message msg = ownerHandler.obtainMessage(ok ? ACCEPTOK : ACCEPTFAILED);
        msg.arg1 = id;
        ownerHandler.sendMessage(msg);
    }


    public void confirmSend(int id) {
        boolean ok = false;
        try {
            String url = "backend/db/textmessagerecipients/" + Integer.toString(id) + "/sent";
            HttpURLConnection conn = connect("POST", url);

            int code = conn.getResponseCode();
            if (code == 200)
                ok = true;

        } catch (IOException e) {
            Message msg = ownerHandler.obtainMessage(EXCEPTION);
            msg.obj = e;
            ownerHandler.sendMessage(msg);
            e.printStackTrace();
        }
        Message msg = ownerHandler.obtainMessage(ok ? SENTOK: SENTFAILED);
        msg.arg1 = id;
        ownerHandler.sendMessage(msg);
    }

    public void confirmDelivery(int id) {
        boolean ok = false;
        try {
            String url = "backend/db/textmessagerecipients/" + Integer.toString(id) + "/delivered";
            HttpURLConnection conn = connect("POST", url);

            int code = conn.getResponseCode();
            if (code == 200)
                ok = true;

        } catch (IOException e) {
            Message msg = ownerHandler.obtainMessage(EXCEPTION);
            msg.obj = e;
            ownerHandler.sendMessage(msg);
            e.printStackTrace();
        }
        Message msg = ownerHandler.obtainMessage(ok ? DELIVEREDOK: DELIVEREDFAILED);
        msg.arg1 = id;
        ownerHandler.sendMessage(msg);
    }

    public void failMessage(int id, String reason) {
        boolean ok = false;
        try {
            String url = "backend/db/textmessagerecipients/" + Integer.toString(id) + "/fail";

            JSONObject o = new JSONObject();

            o.put("reason", reason);
            HttpURLConnection conn = connect("POST", url, null, null, o);
            int code = conn.getResponseCode();
            if (code == 200)
                ok = true;
        } catch (Exception e) {
            Message msg = ownerHandler.obtainMessage(EXCEPTION);
            msg.obj = e;
            ownerHandler.sendMessage(msg);
            e.printStackTrace();
        }

        Message msg = ownerHandler.obtainMessage(ok ? FAILOK : FAILFAILED);
        msg.arg1 = id;
        ownerHandler.sendMessage(msg);
    }

    public void receiveMessage(int id, Pair<String ,String> m) {
        boolean ok = false;
        try {

            String url = "backend/db/incomingtextmessages";

            JSONObject o = new JSONObject();

            o.put("phone", m.first);
            o.put("text", m.second);
            HttpURLConnection conn = connect("POST", url, null, null, o);

            int code = conn.getResponseCode();
            if (code == 200)
                ok = true;
        } catch (IOException e) {
            Message msg = ownerHandler.obtainMessage(EXCEPTION);
            msg.obj = e;
            ownerHandler.sendMessage(msg);
            e.printStackTrace();
        } catch (JSONException e) {
            Message msg = ownerHandler.obtainMessage(EXCEPTION);
            msg.obj = e;
            ownerHandler.sendMessage(msg);
            e.printStackTrace();
        }

        Message msg = ownerHandler.obtainMessage(ok ? RECEIVEDOK : RECEIVEDFAIL);
        msg.arg1 = id;
        ownerHandler.sendMessage(msg);
    }
    private void sendUsageLogs(JSONArray logs) {
        boolean ok = false;
        try {
            JSONObject o = new JSONObject();
            o.put("data", logs.toString());
            String url = "backend/db/usagelogs";
            HttpURLConnection conn = connect("POST", url, null, null, o);
            int code = conn.getResponseCode();
            if (code == 200)
                ok = true;
        } catch (Exception e) {
            Message msg = ownerHandler.obtainMessage(EXCEPTION);
            msg.obj = e;
            ownerHandler.sendMessage(msg);
            e.printStackTrace();
        }
        ownerHandler.sendEmptyMessage(ok ? USAGELOGSOK : USAGELOGSFAILED);
    }
    @Override
    public void run() {
        Looper.prepare();

        handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {

                switch (msg.what) {
                    case LOGIN:
                        Pair<String, String> p = (Pair<String, String>) msg.obj;
                        login(p.first, p.second);
                        break;
                    case LOGOUT:
                        logout();
                        break;
                    case LOADMESSAGES:
                        loadMessages();
                        break;
                    case ACCEPTMESSAGE:
                        acceptMessage(msg.arg1);
                        break;
                    case MESSAGESENT:
                        confirmSend(msg.arg1);
                        break;
                    case MESSAGEFAIL:
                        failMessage(msg.arg1, (String) msg.obj);
                        break;
                    case MESSAGERECEIVED:
                        receiveMessage(msg.arg1, (Pair<String, String>) msg.obj);
                        break;
                    case MESSAGEDELIVERED:
                        confirmDelivery(msg.arg1);
                        break;
                    case SETSESSION:
                        setSession((String) msg.obj);
                        break;
                    case SENDUSAGELOGS:
                        sendUsageLogs((JSONArray) msg.obj);
                        break;

                }
            }
        };
        ownerHandler.sendEmptyMessage(STARTED);

        Looper.loop();
    }

}

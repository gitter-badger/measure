// Copyright 2015, Blackbuck Computing Inc. and/or its subsidiaries

package mobi.blackbuck.measure;

import android.app.IntentService;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.provider.Settings.Secure;
import android.support.v4.content.LocalBroadcastManager;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.CellSignalStrength;
import android.telephony.CellSignalStrengthGsm;
import android.telephony.TelephonyManager;
import android.test.suitebuilder.annotation.LargeTest;
import android.util.JsonWriter;
import android.util.Log;

import com.facebook.network.connectionclass.ConnectionClassManager;
import com.facebook.network.connectionclass.ConnectionQuality;
import com.facebook.network.connectionclass.DeviceBandwidthSampler;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.CharArrayWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p/>
 * TODO: Customize class - update intent actions, extra parameters and static
 * helper methods.
 */
public class MeasurementService extends IntentService implements ConnectionClassManager.ConnectionClassStateChangeListener {
    // Actions that an IntentService can perform, e.g. ACTION_FETCH_NEW_ITEMS
    public static final String ACTION_LATENCY = "mobi.blackbuck.fastah_measure.action.LATENCY";
    public static final String ACTION_BANDWIDTH = "mobi.blackbuck.fastah_measure.action.BW";

    // Parameters related to an action
    private static final String EXTRA_PARAM1 = "mobi.blackbuck.fastah_measure.extra.PARAM1";
    private static final String EXTRA_PARAM2 = "mobi.blackbuck.fastah_measure.extra.PARAM2";

    public static final String EXTENDED_DATA_RUNSTATUS = "mobi.blackbuck.fastah_measure.data.RUNSTATUS";

    public static final String BROADCAST_ACTION_LATRUN_PROGRESS = "mobi.blackbuck.fastah_measure.action.LATRUNPROGRESS";
    public static final String EXTENDED_DATA_LAT_PACKETLOSS = "mobi.blackbuck.fastah_measure.data.LAT_PACKETLOSS";
    public static final String EXTENDED_DATA_LAT_CLOCKSKEW = "mobi.blackbuck.fastah_measure.data.LAT_CLOCKSKEW";

    public static final String BROADCAST_ACTION_BWRUN_COMPLETED = "mobi.blackbuck.fastah_measure.action.BWRUNCOMPLETED";
    public static final String EXTENDED_DATA_BW_KBPS = "mobi.blackbuck.fastah_measure.data.BW_KBPS";
    public static final String EXTENDED_DATA_BW_QUAL = "mobi.blackbuck.fastah_measure.data.BW_QUAL";


    public static final String BROADCAST_ACTION_PROGRESS_1 = "mobi.blackbuck.fastah_measure.action.PROGRESS_1";
    public static final String EXTENDED_DATA_PROGRESS1_NETWORKNAME = "mobi.blackbuck.fastah_measure.data.NETWORKNAME";
    public static final String EXTENDED_DATA_PROGRESS1_BEARERDESC = "mobi.blackbuck.fastah_measure.data.BEARERDESC";
    public static final String EXTENDED_DATA_PROGRESS1_SIGNALSTRENGTH = "mobi.blackbuck.fastah_measure.data.SIGNALSTRENGTH";


    private static final String BW_PROBE_URL = "http://builder.blackbuck.mobi:8443/static/m100_hubble_4060_%d.jpg";

    private static final int MAX_UDP_LATENCY_PACKETS = 200;
    private final HashMap<Integer, LatPacketInfo> mLatTestData = new HashMap<Integer, LatPacketInfo>(MAX_UDP_LATENCY_PACKETS);
    private ConnectionQuality mBwOutcome = ConnectionQuality.UNKNOWN;

    private SecureRandom mSr = new SecureRandom();

    private class LatPacketInfo {
        public Integer seq = -1;
        public long cTimestamp = 0;
        public long sTimestamp = 0;
        public long cRecvTimestamp = 0;
        public long serverReportedClientDrift = 0;
        public LatPacketInfo (Integer sequence, long cTimestamp) {
            this.seq = sequence;
            this.cTimestamp = cTimestamp;
        }
    }

    public class ConnMeta {
        public final String androidId = Secure.ANDROID_ID;
        public String networkType;
        public final boolean nitzOn = Settings.Global.AUTO_TIME.equals("1") && Settings.Global.AUTO_TIME_ZONE.equals("1");
        public boolean isOnline = false;
        public boolean isWifiConn = false;
        public boolean isMobileConn = false;
        public String wiFiApName = "";
        public String operatorName = "";
        public String operatorSignal = "";
    }

    @Override
    public void onBandwidthStateChange(ConnectionQuality bandwidthState) {
        mBwOutcome = bandwidthState;
        Log.d("MS", "FB tool BW = " + bandwidthState.toString());
    }

    /**
     * Starts this service to perform action Latency measurement with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    // TODO: Customize helper method
    public static void startActionMeasureLatency(Context context, String param1, String param2) {
        Intent intent = new Intent(context, MeasurementService.class);
        intent.setAction(ACTION_LATENCY);
        intent.putExtra(EXTRA_PARAM1, param1);
        intent.putExtra(EXTRA_PARAM2, param2);
        context.startService(intent);
    }

    /**
     * Starts this service to perform action Bandwidth measurement with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    // TODO: Customize helper method
    public static void startActionMeasureBandwidth(Context context, String param1, String param2) {
        Intent intent = new Intent(context, MeasurementService.class);
        intent.setAction(ACTION_BANDWIDTH);
        intent.putExtra(EXTRA_PARAM1, param1);
        intent.putExtra(EXTRA_PARAM2, param2);
        context.startService(intent);
    }

    public MeasurementService() {
        super("MeasurementService");
    }

    private ConnMeta updateClientConnectivity() {
        // Quick check of current network conditions.
        ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connMgr.getActiveNetworkInfo();
        ConnMeta meta = new ConnMeta();
        if ( activeNetworkInfo != null ) {
            meta.isOnline = activeNetworkInfo.isConnected();
            meta.isWifiConn = activeNetworkInfo.getType() == ConnectivityManager.TYPE_WIFI;
            meta.isMobileConn = activeNetworkInfo.getType() == ConnectivityManager.TYPE_MOBILE;
            meta.networkType = activeNetworkInfo.getTypeName();
        } else {
            return meta;
        }
        WifiManager wiFiMgr = (WifiManager)getSystemService(Context.WIFI_SERVICE);
        if (wiFiMgr != null && wiFiMgr.isWifiEnabled() && meta.isWifiConn) {
            meta.wiFiApName = wiFiMgr.getConnectionInfo().getSSID();
        }

        if (meta.isMobileConn) {
            TelephonyManager teleMgr = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
            if (teleMgr != null && teleMgr.getDataState() == TelephonyManager.DATA_CONNECTED) {
                meta.operatorName = teleMgr.getNetworkOperatorName();
                // Much ado about finding signal strength follows. Yikes.
                try {
                    List<CellInfo> lci = teleMgr.getAllCellInfo();
                    if (lci != null && lci.size() > 0) {
                        CellInfo ci = lci.get(0);
                        int cs = CellSignalStrength.SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
                        if (ci instanceof CellInfoGsm) {
                            cs = ((CellInfoGsm) ci).getCellSignalStrength().getLevel();
                        }
                        if (ci instanceof CellInfoCdma) {
                            cs = ((CellInfoCdma) ci).getCellSignalStrength().getLevel();
                        }
                        if (ci instanceof CellInfoLte) {
                            cs = ((CellInfoGsm) ci).getCellSignalStrength().getLevel();
                        }
                        if (ci instanceof CellInfoWcdma) {
                            cs = ((CellInfoWcdma) ci).getCellSignalStrength().getLevel();
                        }
                        switch (cs) {
                            case CellSignalStrength.SIGNAL_STRENGTH_NONE_OR_UNKNOWN:
                                meta.operatorSignal = "";
                                break;
                            case CellSignalStrength.SIGNAL_STRENGTH_POOR:
                                meta.operatorSignal = "Poor";
                                break;
                            case CellSignalStrength.SIGNAL_STRENGTH_MODERATE:
                                meta.operatorSignal = "Moderate";
                                break;
                            case CellSignalStrength.SIGNAL_STRENGTH_GOOD:
                                meta.operatorSignal = "Good";
                                break;
                            case CellSignalStrength.SIGNAL_STRENGTH_GREAT:
                                meta.operatorSignal = "Great";
                                break;
                        }
                    }
                } catch (Exception e) {
                }
            }
        }

        return meta;
    }

    /**
     * The IntentService calls this method from the default worker thread with
     * the intent that started the service. When this method returns, IntentService
     * stops the service, as appropriate.
     */
    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d("MS", "OnHandleIntent");

        ConnMeta connMeta = new ConnMeta();
        connMeta = updateClientConnectivity();

        if (!connMeta.isOnline) {
            // Return failure due to offline
            cleanupLatencyTest(false, null, null);
        }

        // Figure out what test was requested
        if (intent != null) {
            final String action = intent.getAction();
            Log.d("MS", action);
            broadcastUpdate("progress", false, null, connMeta);
            // Do latency test
            handleActionLatency("builder.blackbuck.mobi", 500, connMeta);
            // Do BW test
            handleActionBandwidth(connMeta);
        }
    }

    /**
     * Handle action Latency test in the provided background thread with the provided
     * parameters.
     */
    private void handleActionLatency(String testServerHost, int testServerPort, ConnMeta meta) {
        boolean testOk = false;
        byte[] buffer = new byte[1500];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        DatagramSocket socket = null;

        byte[] random = new byte[Long.SIZE];
        mSr.nextBytes(random);
        ByteBuffer wrapped = ByteBuffer.wrap(random); // big-endian by default
        long testId = wrapped.getLong();

        InetAddress rh = null;
        try {
            rh = InetAddress.getByName(testServerHost);
        } catch (UnknownHostException e) {
            e.printStackTrace();
            cleanupLatencyTest(testOk, null, socket);
            return;
        }

        try {
            socket = new DatagramSocket();
            socket.setSoTimeout(5000); // 5 second read timeouts
        } catch (SocketException e) {
            e.printStackTrace();
            cleanupLatencyTest(testOk, null, socket);
            return;
        }

        mLatTestData.clear();
        socket.connect(rh, testServerPort);

        // Send ten simple packets and check for the expected responses.
        for (short i = 1; i <= MAX_UDP_LATENCY_PACKETS; i++) {
            LatPacketInfo lpi = messageToPacket(testId, i, packet);
            try {
                socket.send(packet);
                Log.d("MS", "Successful UDP send with payload len = " + packet.getLength());
                mLatTestData.put(Integer.valueOf(i), lpi);
            } catch (IOException e) {
                // We continue attempting to send outgoing packet despite errors
                e.printStackTrace();
            }
        }

        // Receive packets for responses
        int nServerAcks = 0;
        int readAttempts = 0;
        long totalRcvWaitTime = 0;
        Log.d("MS", "Starting UDP recv loop");
        final long startTimeRecv = SystemClock.elapsedRealtime();
        while ( readAttempts++ < MAX_UDP_LATENCY_PACKETS && (totalRcvWaitTime < 10 * 1000) ) {
            byte[] incomingBuf = new byte[1500];
            DatagramPacket incomingPkt = new DatagramPacket(incomingBuf, incomingBuf.length);
            try {
                Log.d("MS", "Read attempt " + String.valueOf(readAttempts));
                socket.receive(incomingPkt);
                try {
                    JSONObject jo = new JSONObject(new String(incomingPkt.getData()));
                    int seq  = jo.getInt("seq");
                    Log.d("MS", "JSON parsed sequence Id = " + seq);
                    LatPacketInfo val = mLatTestData.get(Integer.valueOf(seq));
                    if (val != null ) {
                        val.sTimestamp = jo.getLong("lTime");
                        val.serverReportedClientDrift = jo.getLong("cDrift");
                        val.cRecvTimestamp = System.currentTimeMillis();
                        nServerAcks++;
                    }
                } catch (JSONException e) {
                    Log.d("MS", "JSON parsing error " + e.toString());
                }
            } catch (IOException e) {
                Log.d("MS", "Failed UDP recv at attempt " + readAttempts + ". Error = " + e.toString());
            } finally {
                totalRcvWaitTime = (SystemClock.elapsedRealtime() - startTimeRecv);
                Log.d("MS", "Read totalRcvWaitTime = " + String.valueOf(totalRcvWaitTime));
            }
        }
        Log.d("MS", "Finished UDP recv with " + nServerAcks + " OK reads, attempts = " + String.valueOf(readAttempts) + ", elapsed clock time = " + totalRcvWaitTime / (1000));
        TestResults lr = calculateLatStats();
        if (readAttempts > 0 ) {
            testOk = true;
        }
        // Signal success/failure + measurements to receivers
        cleanupLatencyTest(testOk, lr, socket);

    }

    // Broadcast progress/result of test
    private void broadcastUpdate(String testType, boolean completedOk, TestResults lr, ConnMeta connMeta) {

        if (testType.equalsIgnoreCase("latency")) {
            // Puts the status into the Intent and broadcast it
            Intent resultIntent = new Intent(MeasurementService.BROADCAST_ACTION_LATRUN_PROGRESS);
            resultIntent.putExtra(MeasurementService.EXTENDED_DATA_RUNSTATUS, completedOk);
            // Actual measurements that the UI may want to show
            if (lr != null) {
                resultIntent.putExtra(MeasurementService.EXTENDED_DATA_LAT_PACKETLOSS, lr.packetLoss);
                resultIntent.putExtra(MeasurementService.EXTENDED_DATA_LAT_CLOCKSKEW, lr.clockSkewCS);
            }
            // Broadcasts the Intent to receivers in this app.
            LocalBroadcastManager.getInstance(this).sendBroadcast(resultIntent);
        }

        if (testType.equalsIgnoreCase("bandwidth")) {
            // Puts the status into the Intent and broadcast it
            Intent resultIntent = new Intent(MeasurementService.BROADCAST_ACTION_BWRUN_COMPLETED);
            resultIntent.putExtra(MeasurementService.EXTENDED_DATA_RUNSTATUS, completedOk);
            // Actual measurements that the UI may want to show
            if (lr != null) {
                resultIntent.putExtra(MeasurementService.EXTENDED_DATA_BW_KBPS, lr.bandwidthKbps);
                resultIntent.putExtra(MeasurementService.EXTENDED_DATA_BW_QUAL, lr.bandwidth);
            }
            // Broadcasts the Intent to receivers in this app.
            LocalBroadcastManager.getInstance(this).sendBroadcast(resultIntent);
        }

        if (testType.equalsIgnoreCase("progress") && connMeta != null ) {
            Intent resultIntent = new Intent(MeasurementService.BROADCAST_ACTION_PROGRESS_1);
            String networkName = connMeta.wiFiApName.equalsIgnoreCase("") ? connMeta.operatorName : connMeta.wiFiApName;
            resultIntent.putExtra(MeasurementService.EXTENDED_DATA_PROGRESS1_NETWORKNAME, networkName);
            resultIntent.putExtra(MeasurementService.EXTENDED_DATA_PROGRESS1_BEARERDESC, connMeta.networkType);
            resultIntent.putExtra(MeasurementService.EXTENDED_DATA_PROGRESS1_SIGNALSTRENGTH, connMeta.operatorSignal);
            // Broadcasts the Intent to receivers in this app.
            LocalBroadcastManager.getInstance(this).sendBroadcast(resultIntent);
        }
    }

    // Cleans up network socket and then calls @broadcastupdate()
    private void cleanupLatencyTest(boolean completedOk, TestResults lr, DatagramSocket socket) {
        if (socket != null) {
            socket.disconnect();
            socket.close();
        }
        broadcastUpdate("latency", completedOk, lr, null);
    }

    /**
     * Converts a given String into a datagram packet.
     */
    private LatPacketInfo messageToPacket(long testId, short sequence, DatagramPacket packet) {
        StringWriter sw = new StringWriter(1500);
        JsonWriter writer = new JsonWriter(sw);
        long unixTime = System.currentTimeMillis();
        try {
            writer.beginObject();
            writer.name("v").value(Short.valueOf((short) 1));
            writer.name("testId").value(testId);
            writer.name("seq").value(Short.valueOf(sequence));
            writer.name("lTime").value(unixTime);
            writer.endObject();
        } catch (IOException e) {
            e.printStackTrace();
        }
        byte[] bytes = sw.toString().getBytes();
        Log.d("MS", "Serialized message:" + sw.toString());
        System.arraycopy(bytes, 0, packet.getData(), 0, bytes.length);
        packet.setLength(bytes.length);

        return new LatPacketInfo(Integer.valueOf(sequence), unixTime);
    }

    public class TestResults {
        public float packetLoss;
        public long clockSkewCS;
        public long latency;
        public String bandwidth = "";
        public double bandwidthKbps = 0.0;

    }

    private TestResults calculateLatStats() {
        long minT1 = Long.MAX_VALUE;
        long minT2 = Long.MAX_VALUE;
        int nServerResponses = 0;
        for (HashMap.Entry<Integer, LatPacketInfo> entry : mLatTestData.entrySet())
        {
            LatPacketInfo lpi = entry.getValue();
            if (lpi.cRecvTimestamp > 0) {
                nServerResponses++;
                //Log.d("MS", "Seq " + entry.getKey().toString() + ", Meta = " + lpi.cTimestamp + ", " + lpi.sTimestamp);
                // Difference between local receive time and server-inserted timestamp
                long t1 = lpi.cRecvTimestamp - lpi.sTimestamp;
                minT1 = Math.min(minT1, Math.abs(t1));
                // Log.d("MS", "T1 " + t1 + ", MinT1 = " + minT1);
                long t2 = lpi.serverReportedClientDrift;
                minT2 = Math.min(minT2, Math.abs(t2));
                // Log.d("MS", "T2 " + t2 + ", MinT2 = " + minT2);
            }
        }

        float packetLossRate = 1.0F - ((float)nServerResponses/MAX_UDP_LATENCY_PACKETS); // fraction of packets lost along the way
        long clientServerClockDiff = (minT2 - minT1)/2;

        TestResults lr = new TestResults();
        lr.packetLoss = packetLossRate;
        lr.clockSkewCS = clientServerClockDiff;
        lr.latency = 0;

        Log.d("MS", "packetLossRate " + packetLossRate + ", clientServerClockDiff = " + clientServerClockDiff);

        return lr;
    }
    /**
     * Handle action BW Test in the provided background thread with the provided
     * parameters.
     */
    private void handleActionBandwidth(ConnMeta meta) {
        final int MAX_DOWNLOADS = 5;
        ConnectionClassManager.getInstance().register(this);
        DeviceBandwidthSampler.getInstance().startSampling();
        URLConnection connection = null;
        int nBytesRead = -1;
        int nDownloads = 0;
        boolean completedOk = false;

        while (mBwOutcome.equals(ConnectionQuality.UNKNOWN)  && nDownloads < MAX_DOWNLOADS) {
            try {

                URL url = new URL(String.format(BW_PROBE_URL, nDownloads));
                // Open a stream to download the image from our URL.
                connection = url.openConnection();
                connection.setUseCaches(false);
                connection.connect();
                Log.e("MS", "Starting download for " + connection.getURL().toString());
                InputStream input = connection.getInputStream();
                try {
                    byte[] buffer = new byte[1024];
                    // Do some busy waiting while the stream is open.
                    while (input.read(buffer) != -1) {
                        if (!mBwOutcome.equals(ConnectionQuality.UNKNOWN)) {
                            break;  // If onBandwidthStateChange() did update us with an outcome, stop downloading.
                        }
                    }
                    completedOk = true;
                } finally {
                    input.close();
                }
            } catch (IOException e) {
                Log.e("MS", "Error while downloading image.");
            }
            nDownloads++;
        }
        DeviceBandwidthSampler.getInstance().stopSampling();

        TestResults bwResult = new TestResults();
        bwResult.bandwidthKbps  = ConnectionClassManager.getInstance().getDownloadKBitsPerSecond();
        bwResult.bandwidth =  mBwOutcome.name();
        Log.e("MS", "Bandwidth measured as " + bwResult.bandwidthKbps + " kbps, " + bwResult.bandwidth + ".");
        broadcastUpdate("bandwidth", completedOk, bwResult, meta);

    }
}

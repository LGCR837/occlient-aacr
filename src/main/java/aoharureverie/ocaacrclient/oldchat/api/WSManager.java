package aoharureverie.ocaacrclient.oldchat.api;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import aoharureverie.ocaacrclient.oldchat.OldChatApplication;
import aoharureverie.ocaacrclient.oldchat.util.CryptoUtil;
import aoharureverie.ocaacrclient.oldchat.util.NetworkStateManager;
import org.json.JSONObject;
import java.net.URI;
import java.net.URLEncoder;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class WSManager {
    public interface Listener {
        void onDirectMessage(WSModels.DirectMessage message);
        void onDirectRead(String threadId, String readerUid, long readAt);
        void onDirectRecall(WSModels.DirectRecall recall);
        void onGroupMessage(WSModels.GroupMessage message);
        void onGroupRecall(WSModels.GroupRecall recall);
        void onTyping(WSModels.TypingEvent event);
        void onConnectionChanged(boolean connected);
    }

    private static final String AUTH_PREFS = "auth";
    private static final long RECONNECT_BASE_MS = 1000;
    private static final long RECONNECT_MAX_MS = 60000;
    private static WSManager instance;
    private final Set<Listener> listeners = new CopyOnWriteArraySet<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final WSUnreadSyncHandler unreadSyncHandler = new WSUnreadSyncHandler();
    private final WSIncomingHandler incomingHandler = new WSIncomingHandler();
    private final WSIncomingHandler.Callback incomingCallback = new WSIncomingHandler.Callback() {
        @Override
        public void onDirectMessage(WSModels.DirectMessage message) {
            WSDispatchHelper.dispatchDirectMessage(mainHandler, listeners, message);
        }

        @Override
        public void onDirectRead(String threadId, String readerUid, long readAt) {
            WSDispatchHelper.dispatchDirectRead(mainHandler, listeners, threadId, readerUid, readAt);
        }

        @Override
        public void onDirectRecall(WSModels.DirectRecall recall) {
            WSDispatchHelper.dispatchDirectRecall(mainHandler, listeners, recall);
        }

        @Override
        public void onGroupMessage(WSModels.GroupMessage message) {
            WSDispatchHelper.dispatchGroupMessage(mainHandler, listeners, message);
        }

        @Override
        public void onGroupRecall(WSModels.GroupRecall recall) {
            WSDispatchHelper.dispatchGroupRecall(mainHandler, listeners, recall);
        }

        @Override
        public void onTyping(WSModels.TypingEvent event) {
            WSDispatchHelper.dispatchTyping(mainHandler, listeners, event);
        }
    };
    private final WSUnreadSyncHandler.DirectMessageDispatcher directUnreadDispatcher =
            new WSUnreadSyncHandler.DirectMessageDispatcher() {
                @Override
                public void dispatch(WSModels.DirectMessage message) {
                    WSDispatchHelper.dispatchDirectMessage(mainHandler, listeners, message);
                }
            };
    private final WSUnreadSyncHandler.GroupMessageDispatcher groupUnreadDispatcher =
            new WSUnreadSyncHandler.GroupMessageDispatcher() {
                @Override
                public void dispatch(WSModels.GroupMessage message) {
                    WSDispatchHelper.dispatchGroupMessage(mainHandler, listeners, message);
                }
            };
    private SimpleWebSocketClient client;
    private boolean connecting = false;
    private boolean connected = false;
    private boolean needsUnreadSync = false;
    private boolean checkingToken = false;
    private boolean authRefreshing = false;
    private int reconnectAttempts = 0;

    public static synchronized WSManager getInstance() {
        if (instance == null) {
            instance = new WSManager();
        }
        return instance;
    }

    public void addListener(Listener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public boolean isConnected() {
        return connected;
    }

    public void start(Context context) {
        if (context == null) {
            return;
        }
        final Context appContext = context.getApplicationContext();
        final String token = getToken(appContext);
        if (token == null || token.isEmpty()) {
            return;
        }
        if (connected || connecting) {
            syncUnread(appContext, token);
            syncGroupUnread(appContext, token);
            return;
        }
        if (checkingToken) {
            return;
        }
        checkingToken = true;
        HttpUtil.get("/me", token, new HttpUtil.Callback() {
            @Override
            public void onSuccess(String response) {
                checkingToken = false;
                String freshToken = getToken(appContext);
                if (freshToken == null || freshToken.isEmpty()) {
                    return;
                }
                syncUnread(appContext, freshToken);
                syncGroupUnread(appContext, freshToken);
                connect(freshToken);
            }

            @Override
            public void onError(int code, String error) {
                checkingToken = false;
                syncUnread(appContext, token);
                syncGroupUnread(appContext, token);
                connect(token);
            }
        });
    }

    public void stop() {
        if (client != null) {
            client.close();
            client = null;
        }
        connected = false;
        connecting = false;
    }

    public void connect(String token) {
        if (token == null || token.isEmpty()) {
            return;
        }
        final String wsToken = token;
        if (connected || connecting) {
            return;
        }
        boolean needSession = CryptoUtil.isEcdhSupported();
        if (needSession && !CryptoUtil.hasSession()) {
            connecting = true;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final boolean ok = HttpUtil.ensureSession();
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            connecting = false;
                            if (ok) {
                                connect(wsToken);
                            } else {
                                scheduleReconnect();
                            }
                        }
                    });
                }
            }, "ws-handshake").start();
            return;
        }
        connecting = true;
        try {
            String wsUrl = buildWsUrl(wsToken);
            client = new SimpleWebSocketClient(new URI(wsUrl), new SimpleWebSocketClient.Listener() {
                @Override
                public void onOpen() {
                    connecting = false;
                    connected = true;
                    reconnectAttempts = 0;
                    NetworkStateManager.getInstance().recordRequestSuccess();
                    WSDispatchHelper.notifyConnection(mainHandler, listeners, true);
                    if (needsUnreadSync) {
                        Context ctx = OldChatApplication.getAppContext();
                        if (ctx != null) {
                            String token = getToken(ctx);
                            if (token != null) {
                                syncUnread(ctx, token);
                                syncGroupUnread(ctx, token);
                            }
                        }
                        needsUnreadSync = false;
                    }
                }

                @Override
                public void onMessage(String message) {
                    Context ctx = OldChatApplication.getAppContext();
                    incomingHandler.handleMessage(ctx, message, incomingCallback);
                }

                @Override
                public void onClose(int code, String reason) {
                    connected = false;
                    connecting = false;
                    CryptoUtil.clearSession();
                    NetworkStateManager.getInstance().recordRequestFailure(-1);
                    WSDispatchHelper.notifyConnection(mainHandler, listeners, false);
                    if (!authRefreshing) {
                        scheduleReconnect();
                    }
                }

                @Override
                public void onError(Exception ex) {
                    connected = false;
                    connecting = false;
                    CryptoUtil.clearSession();
                    NetworkStateManager.getInstance().recordRequestFailure(-1);
                    WSDispatchHelper.notifyConnection(mainHandler, listeners, false);

                    String msg = ex.getMessage();
                    if (msg != null && (msg.contains("403") || msg.contains("401"))) {
                        authRefreshing = true;
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                final String newToken = HttpAuthHelper.refreshAccessToken();
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        authRefreshing = false;
                                        if (newToken != null && !newToken.isEmpty()) {
                                            reconnectAttempts = 0;
                                            connect(newToken);
                                        } else {
                                            scheduleReconnect();
                                        }
                                    }
                                });
                            }
                        }, "ws-auth-refresh").start();
                    } else {
                        scheduleReconnect();
                    }
                }
            });
            client.connect();
        } catch (Exception e) {
            connecting = false;
        }
    }

    public void syncUnread(Context context, String token) {
        try {
            JSONObject json = new JSONObject();
            json.put("limit", 50);
            final Context appContext = context == null ? null : context.getApplicationContext();
            HttpUtil.post("/direct/unread", json, token, new HttpUtil.Callback() {
                @Override
                public void onSuccess(String response) {
                    unreadSyncHandler.handleDirectUnread(appContext, response, directUnreadDispatcher);
                }

                @Override
                public void onError(int code, String error) {
                }
            });
        } catch (Exception e) {
        }
    }

    public void syncGroupUnread(Context context, String token) {
        try {
            JSONObject json = new JSONObject();
            json.put("limit", 50);
            final Context appContext = context == null ? null : context.getApplicationContext();
            HttpUtil.post("/groups/unread", json, token, new HttpUtil.Callback() {
                @Override
                public void onSuccess(String response) {
                    unreadSyncHandler.handleGroupUnread(appContext, response, groupUnreadDispatcher);
                }

                @Override
                public void onError(int code, String error) {
                }
            });
        } catch (Exception e) {
        }
    }

    private void scheduleReconnect() {
        needsUnreadSync = true;
        long delay = Math.min(RECONNECT_BASE_MS * (1L << reconnectAttempts), RECONNECT_MAX_MS);
        reconnectAttempts++;
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Context ctx = OldChatApplication.getAppContext();
                if (ctx == null) {
                    return;
                }
                String token = getToken(ctx);
                if (token == null || token.isEmpty()) {
                    return;
                }
                connect(token);
            }
        }, delay);
    }

    private String buildWsUrl(String token) throws Exception {
        String base = HttpUtil.BASE_URL;
        String wsBase;
        if (base.startsWith("https://")) {
            wsBase = "wss://" + base.substring("https://".length());
        } else if (base.startsWith("http://")) {
            wsBase = "ws://" + base.substring("http://".length());
        } else {
            wsBase = base;
        }
        StringBuilder query = new StringBuilder();
        query.append("token=").append(URLEncoder.encode(token, "UTF-8"));
        String sessionId = CryptoUtil.getSessionId();
        if (sessionId != null && sessionId.length() > 0) {
            query.append("&sid=").append(URLEncoder.encode(sessionId, "UTF-8"));
        }
        return wsBase + "/ws?" + query;
    }

    private String getToken(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE);
        return prefs.getString("access_token", "");
    }
}

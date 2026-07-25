package aoharureverie.ocaacrclient.oldchat.api;

import android.os.Handler;

import java.util.Set;

final class WSDispatchHelper {
    private WSDispatchHelper() {
    }

    static void dispatchDirectMessage(Handler handler, Set<WSManager.Listener> listeners, WSModels.DirectMessage message) {
        final WSModels.DirectMessage msg = message;
        handler.post(new Runnable() {
            @Override
            public void run() {
                for (WSManager.Listener listener : listeners) {
                    listener.onDirectMessage(msg);
                }
            }
        });
    }

    static void dispatchDirectRead(Handler handler, Set<WSManager.Listener> listeners, String threadId, String readerUid, long readAt) {
        final String finalThreadId = threadId;
        final String finalReaderUid = readerUid;
        final long finalReadAt = readAt;
        handler.post(new Runnable() {
            @Override
            public void run() {
                for (WSManager.Listener listener : listeners) {
                    listener.onDirectRead(finalThreadId, finalReaderUid, finalReadAt);
                }
            }
        });
    }

    static void dispatchDirectRecall(Handler handler, Set<WSManager.Listener> listeners, WSModels.DirectRecall recall) {
        final WSModels.DirectRecall msg = recall;
        handler.post(new Runnable() {
            @Override
            public void run() {
                for (WSManager.Listener listener : listeners) {
                    listener.onDirectRecall(msg);
                }
            }
        });
    }

    static void dispatchGroupMessage(Handler handler, Set<WSManager.Listener> listeners, WSModels.GroupMessage message) {
        final WSModels.GroupMessage msg = message;
        handler.post(new Runnable() {
            @Override
            public void run() {
                for (WSManager.Listener listener : listeners) {
                    listener.onGroupMessage(msg);
                }
            }
        });
    }

    static void dispatchGroupRecall(Handler handler, Set<WSManager.Listener> listeners, WSModels.GroupRecall recall) {
        final WSModels.GroupRecall msg = recall;
        handler.post(new Runnable() {
            @Override
            public void run() {
                for (WSManager.Listener listener : listeners) {
                    listener.onGroupRecall(msg);
                }
            }
        });
    }

    static void dispatchTyping(Handler handler, Set<WSManager.Listener> listeners, WSModels.TypingEvent event) {
        final WSModels.TypingEvent msg = event;
        handler.post(new Runnable() {
            @Override
            public void run() {
                for (WSManager.Listener listener : listeners) {
                    listener.onTyping(msg);
                }
            }
        });
    }

    static void notifyConnection(Handler handler, Set<WSManager.Listener> listeners, boolean isConnected) {
        final boolean connectedState = isConnected;
        handler.post(new Runnable() {
            @Override
            public void run() {
                for (WSManager.Listener listener : listeners) {
                    listener.onConnectionChanged(connectedState);
                }
            }
        });
    }
}

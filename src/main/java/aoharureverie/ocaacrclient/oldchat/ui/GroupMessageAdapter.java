package aoharureverie.ocaacrclient.oldchat.ui;

import aoharureverie.ocaacrclient.oldchat.models.GroupMessage;
import aoharureverie.ocaacrclient.oldchat.util.MessagePayload;

import java.util.List;

public class GroupMessageAdapter extends GroupMessageAdapterRenderSupport2 {
    public interface GroupMessageActionListener {
        void onQuote(GroupMessage message, String displayName);

        void onMention(GroupMessage message, String displayName);

        void onCopy(String text);

        void onRecall(GroupMessage message);

        void onReEdit(GroupMessage message);
    }

    public interface QuoteClickListener {
        void onQuoteClick(MessagePayload.Quote quote);
    }

    public GroupMessageAdapter(android.content.Context context, List<GroupMessage> messages, String myUID) {
        super(context, messages, myUID);
    }
}

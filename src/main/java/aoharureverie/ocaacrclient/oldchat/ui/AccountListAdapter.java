package aoharureverie.ocaacrclient.oldchat.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.List;

import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.models.AccountInfo;

public class AccountListAdapter extends BaseAdapter {

    private final Context context;
    private final List<AccountInfo> accounts;
    private final OnAccountClickListener listener;
    private final OnAccountEditListener editListener;

    public interface OnAccountClickListener {
        void onAccountClick(AccountInfo account);
    }

    public interface OnAccountEditListener {
        void onAccountEdit(AccountInfo account);
    }

    public AccountListAdapter(Context context, List<AccountInfo> accounts,
                              OnAccountClickListener listener, OnAccountEditListener editListener) {
        this.context = context;
        this.accounts = accounts;
        this.listener = listener;
        this.editListener = editListener;
    }

    @Override
    public int getCount() {
        return accounts.size();
    }

    @Override
    public AccountInfo getItem(int position) {
        return accounts.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_account, parent, false);
            holder = new ViewHolder();
            holder.tvName = convertView.findViewById(R.id.tvAccountName);
            holder.tvUsername = convertView.findViewById(R.id.tvAccountUsername);
            holder.tvUrl = convertView.findViewById(R.id.tvAccountUrl);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final AccountInfo account = accounts.get(position);
        holder.tvName.setText(account.displayName != null && !account.displayName.isEmpty()
                ? account.displayName : "未命名账户");
        holder.tvUsername.setText(account.username != null ? account.username : "");
        holder.tvUrl.setText(account.baseurl != null ? account.baseurl : "");

        convertView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onAccountClick(account);
                }
            }
        });

        convertView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (editListener != null) {
                    editListener.onAccountEdit(account);
                }
                return true;
            }
        });

        return convertView;
    }

    private static class ViewHolder {
        TextView tvName;
        TextView tvUsername;
        TextView tvUrl;
    }
}

package aoharureverie.ocaacrclient.oldchat.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.List;

import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.api.HttpUtil;
import aoharureverie.ocaacrclient.oldchat.models.ServerInfo;

public class ServerListAdapter extends BaseAdapter {

    private final Context context;
    private final List<ServerInfo> servers;
    private final OnServerClickListener listener;

    public interface OnServerClickListener {
        void onServerClick(ServerInfo server);
    }

    public ServerListAdapter(Context context, List<ServerInfo> servers, OnServerClickListener listener) {
        this.context = context;
        this.servers = servers;
        this.listener = listener;
    }

    @Override
    public int getCount() {
        return servers.size();
    }

    @Override
    public ServerInfo getItem(int position) {
        return servers.get(position);
    }

    @Override
    public long getItemId(int position) {
        return servers.get(position).id;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_server, parent, false);
            holder = new ViewHolder();
            holder.tvName = convertView.findViewById(R.id.tvServerName);
            holder.tvAuthor = convertView.findViewById(R.id.tvServerAuthor);
            holder.tvUrl = convertView.findViewById(R.id.tvServerUrl);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final ServerInfo server = servers.get(position);
        holder.tvName.setText(server.name != null ? server.name : "未知服务器");
        holder.tvAuthor.setText(server.author != null ? "作者：" + server.author : "");
        holder.tvUrl.setText(server.baseurl != null ? server.baseurl : "");

        convertView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onServerClick(server);
                }
            }
        });

        return convertView;
    }

    private static class ViewHolder {
        TextView tvName;
        TextView tvAuthor;
        TextView tvUrl;
    }
}

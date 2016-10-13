package cn.vfinance.demo;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import cn.vfinance.demo.R;

import java.util.ArrayList;

public class PayMethodListItem  extends BaseAdapter {
    private ArrayList<ChannelInfoEntity> channels = new ArrayList<ChannelInfoEntity>();
    private LayoutInflater mInflater;

    public PayMethodListItem(Context context, ArrayList<ChannelInfoEntity> channels) {
        this.channels = channels;
        this.mInflater = LayoutInflater.from(context);
    }

    public void setList(ArrayList<ChannelInfoEntity> channels){
        this.channels = channels;
    }

    /**
     * How many items are in the data set represented by this Adapter.
     *
     * @return Count of items.
     */
    @Override
    public int getCount() {
        if (channels == null)
            return 0;
        return channels.size();
    }

    /**
     * Get the data item associated with the specified position in the data set.
     *
     * @param position Position of the item whose data we want within the adapter's
     *                 data set.
     * @return The data at the specified position.
     */
    @Override
    public ChannelInfoEntity getItem(int position) {
        return channels.get(position);
    }

    /**
     * Get the row id associated with the specified position in the list.
     *
     * @param position The position of the item within the adapter's data set whose row id we want.
     * @return The id of the item at the specified position.
     */
    @Override
    public long getItemId(int position) {
        return position;
    }

    /**
     * Get a View that displays the data at the specified position in the data set. You can either
     * create a View manually or inflate it from an XML layout file. When the View is inflated, the
     * parent View (GridView, ListView...) will apply default layout parameters unless you use
     * {@link android.view.LayoutInflater#inflate(int, android.view.ViewGroup, boolean)}
     * to specify a root view and to prevent attachment to the root.
     *
     * @param position    The position of the item within the adapter's data set of the item whose view
     *                    we want.
     * @param convertView The old view to reuse, if possible. Note: You should check that this view
     *                    is non-null and of an appropriate type before using. If it is not possible to convert
     *                    this view to display the correct data, this method can create a new view.
     *                    Heterogeneous lists can specify their number of view types, so that this View is
     *                    always of the right type (see {@link #getViewTypeCount()} and
     *                    {@link #getItemViewType(int)}).
     * @param parent      The parent that this view will eventually be attached to
     * @return A View corresponding to the data at the specified position.
     */
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        final ViewHolder viewHolder;

        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.pay_list_item, null);

            viewHolder = new ViewHolder();

            viewHolder.payIcon = (ImageView) convertView.findViewById(R.id.payIcon);
            viewHolder.payName = (TextView) convertView.findViewById(R.id.payName);
            viewHolder.payDesc = (TextView) convertView.findViewById(R.id.payDesc);

            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) convertView.getTag();
        }

        ChannelInfoEntity item = getItem(position);
        viewHolder.payIcon.setImageResource(item.payIcon);
        viewHolder.payName.setText(item.payName);
        viewHolder.payDesc.setText(item.payDesc);
        return convertView;
    }

    private class ViewHolder {
        public ImageView payIcon;
        public TextView payName;
        public TextView payDesc;
    }
}


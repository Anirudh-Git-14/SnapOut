package com.example.snapout;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;

public class AppSelectionAdapter extends RecyclerView.Adapter<AppSelectionAdapter.ViewHolder> {

    private final ArrayList<AppSelectionModel> appList;
    private final OnAppSelectionChangedListener listener;

    public interface OnAppSelectionChangedListener {
        void onSelectionChanged(String packageName, boolean isChecked);
    }

    public AppSelectionAdapter(ArrayList<AppSelectionModel> appList, OnAppSelectionChangedListener listener) {
        this.appList = appList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.row_app_selection, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppSelectionModel model = appList.get(position);
        holder.txtName.setText(model.appName);
        holder.imgIcon.setImageDrawable(model.appIcon);

        // Break listener loop during programmatic binding updates
        holder.chkLock.setOnCheckedChangeListener(null);
        holder.chkLock.setChecked(model.isSelected);

        holder.chkLock.setOnCheckedChangeListener((buttonView, isChecked) -> {
            model.isSelected = isChecked;
            if (listener != null) {
                listener.onSelectionChanged(model.packageName, isChecked);
            }
        });

        holder.itemView.setOnClickListener(v -> holder.chkLock.performClick());
    }

    @Override
    public int getItemCount() {
        return appList == null ? 0 : appList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgIcon;
        TextView txtName;
        CheckBox chkLock;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgIcon = itemView.findViewById(R.id.imgSelectedAppIcon);
            txtName = itemView.findViewById(R.id.txtSelectedAppName);
            chkLock = itemView.findViewById(R.id.chkAppLock);
        }
    }
}
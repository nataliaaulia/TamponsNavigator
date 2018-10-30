package com.example.nataliaaulia.tamponsnavigator.historyRecyclerView;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;

import com.example.nataliaaulia.tamponsnavigator.HistorySingleActivity;
import com.example.nataliaaulia.tamponsnavigator.R;

//LAYOUT
public class HistoryViewHolders extends RecyclerView.ViewHolder implements View.OnClickListener{

    public TextView rideId;
    public TextView time;

    public HistoryViewHolders(View itemView) {
        super(itemView);
        itemView.setOnClickListener(this);

        rideId = (TextView) itemView.findViewById(R.id.rideId);
        time = (TextView) itemView.findViewById(R.id.time);
    }

    //TO KNOW WHICH CAR IS CLICKED
    @Override
    public void onClick(View view) {
        Intent intent = new Intent(view.getContext(), HistorySingleActivity.class);
        Bundle bundle = new Bundle();
        bundle.putString("rideId", rideId.getText().toString());
        intent.putExtras(bundle);
        view.getContext().startActivity(intent);

    }
}

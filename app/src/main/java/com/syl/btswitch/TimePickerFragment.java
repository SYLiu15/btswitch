package com.syl.btswitch;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.NumberPicker;
import android.widget.TextView;

public class TimePickerFragment extends DialogFragment {
    private TextView mTitle;
    private NumberPicker mHourPicker;
    private NumberPicker mMinPicker;

    private int mHour;
    private int mMin;
    private int mIndex;

    private Button mCancel;
    private Button mSave;

    public TimePickerFragment() {
        // Empty constructor is required for DialogFragment
        // Make sure not to add arguments to the constructor
        // Use `newInstance` instead as shown below
    }

    public static TimePickerFragment newInstance(int hours, int min, int index) {
        TimePickerFragment frag = new TimePickerFragment();
        Bundle args = new Bundle();
        args.putInt("hours", hours);
        args.putInt("min", min);
        args.putInt("index", index);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.time_picker, container);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mTitle = view.findViewById(R.id.timePickerTitle);
        mHourPicker = view.findViewById(R.id.hourPicker);
        mMinPicker = view.findViewById(R.id.minPicker);

        mHour = getArguments().getInt("hours");
        mMin = getArguments().getInt("min");
        mIndex = getArguments().getInt("index");
        if (mIndex == 1) {
            mTitle.setText(R.string.left_outlet);
        } else {
            mTitle.setText(R.string.right_outlet);
        }

        mHourPicker.setMinValue(0);
        mHourPicker.setMaxValue(9);
        mMinPicker.setMinValue(0);
        mMinPicker.setMaxValue(59);

        mHourPicker.setValue(mHour);
        mMinPicker.setValue(mMin);

        mSave = view.findViewById(R.id.setTimer);
        mSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeTimer(1);
            }
        });
        mCancel = view.findViewById(R.id.cancelTimer);
        mCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeTimer(2);
            }
        });
    }

    public void closeTimer (int index) {
        if (index == 1) {
            mHour = mHourPicker.getValue();
            mMin = mMinPicker.getValue();
            mListener.returnData(mHour, mMin, mIndex);
        }
        this.dismiss();
    }

    public interface OnCompleteListener {
        void returnData(int hour, int min, int index);
    }

    private OnCompleteListener mListener;

    // make sure the Activity implemented it
    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the EditNameDialogListener so we can send events to the host
            mListener = (OnCompleteListener) context;
        } catch (ClassCastException e) {
            // The activity doesn't implement the interface, throw exception
            throw new ClassCastException(context.toString() + " must implement EditNameDialogListener");
        }
    }
}

package de.beamerscope.calibration;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import de.beamerscope.R;

/**
 * Created by Bene on 13.12.16.
 */

public class GammaCalibSettings  extends DialogFragment {

    public static String TAG = "Settings Dialog 2";

    public interface NoticeDialogListener {
        public void onDialogPositiveClick(DialogFragment dialog);
        public void onDialogNegativeClick(DialogFragment dialog);
    }

    // Use this instance of the interface to deliver action events
    de.beamerscope.calibration.GammaCalibSettings.NoticeDialogListener mListener;
    private Button acqSettingsDFButton;
    private Button acqSettingsBFButton;
    private Button acqSettingsFPMButton;
    private Button acqSettingsDPCButton;
    private Button acqSettingsOPTButton;

    private TextView acquireSettingsSetDatasetName;
    private TextView acquireSettingsMultiModeCountTextView;
    private TextView acquireSettingsSetNAEditText;
    private TextView acquireSettingsSetMultiModeDelayEditText;
    private TextView acquireSettingsAECCompensationEditText;
    private CheckBox acquireSettingsHDRCheckbox;
    private TextView acquireSettingsISOEditText;

    // Override the Fragment.onAttach() method to instantiate the NoticeDialogListener
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);


        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the NoticeDialogListener so we can send events to the host
            mListener = (de.beamerscope.calibration.GammaCalibSettings.NoticeDialogListener) activity;
        } catch (ClassCastException e) {
            // The activity doesn't implement the interface, throw exception
            throw new ClassCastException(activity.toString()
                    + " must implement NoticeDialogListener");
        }
    }

    public static interface OnCompleteListener {
        public abstract void onComplete(String time);
    }


    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        // Get the layout inflater
        LayoutInflater inflater = getActivity().getLayoutInflater();
        View content = inflater.inflate(R.layout.settings_acquire3, null);



        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        builder.setView(content);
        // Add action buttons
        builder.setPositiveButton("Set", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                String mmCountValue = acquireSettingsMultiModeCountTextView.getText().toString();
                String naValue = acquireSettingsSetNAEditText.getText().toString();
                String mmDelayValue = acquireSettingsSetMultiModeDelayEditText.getText().toString();
                String aecCompensationVal = acquireSettingsAECCompensationEditText.getText().toString();
                String datasetName = acquireSettingsSetDatasetName.getText().toString();
                String isoSetting = acquireSettingsISOEditText.getText().toString();
                Log.d(TAG,String.format("nMaxSearchApertures: %s", mmCountValue));
                Log.d(TAG,String.format("mmDelay: %s", mmDelayValue));
                Log.d(TAG,String.format("new na: %s", naValue));
                Log.d(TAG,String.format("new ISO: %s", isoSetting));
                GammaCalibActivity callingActivity = (GammaCalibActivity) getActivity();
//    	        callingActivity.setMultiModeCount(Integer.parseInt(mmCountValue));
//    	        callingActivity.setMultiModeDelay(Float.parseFloat(mmDelayValue));
//    	        callingActivity.setAECCompensation(Integer.parseInt(aecCompensationVal));
                callingActivity.setNA(Double.parseDouble(naValue));
                callingActivity.setDatasetName(datasetName);
                callingActivity.setISO(isoSetting);
            }
        })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss();
                    }
                });

        GammaCalibActivity callingActivity = (GammaCalibActivity) getActivity();

        acquireSettingsISOEditText = (TextView) content.findViewById(R.id.acquireSettingsISOEditText);
        acquireSettingsISOEditText.setInputType(InputType.TYPE_CLASS_NUMBER);
        acquireSettingsISOEditText.setText(String.format("%d", callingActivity.isoSetting));

        acquireSettingsMultiModeCountTextView = (TextView) content.findViewById(R.id.acquireSettingsMultiModeCountTextView);
        acquireSettingsMultiModeCountTextView.setInputType(InputType.TYPE_CLASS_NUMBER);
        acquireSettingsMultiModeCountTextView.setText(String.format("%d", callingActivity.nMaxSearchApertures));

        acquireSettingsSetNAEditText = (TextView) content.findViewById(R.id.acquireSettingsSetNAEditText);
        acquireSettingsSetNAEditText.setInputType(InputType.TYPE_NUMBER_FLAG_DECIMAL);
        acquireSettingsSetNAEditText.setText(String.valueOf(callingActivity.brightfieldNA));

        acquireSettingsAECCompensationEditText = (TextView) content.findViewById(R.id.acquireSettingsAECCompensationEditText);
        acquireSettingsAECCompensationEditText.setInputType(InputType.TYPE_NUMBER_FLAG_SIGNED);
        acquireSettingsAECCompensationEditText.setText(String.format("%d", callingActivity.aecCompensation));

        acquireSettingsSetMultiModeDelayEditText = (TextView) content.findViewById(R.id.acquireSettingsSetMultiModeDelayEditText);
        acquireSettingsSetMultiModeDelayEditText.setInputType(InputType.TYPE_NUMBER_FLAG_DECIMAL);
        acquireSettingsSetMultiModeDelayEditText.setText(String.format("%.2f", callingActivity.mmDelay));

        acquireSettingsSetDatasetName = (TextView) content.findViewById(R.id.acquireSettingsSetDatasetName);
        acquireSettingsSetDatasetName.setInputType(InputType.TYPE_CLASS_TEXT);
        acquireSettingsSetDatasetName.setText(callingActivity.datasetName);

        acquireSettingsHDRCheckbox = (CheckBox) content.findViewById(R.id.acquireSettingsHDRCheckbox);
        acquireSettingsHDRCheckbox.setChecked(callingActivity.usingHDR);

        acqSettingsBFButton = (Button) content.findViewById(R.id.acqSettingsBrightfieldButton);
        acqSettingsBFButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                GammaCalibActivity callingActivity = (GammaCalibActivity) getActivity();
                callingActivity.toggleBrightfield();

            }
        });

        // Choose Darkfield mode
        acqSettingsDFButton = (Button) content.findViewById(R.id.acqSettingsDarkfieldButton);
        acqSettingsDFButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                GammaCalibActivity callingActivity = (GammaCalibActivity) getActivity();
                callingActivity.toggleDarkfield();
            }
        });
        acqSettingsFPMButton = (Button) content.findViewById(R.id.acqSettingsFPMButton); // Setup FPM
        acqSettingsFPMButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                GammaCalibActivity callingActivity = (GammaCalibActivity) getActivity();
                callingActivity.toggleFPM();
            }
        });

        acqSettingsDPCButton = (Button) content.findViewById(R.id.acqSettingsDPCButton);
        acqSettingsDPCButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                GammaCalibActivity callingActivity = (GammaCalibActivity) getActivity();
                callingActivity.toggleDPC();

            }
        });

        acqSettingsOPTButton = (Button) content.findViewById(R.id.acqSettingsOPTButton);
        acqSettingsOPTButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                GammaCalibActivity callingActivity = (GammaCalibActivity) getActivity();
                callingActivity.toggleOPT();

            }
        });


        return builder.create();
    }


}


